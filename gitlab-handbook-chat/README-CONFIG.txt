TRIEN KHAI SECRET CHAT

1. SERVER

Tai thu muc chat-system:

  docker compose up -d

Khong can file .env, certificate, public key hay private key.

2. CLIENT CUNG MAY SERVER

Client tu dong ket noi:

  http://localhost:8088

Khong can dat file cau hinh canh SecretChat.exe.

3. CLIENT TRONG LAN

Khi dong goi client, dat IP/hostname cua may server:

  .\build-setup.ps1 -ServerIP 192.168.1.91

Mo TCP 8088 tren firewall cua may server. Client se ket noi:

  http://192.168.1.91:8088

4. DIA CHI DICH VU

- API Gateway: http://SERVER:8088
- Keycloak: http://SERVER:8081
- Webmail: http://SERVER:8085

Tai khoan khoi tao:

- Keycloak admin: admin / admin
- Mail admin: admin@gitlab.handbook.local / admin123
