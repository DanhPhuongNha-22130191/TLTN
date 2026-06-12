import hmac
import imaplib
import json
import os
import re
import ssl
import subprocess
import threading
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import parse_qs, unquote, urlparse


API_TOKEN = os.environ["MAIL_ACCOUNT_API_TOKEN"]
MAILSERVER_CONTAINER = os.getenv("MAILSERVER_CONTAINER", "mailserver")
MAIL_DOMAIN = os.getenv("MAIL_DOMAIN", "gitlab.handbook.local").lower()
IMAP_HOST = os.getenv("IMAP_HOST", "mail.gitlab.handbook.local")
IMAP_PORT = int(os.getenv("IMAP_PORT", "993"))
IMAP_USE_SSL = os.getenv("IMAP_USE_SSL", "true").lower() == "true"
TLS_CA_FILE = os.environ["TLS_CA_FILE"]
INITIAL_ADMIN_EMAIL = os.getenv("INITIAL_ADMIN_EMAIL", "")
INITIAL_ADMIN_PASSWORD = os.getenv("INITIAL_ADMIN_PASSWORD", "")
PORT = int(os.getenv("PORT", "8080"))

EMAIL_PATTERN = re.compile(r"^[a-z0-9][a-z0-9._+-]{0,63}@[a-z0-9.-]+$")
PASSWORD_MIN_LENGTH = 8
MAX_BODY_BYTES = 64 * 1024
COMMAND_LOCK = threading.Lock()


class ApiError(Exception):
    def __init__(self, status, message):
        super().__init__(message)
        self.status = status


def validate_email(value):
    email = str(value or "").strip().lower()
    if not EMAIL_PATTERN.fullmatch(email) or email.rsplit("@", 1)[1] != MAIL_DOMAIN:
        raise ApiError(400, f"Email must belong to {MAIL_DOMAIN}")
    return email


def validate_password(value):
    password = str(value or "")
    if len(password) < PASSWORD_MIN_LENGTH or len(password) > 256:
        raise ApiError(400, f"Password length must be {PASSWORD_MIN_LENGTH}-256 characters")
    if "\x00" in password or "\n" in password or "\r" in password:
        raise ApiError(400, "Password contains invalid characters")
    return password


def run_setup(*arguments, allow_exists=False, allow_missing=False, allow_empty=False):
    command = ["docker", "exec", MAILSERVER_CONTAINER, "setup", *arguments]
    with COMMAND_LOCK:
        result = subprocess.run(command, capture_output=True, text=True, timeout=45)

    output = "\n".join(part for part in (result.stdout, result.stderr) if part).strip()
    normalized = output.lower()
    if result.returncode == 0:
        return output
    if allow_exists and ("already exists" in normalized or "is already configured" in normalized):
        return output
    if allow_missing and ("does not exist" in normalized or "could not find" in normalized):
        return output
    if allow_empty and "is empty, nothing to list" in normalized:
        return output
    raise ApiError(502, output or "docker-mailserver setup command failed")


def create_account(email, password, quota_bytes=None, allow_exists=False):
    run_setup("email", "add", email, password, allow_exists=allow_exists)
    if quota_bytes:
        try:
            quota = int(quota_bytes)
        except (TypeError, ValueError):
            raise ApiError(400, "quotaBytes must be an integer")
        if quota <= 0:
            raise ApiError(400, "quotaBytes must be positive")
        run_setup("quota", "set", email, f"{quota}B")
    wait_for_imap_auth(email, password)


def account_exists(email):
    output = run_setup("email", "list", allow_empty=True)
    return any(
        line.strip().split(" ", 1)[-1].split(" ", 1)[0] == email
        for line in output.splitlines()
        if line.strip().startswith("* ")
    )


def imap_login(email, password):
    if IMAP_USE_SSL:
        context = ssl.create_default_context(cafile=TLS_CA_FILE)
        client = imaplib.IMAP4_SSL(IMAP_HOST, IMAP_PORT, ssl_context=context, timeout=15)
    else:
        client = imaplib.IMAP4(IMAP_HOST, IMAP_PORT, timeout=15)
    try:
        client.login(email, password)
        client.logout()
    finally:
        try:
            client.shutdown()
        except Exception:
            pass


def authenticate_imap(email, password):
    try:
        imap_login(email, password)
    except imaplib.IMAP4.error as error:
        raise ApiError(401, "Current mailbox password is incorrect") from error
    except (OSError, ssl.SSLError) as error:
        raise ApiError(502, "Unable to verify password with IMAP") from error


