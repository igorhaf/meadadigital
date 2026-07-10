"""
Configuração compartilhada da suíte Selenium do Meada.
=======================================================
Lê URLs e chaves dos envs REAIS do projeto (.env raiz e frontend/.env.local) —
nenhum segredo vive neste arquivo. A senha dos usuários de teste é sintética,
local-only (usuários descartáveis selenium.*@meada.test no Supabase LOCAL) e
pode ser trocada via env MEADA_SELENIUM_PASSWORD.
"""

import os

REPO_ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))

BASE_URL = os.environ.get("MEADA_FRONTEND_URL", "http://localhost:3000")
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

DB_HOST = "127.0.0.1"
DB_PORT = "54322"

# Usuários de teste: 1 por chassi estrutural (A..F/G), amarrados às empresas-modelo
# seedadas no Supabase local. NÃO tocar nos usuários reais do Igor (igorhaf*).
NICHE_USERS = {
    "salon": {
        "email": "selenium.salon@meada.test",
        "company_id": "371d42a6-2160-f74a-1915-b439c8b29eaa",  # Beleza Pura
        "chassis": "A (agenda por profissional)",
    },
    "sushi": {
        "email": "selenium.sushi@meada.test",
        "company_id": "37ada74c-da21-2e0c-ddeb-8d4ab4f778de",  # Sushi Legal
        "chassis": "B (pedido + gate de aceite)",
    },
    "lingerie": {
        "email": "selenium.lingerie@meada.test",
        "company_id": "c8000000-0000-0000-0000-000000000032",  # Lingerie Modelo
        "chassis": "C (variantes + estoque)",
    },
    "oficina": {
        "email": "selenium.oficina@meada.test",
        "company_id": "36d23c59-c1af-c7cb-c424-37da335ad556",  # Motor Forte
        "chassis": "D (proposta 2 fases)",
    },
    "academia": {
        "email": "selenium.academia@meada.test",
        "company_id": "9ab9c40a-f930-c847-28bd-c513f9fc2f81",  # Corpo em Forma
        "chassis": "E (assinatura/recorrência)",
    },
    "nutri": {
        "email": "selenium.nutri@meada.test",
        "company_id": "1664e5fb-164d-ec18-7a41-da6c839e78ea",  # Nutre Vida
        "chassis": "F+G (entrega read-only + sub-entidade)",
    },
}
