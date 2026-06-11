# TLS certificates

Place CA-issued PEM files in this directory before starting the stack:

```text
secrets/tls/gateway/fullchain.pem
secrets/tls/gateway/privkey.pem
secrets/tls/keycloak/fullchain.pem
secrets/tls/keycloak/privkey.pem
secrets/tls/mail/fullchain.pem
secrets/tls/mail/privkey.pem
secrets/tls/mail/ca.pem
secrets/tls/webmail/fullchain.pem
secrets/tls/webmail/privkey.pem
```

The gateway, Keycloak and webmail certificates must cover `PUBLIC_HOSTNAME`.
The mail certificate must cover `mail.gitlab.handbook.local`. For an internal
`.local` domain, use an organizational CA and trust `mail/ca.pem` on clients.
Private keys and certificate files are ignored by Git.
