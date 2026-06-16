#!/usr/bin/env bash
# Sobe a stack dockerizada do Meada WhatsApp (fase 0.5): backend + frontend + embeddings + caddy.
# O banco é o Supabase REMOTO (lido do .env). Para o Apache local (libera a porta 80 p/ o Caddy)
# — temporário, NÃO desabilita; o meada-down.sh deixa instruções pra religar se você quiser.
set -euo pipefail

cd "$(dirname "${BASH_SOURCE[0]}")/.."

# ---- Apache: liberar a porta 80 para o Caddy ----
if command -v systemctl >/dev/null 2>&1 && systemctl is-active --quiet apache2; then
  echo "Parando Apache local (temporário, libera a porta 80 p/ o Caddy)..."
  sudo systemctl stop apache2
fi

# ---- sobe os containers ----
echo "Subindo containers (docker compose up -d --build)..."
docker compose up -d --build

# ---- espera o backend responder ----
echo "Aguardando o backend (até ~2min — 1ª subida compila o jar)..."
for i in $(seq 1 60); do
  code=$(curl -s -o /dev/null -w "%{http_code}" http://localhost:8095/admin/me 2>/dev/null || echo "000")
  if [ "$code" = "401" ]; then
    echo "Backend pronto (/admin/me → 401)."
    break
  fi
  sleep 2
done

echo ""
echo "Meada está no ar! Acesse (sem porta):"
echo "  http://meada.meadadigital.local      (login universal)"
echo "  http://processo.meadadigital.local   (ProcessoBot)"
echo "  http://dental.meadadigital.local     (DentalBot)"
echo "  http://sushi.meadadigital.local      (SushiBot)"
echo "  http://api.meadadigital.local        (backend / API)"
echo ""
echo "Logs:  docker compose logs -f <backend|frontend|embeddings|caddy>"
echo "Parar: ./scripts/meada-down.sh"
