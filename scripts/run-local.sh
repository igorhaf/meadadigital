#!/usr/bin/env bash
# Sobe o backend meada localmente, lendo as variáveis de ambiente do .env da raiz.
#   ./scripts/run-local.sh
#
# Falha cedo (e com mensagem clara) se o .env não existir.
set -euo pipefail

# Raiz do projeto = diretório-pai deste script (funciona de qualquer cwd).
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
ENV_FILE="$ROOT_DIR/.env"

if [[ ! -f "$ENV_FILE" ]]; then
  echo "ERRO: $ENV_FILE não encontrado." >&2
  echo "Crie-o a partir do template:  cp .env.example .env  e preencha os valores." >&2
  exit 1
fi

# Carrega o .env e EXPORTA tudo que ele define para o ambiente do mvn.
# 'set -a' faz toda atribuição subsequente virar export automaticamente.
set -a
# shellcheck disable=SC1090
source "$ENV_FILE"
set +a

LOG_FILE="/tmp/meada-backend.log"
echo "Variáveis carregadas de $ENV_FILE. Subindo o backend (mvn spring-boot:run)..."
echo "Log também em $LOG_FILE (para acompanhamento via tail/grep)."
cd "$ROOT_DIR"
# tee: mostra na tela E grava no arquivo (para observação dos outcomes durante E2E).
JAVA_HOME=/usr/lib/jvm/temurin-17-jdk-amd64 mvn spring-boot:run 2>&1 | tee "$LOG_FILE"
