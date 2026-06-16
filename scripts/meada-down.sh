#!/usr/bin/env bash
# Derruba a stack dockerizada do Meada WhatsApp (fase 0.5). NÃO religa o Apache automaticamente
# (decisão: Apache é resíduo — religar é escolha consciente sua).
set -euo pipefail

cd "$(dirname "${BASH_SOURCE[0]}")/.."

echo "Parando containers (docker compose down)..."
docker compose down

echo ""
echo "Containers parados. Apache local NÃO foi reiniciado."
echo "Se quiser religar o Apache:  sudo systemctl start apache2"
