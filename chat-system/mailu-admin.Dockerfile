FROM ghcr.io/mailu/admin:2024.06

# Mailu blocks admin startup without a public DNSSEC resolver. This deployment
# is intentionally LAN-only, so keep the admin/API available without Internet.
USER root
RUN sed -i 's/^test_DNS()$/# DNSSEC startup check disabled for LAN-only deployment/' /start.py
