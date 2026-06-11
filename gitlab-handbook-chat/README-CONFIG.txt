TRIEN KHAI SECRET CHAT

1. CHAY TAT CA TREN CUNG MOT MAY

Tai thu muc chat-system:

  docker compose up -d
  docker compose ps

File .env nam canh SecretChat.exe:

  GATEWAY_HOST=localhost
  GATEWAY_PORT=8088
  GATEWAY_SCHEME=https

Chep CA cua server:

  chat-system\secrets\tls\mail\ca.pem

thanh:

  secretchat-ca.pem

va dat canh SecretChat.exe. Mo PowerShell tai thu muc ung dung, chay:

  powershell -ExecutionPolicy Bypass -File .\INSTALL-CLIENT-CA.ps1

Sau do mo lai SecretChat.exe.


2. CHAY CLIENT TREN MAY KHAC TRONG LAN

- Tat ca may phai cung mang LAN/Wi-Fi.
- May server phai mo TCP 8088, 8081 va 8085 trong Windows Firewall.
- PUBLIC_HOSTNAME trong chat-system\.env phai la hostname/IP cua server.
- Chung chi gateway, Keycloak va webmail phai chua hostname/IP nay trong
  Subject Alternative Name (SAN). Chi sua GATEWAY_HOST la khong du.
- Chep ca.pem cua server sang tung client, doi ten thanh secretchat-ca.pem,
  dat canh SecretChat.exe va chay INSTALL-CLIENT-CA.ps1 mot lan.

Vi du file .env cua client:

  GATEWAY_HOST=192.168.1.91
  GATEWAY_PORT=8088
  GATEWAY_SCHEME=https

Khong dung IP vi du neu chung chi TLS khong bao gom IP do.


3. DIA CHI DICH VU

- API Gateway: https://SERVER:8088
- Keycloak: https://SERVER:8081
- Webmail: https://SERVER:8085

Kiem tra gateway:

  Test-NetConnection SERVER -Port 8088

HTTP 401 tai endpoint duoc bao ve van co nghia gateway dang hoat dong.


4. LUU Y

- Khong commit chat-system\.env, private key hoac CA noi bo.
- Runtime Java cua SecretChat.exe co truststore rieng. Cai CA vao Windows
  khong dam bao ung dung Java se tin CA; hay dung INSTALL-CLIENT-CA.ps1.
- Neu thay "Ket noi server that bai", kiem tra lan luot: container, port,
  GATEWAY_HOST, SAN cua certificate, sau do truststore cua runtime.
