CAU HINH KET NOI SECRET CHAT

1. Mo file .env bang Notepad.
2. Dat GATEWAY_HOST thanh dia chi IPv4 cua laptop dang chay Docker Compose.
3. Giu GATEWAY_PORT=8088.
4. Luu file va mo lai SecretChat.exe.

Vi du:
GATEWAY_HOST=192.168.1.91
GATEWAY_PORT=8088

Tat ca laptop phai cung mang Wi-Fi. May chay Docker phai mo TCP port 8088
trong Windows Firewall.

CAU HINH EMAIL NOI BO VA QUEN MAT KHAU

1. Truoc khi chay Docker Compose, dat MAIL_HOST thanh dia chi IPv4 cua may chu.
   Vi du PowerShell:
   $env:MAIL_HOST="192.168.1.91"
2. Chay tai thu muc chat-system:
   docker compose up -d --build
3. Webmail cua user:
   http://192.168.1.91:8085/
4. Khi dang ky, he thong tu tao:
   username@gitlab.handbook.local
   Mat khau mail duoc hien mot lan sau khi dang ky.
5. Man hinh Quen mat khau nhap dia chi mail noi bo tren. Keycloak gui link
   dat lai mat khau vao chinh hop thu nay.
   Neu user mo mail tu may khac trong LAN, cap nhat Frontend URL cua realm:
   http://192.168.1.91:8081
   Keycloak Admin Console > Realm settings > General > Frontend URL.
6. Tai khoan mail quan tri ban dau:
   admin@gitlab.handbook.local
   ChangeMe-Mail-Admin-2026!
7. User co the doi mat khau mail tai Settings > Password trong Roundcube.

Hay dat MAIL_ACCOUNT_API_TOKEN, INITIAL_MAIL_ADMIN_PASSWORD va
ROUNDCUBE_DB_PASSWORD truoc khi trien khai that. Vi du PowerShell:
$env:MAIL_ACCOUNT_API_TOKEN="replace-with-a-long-random-token"
$env:INITIAL_MAIL_ADMIN_PASSWORD="replace-with-a-strong-password"
$env:ROUNDCUBE_DB_PASSWORD="replace-with-a-strong-password"

Neu realm master da ton tai trong PostgreSQL, Keycloak se khong import de len.
Can cap nhat SMTP trong Keycloak Admin Console hoac tao lai database dev de
realm-export.json moi duoc import.

He thong khong can Internet khi su dung. Chi can Internet mot lan de Docker
pull docker-mailserver, Roundcube va PostgreSQL; sau do mail gui va nhan hoan
toan trong mang noi bo. DMS dung chung account password dang SHA512-CRYPT,
Roundcube chi xac thuc qua IMAP va khong luu mat khau mail.
Khong dung cau hinh nay de mo mail server ra Internet.
