#!/usr/bin/env bash
# Serve a wiki do projeto (docs/wiki, Docsify vendorizado) — uso: ./scripts/wiki-serve.sh [porta]
set -euo pipefail
cd "$(dirname "$0")/../docs/wiki"
PORT="${1:-8098}"
echo "Wiki do Meada em http://localhost:${PORT}"
exec python3 -m http.server "$PORT" --bind 0.0.0.0
