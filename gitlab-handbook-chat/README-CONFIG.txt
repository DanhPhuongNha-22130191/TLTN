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

CAU HINH EMAIL QUEN MAT KHAU

1. Mo Keycloak Admin Console: http://localhost:8081
2. Chon realm dang su dung (thuong la master).
3. Vao Realm settings > Email.
4. Nhap SMTP host, port, email gui, username va password.
5. Bat SSL/TLS theo nha cung cap email va bam Test connection.
6. Trong Realm settings > Login, bat Forgot password.

Khong luu mat khau SMTP vao source code hoac realm-export.json.
