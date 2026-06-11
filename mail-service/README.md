# Mail Service

Internal mail infrastructure for the project:

- `docker-mailserver`: SMTP, IMAP, mailbox data and password hashes.
- `roundcube`: Webmail UI.
- `roundcube-db`: Roundcube metadata database.
- `account-manager`: Internal API for creating, deleting and changing mailbox passwords.
- `roundcube-config`: Roundcube TLS and password plugin configuration.

The services are orchestrated by `chat-system/docker-compose.yml`.

Webmail: `http://localhost:8085/`

Mailbox domain: `gitlab.handbook.local`
