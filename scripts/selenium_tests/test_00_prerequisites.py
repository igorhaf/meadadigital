"""
00 — Pré-requisitos do ambiente.
Falha aqui = ambiente não está de pé; o resto da suíte não é confiável.
"""

import requests

from config import API_URL, BASE_URL, NICHE_USERS, SUPABASE_URL
from helpers import password_grant_token


def test_backend_up_and_guarded():
    """Backend saudável = /admin/me sem token responde 401 missing_auth_header."""
    resp = requests.get(f"{API_URL}/admin/me", timeout=10)
    assert resp.status_code == 401, f"esperava 401, veio {resp.status_code}"
    body = resp.json()
    assert body.get("reason") == "missing_auth_header", f"reason inesperado: {body}"


def test_frontend_up():
    resp = requests.get(f"{BASE_URL}/login", timeout=15)
    assert resp.status_code == 200


def test_supabase_auth_up():
    resp = requests.get(f"{SUPABASE_URL}/auth/v1/health", timeout=10)
    assert resp.status_code == 200


def test_selenium_users_can_authenticate():
    """Todos os usuários seedados conseguem token via password grant."""
    failures = []
    for niche, info in NICHE_USERS.items():
        try:
            token = password_grant_token(info["email"])
            assert token
        except Exception as exc:  # noqa: BLE001
            failures.append(f"{niche} ({info['email']}): {exc}")
    assert not failures, (
        "Usuários sem login — rode python3 scripts/selenium_tests/seed_users.py:\n"
        + "\n".join(failures)
    )
