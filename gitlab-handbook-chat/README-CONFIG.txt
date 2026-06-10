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
   http://192.168.1.91:8085/webmail
4. Khi dang ky, he thong tu tao:
   username@gitlab.handbook.local
   Mat khau mail duoc hien mot lan sau khi dang ky.
5. Man hinh Quen mat khau nhap dia chi mail noi bo tren. Keycloak gui link
   dat lai mat khau vao chinh hop thu Mailu nay.
6. Tai khoan quan tri Mailu ban dau:
   admin@gitlab.handbook.local
   ChangeMe-Mail-Admin-2026!

Hay doi INITIAL_ADMIN_PW, API_TOKEN trong chat-system/mailu.env va cac gia tri
tuong ung trong Docker Compose/realm-export.json truoc khi trien khai that.

Neu realm master da ton tai trong PostgreSQL, Keycloak se khong import de len.
Can cap nhat SMTP trong Keycloak Admin Console hoac tao lai database dev de
realm-export.json moi duoc import.

He thong khong can Internet khi su dung. Chi can Internet mot lan de Docker
pull cac image Mailu; sau do mail gui va nhan hoan toan trong mang noi bo.
Vi day la he thong LAN khong co DNSSEC cong cong, mailu.env da bat co ngoai le
LAN-only va image mailu-admin da bo rieng buoc kiem tra DNSSEC khi khoi dong.
Khong dung cau hinh nay de mo mail server ra Internet.