def wait_for_imap_auth(email, password):
    deadline = time.monotonic() + 20
    last_error = None
    while time.monotonic() < deadline:
        try:
            imap_login(email, password)
            return
        except (imaplib.IMAP4.error, OSError, ssl.SSLError) as error:
            last_error = error
            time.sleep(1)
    raise ApiError(502, f"Mailbox credentials did not become active: {last_error}")


def bootstrap_admin():
    if not INITIAL_ADMIN_EMAIL or not INITIAL_ADMIN_PASSWORD:
        return

    email = validate_email(INITIAL_ADMIN_EMAIL)
    password = validate_password(INITIAL_ADMIN_PASSWORD)
    deadline = time.monotonic() + 110
    while time.monotonic() < deadline:
        try:
            if account_exists(email):
                print(f"Initial mailbox already exists: {email}", flush=True)
                return
            create_account(email, password)
            print(f"Initial mailbox is ready: {email}", flush=True)
            return
        except Exception as error:
            print(f"Waiting to create initial mailbox: {error}", flush=True)
            time.sleep(5)
    raise RuntimeError("Timed out while creating the initial mailbox")


class Handler(BaseHTTPRequestHandler):
    server_version = "MailAccountManager/1.0"

    def do_GET(self):
        if urlparse(self.path).path == "/health":
            self.respond(200, {"status": "up"})
            return
        self.respond(404, {"error": "Not found"})

    def do_POST(self):
        path = urlparse(self.path).path
        try:
            if path == "/accounts":
                self.require_token()
                payload = self.read_json()
                create_account(
                    validate_email(payload.get("email")),
                    validate_password(payload.get("password")),
                    payload.get("quotaBytes"),
                )
                self.respond(201, {"status": "created"})
                return
            if path == "/password":
                payload = self.read_form()
                email = validate_email(payload.get("username"))
                current_password = validate_password(payload.get("currentPassword"))
                new_password = validate_password(payload.get("newPassword"))
                authenticate_imap(email, current_password)
                run_setup("email", "update", email, new_password)
                wait_for_imap_auth(email, new_password)
                self.respond(200, {"status": "updated"})
                return
            raise ApiError(404, "Not found")
        except ApiError as error:
            self.respond(error.status, {"error": str(error)})
        except Exception as error:
            print(f"Unhandled request error: {error}", flush=True)
            self.respond(500, {"error": "Internal server error"})

    def do_DELETE(self):
        try:
            self.require_token()
            path = urlparse(self.path).path
            prefix = "/accounts/"
            if not path.startswith(prefix):
                raise ApiError(404, "Not found")
            email = validate_email(unquote(path[len(prefix):]))
            run_setup("email", "del", "-y", email, allow_missing=True)
            self.respond(204, None)
        except ApiError as error:
            self.respond(error.status, {"error": str(error)})
        except Exception as error:
            print(f"Unhandled request error: {error}", flush=True)
            self.respond(500, {"error": "Internal server error"})

    def require_token(self):
        authorization = self.headers.get("Authorization", "")
        expected = f"Bearer {API_TOKEN}"
        if not hmac.compare_digest(authorization, expected):
            raise ApiError(401, "Invalid API token")

    def read_body(self):
        try:
            length = int(self.headers.get("Content-Length", "0"))
        except ValueError as error:
            raise ApiError(400, "Invalid Content-Length") from error
        if length <= 0 or length > MAX_BODY_BYTES:
            raise ApiError(400, "Invalid request body size")
        return self.rfile.read(length)

    def read_json(self):
        try:
            return json.loads(self.read_body().decode("utf-8"))
        except (UnicodeDecodeError, json.JSONDecodeError) as error:
            raise ApiError(400, "Invalid JSON body") from error

    def read_form(self):
        try:
            values = parse_qs(
                self.read_body().decode("utf-8"),
                keep_blank_values=True,
                strict_parsing=True,
            )
        except (UnicodeDecodeError, ValueError) as error:
            raise ApiError(400, "Invalid form body") from error
        return {key: items[-1] for key, items in values.items()}

    def respond(self, status, payload):
        body = b"" if payload is None else json.dumps(payload).encode("utf-8")
        self.send_response(status)
        if body:
            self.send_header("Content-Type", "application/json")
            self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        if body:
            self.wfile.write(body)

    def log_message(self, message, *args):
        print(f"{self.address_string()} - {message % args}", flush=True)


if __name__ == "__main__":
    bootstrap_admin()
    server = ThreadingHTTPServer(("0.0.0.0", PORT), Handler)
    print(f"Mail account manager listening on port {PORT}", flush=True)
    server.serve_forever()
