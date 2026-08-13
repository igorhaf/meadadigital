"""
Configuração compartilhada da suíte de testes funcionais do Meada.
===================================================================
Lê URLs e chaves dos envs REAIS do projeto (.env raiz e frontend/.env.local) —
nenhum segredo vive neste arquivo. A senha dos usuários de teste é sintética,
local-only (usuários descartáveis selenium.*@meada.test no Supabase LOCAL) e
pode ser trocada via env MEADA_SELENIUM_PASSWORD.

Nesta camada (regra/1 — schema multi-tenant) a suíte cobre CONTRATOS de banco
(RLS/isolamento) via API real do Supabase. O browser (Selenium) entra na
história quando a UI nascer.
"""

import os

REPO_ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))

# Backend Spring (regra/2+). A suíte exige o backend DE PÉ ao rodar.
API_URL = os.environ.get("MEADA_API_URL", "http://localhost:8095")

# Senha dos usuários descartáveis de teste (Supabase LOCAL apenas).
SELENIUM_PASSWORD = os.environ.get("MEADA_SELENIUM_PASSWORD", "Selenium.Meada.2026!")


def _parse_env_file(path: str) -> dict:
    """Parser mínimo de arquivo .env (KEY=VALUE, sem interpolação)."""
    values = {}
    if not os.path.exists(path):
        return values
    with open(path, encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if not line or line.startswith("#") or "=" not in line:
                continue
            key, _, value = line.partition("=")
            values[key.strip()] = value.strip().strip('"').strip("'")
    return values


_backend_env = _parse_env_file(os.path.join(REPO_ROOT, ".env"))
_frontend_env = _parse_env_file(os.path.join(REPO_ROOT, "frontend", ".env.local"))

SUPABASE_URL = _backend_env.get("SUPABASE_URL", "http://127.0.0.1:54321")
SERVICE_ROLE_KEY = _backend_env.get("SUPABASE_SERVICE_ROLE_KEY", "")
DB_PASSWORD = _backend_env.get("SPRING_DATASOURCE_PASSWORD", "")
ANON_KEY = _frontend_env.get("NEXT_PUBLIC_SUPABASE_ANON_KEY", "")
WEBHOOK_SECRET = _backend_env.get("WEBHOOK_SECRET", "")

DB_HOST = "127.0.0.1"
DB_PORT = "54322"

# Laboratório da suíte: 2 empresas descartáveis com UUIDs fixos (determinismo)
# para provar o isolamento multi-tenant. Nunca usa dados reais.
CORE_COMPANIES = {
    "alpha": {
        "id": "5e1e0000-0000-4000-8000-00000000000a",
        "name": "Selenium Core Alpha",
        "slug": "selenium-core-alpha",
    },
    "beta": {
        "id": "5e1e0000-0000-4000-8000-00000000000b",
        "name": "Selenium Core Beta",
        "slug": "selenium-core-beta",
    },
}

CORE_USERS = {
    "alpha": {"email": "selenium.core-alpha@meada.test", "company": "alpha"},
    "beta": {"email": "selenium.core-beta@meada.test", "company": "beta"},
}

# Instância WhatsApp do laboratório (regra/2+): chave de resolução do tenant no
# webhook. Token sintético — nunca fala com uma Evolution real.
CORE_INSTANCE = {
    "id": "5e1e0000-0000-4000-8000-0000000000aa",
    "company": "alpha",
    "instance_name": "selenium-core-alpha-wa",
    "evolution_token": "selenium-local-token",
}
