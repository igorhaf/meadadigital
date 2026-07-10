#!/usr/bin/env bash
#
# Sobe o ngrok para a aplicação Muda e conecta a URL pública no .env.
#
# A aplicação roda 100% via nginx (Docker) na porta 8095 — esse é o ÚNICO
# tunnel necessário. Ele já cobre o site + todos os webhooks/callbacks
# (Mercado Pago, Google OAuth, MelhorEnvio), que apontam para o APP_URL.
#
# O tunnel do Vite (5173) só é preciso se você rodar `npm run dev` (HMR).
# Hoje os assets já estão buildados (public/build/manifest.json), então
# NÃO é necessário. Descomente TUNNELS abaixo se quiser HMR (requer plano
# ngrok que permita 2 tunnels simultâneos).
#
set -euo pipefail

cd "$(dirname "$0")"

TUNNELS="muda-web"
# TUNNELS="muda-web muda-vite"   # habilite p/ Vite HMR (npm run dev)

echo "==> Subindo ngrok ($TUNNELS)..."
pkill -f 'ngrok start' 2>/dev/null || true
sleep 1

# Roda o ngrok em background, log em arquivo (sem TUI).
nohup ngrok start $TUNNELS \
  --log "$PWD/storage/logs/ngrok.log" \
  --log-format logfmt --log-level info \
  >/dev/null 2>&1 &

echo "==> Aguardando o tunnel subir..."
# Domínio fixo (reservado na conta ngrok) — definido no ngrok.yml.
URL="https://slum-feminist-speculate.ngrok-free.dev"
for i in $(seq 1 20); do
  if curl -s http://localhost:4040/api/tunnels 2>/dev/null | grep -q 'public_url'; then
    break
  fi
  sleep 1
done

echo "==> URL pública: $URL"

# --- Atualiza o .env ---------------------------------------------------------
update_env() {
  local key="$1" val="$2"
  if grep -qE "^${key}=" .env; then
    # escapa & e / para o sed
    local esc; esc=$(printf '%s' "$val" | sed -e 's/[&/\]/\\&/g')
    sed -i -E "s|^${key}=.*|${key}=${esc}|" .env
  else
    printf '%s=%s\n' "$key" "$val" >> .env
  fi
}

update_env APP_URL "$URL"
update_env MP_BACK_URL_BASE "$URL"

echo "==> .env atualizado: APP_URL e MP_BACK_URL_BASE = $URL"

# Limpa cache de config do Laravel dentro do container.
if docker ps --format '{{.Names}}' | grep -q '^muda-app$'; then
  docker exec muda-app php artisan config:clear >/dev/null 2>&1 || true
  docker exec muda-app php artisan cache:clear  >/dev/null 2>&1 || true
  echo "==> Cache de config do Laravel limpo (muda-app)."
fi

cat <<EOF

============================================================
  Muda no ar via ngrok
------------------------------------------------------------
  App público : $URL
  Inspector   : http://localhost:4040

  Callbacks para registrar nos provedores:
    Google OAuth   : $URL/auth/google/callback
    Mercado Pago   : $URL/  (usa MP_BACK_URL_BASE)
    MelhorEnvio    : já fixo em https://meadadigital.com/frete/callback

  Para parar: pkill -f 'ngrok start'
============================================================
EOF
