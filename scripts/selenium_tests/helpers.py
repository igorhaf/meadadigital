"""
Helpers compartilhados da suíte de testes funcionais.
======================================================
Nesta camada: psql fora do RLS (setup/teardown), password grant real do
Supabase Auth e chamadas REST (PostgREST) autenticadas como um tenant.
"""

import subprocess

import requests

from config import ANON_KEY, DB_HOST, DB_PASSWORD, DB_PORT, SELENIUM_PASSWORD, SUPABASE_URL


def psql(sql: str) -> str:
    """Roda SQL no Supabase local como postgres (fora do RLS). Retorna stdout cru."""
    result = subprocess.run(
        ["psql", "-h", DB_HOST, "-p", DB_PORT, "-U", "postgres", "-d", "postgres",
         "-t", "-A", "-c", sql],
        env={"PGPASSWORD": DB_PASSWORD or "postgres", "PATH": "/usr/bin:/bin"},
        capture_output=True, text=True, timeout=15,
    )
    if result.returncode != 0:
        raise RuntimeError(f"psql falhou: {result.stderr.strip()}")
    return result.stdout.strip()


def password_grant_token(email: str, password: str = SELENIUM_PASSWORD) -> str:
    """Token de acesso via Supabase password grant (fluxo real de login)."""
    resp = requests.post(
        f"{SUPABASE_URL}/auth/v1/token?grant_type=password",
        headers={"apikey": ANON_KEY, "Content-Type": "application/json"},
        json={"email": email, "password": password},
        timeout=15,
    )
    resp.raise_for_status()
    return resp.json()["access_token"]


def rest_get(path: str, token: str | None = None, key: str | None = None) -> requests.Response:
    """GET no PostgREST. key default = anon; token = sessão de um usuário logado."""
    apikey = key or ANON_KEY
    headers = {"apikey": apikey, "Authorization": f"Bearer {token or apikey}"}
    return requests.get(f"{SUPABASE_URL}/rest/v1{path}", headers=headers, timeout=15)


def rest_post(path: str, payload, token: str | None = None, key: str | None = None) -> requests.Response:
    """POST no PostgREST com return=representation."""
    apikey = key or ANON_KEY
    headers = {
        "apikey": apikey,
        "Authorization": f"Bearer {token or apikey}",
        "Content-Type": "application/json",
        "Prefer": "return=representation",
    }
    return requests.post(f"{SUPABASE_URL}/rest/v1{path}", headers=headers, json=payload, timeout=15)
