"""
Seeder idempotente dos usuários Selenium (Supabase LOCAL).
===========================================================
Cria (ou reaproveita) 1 usuário tenant-admin por nicho de teste via GoTrue
admin API (que cuida de instance_id/tokens corretamente — lição do seed via
SQL cru) e faz upsert da linha correspondente em public.users.

Uso:  python3 scripts/selenium_tests/seed_users.py
Pré:  supabase local rodando (127.0.0.1:54321 / 54322).
"""

import subprocess
import sys

import requests

from config import (
    DB_HOST,
    DB_PASSWORD,
    DB_PORT,
    NICHE_USERS,
    SELENIUM_PASSWORD,
    SERVICE_ROLE_KEY,
    SUPABASE_URL,
)


def psql(sql: str) -> str:
    """Roda SQL no Supabase local como postgres (fora do RLS). Retorna stdout cru."""
    result = subprocess.run(
        ["psql", "-h", DB_HOST, "-p", DB_PORT, "-U", "postgres", "-d", "postgres",
         "-t", "-A", "-c", sql],
        env={"PGPASSWORD": DB_PASSWORD, "PATH": "/usr/bin:/bin"},
        capture_output=True, text=True, timeout=15,
    )
    if result.returncode != 0:
        raise RuntimeError(f"psql falhou: {result.stderr.strip()}")
    return result.stdout.strip()


def find_auth_user(email: str) -> str | None:
    out = psql(f"select id from auth.users where email = '{email}' limit 1;")
    return out or None


def create_auth_user(email: str) -> str:
    resp = requests.post(
        f"{SUPABASE_URL}/auth/v1/admin/users",
        headers={
            "apikey": SERVICE_ROLE_KEY,
            "Authorization": f"Bearer {SERVICE_ROLE_KEY}",
            "Content-Type": "application/json",
        },
        json={"email": email, "password": SELENIUM_PASSWORD, "email_confirm": True},
        timeout=15,
    )
    if resp.status_code not in (200, 201):
        raise RuntimeError(f"GoTrue admin create falhou ({resp.status_code}): {resp.text[:300]}")
    return resp.json()["id"]


def set_password(user_id: str) -> None:
    """Garante a senha conhecida mesmo se o usuário já existia com outra."""
    resp = requests.put(
        f"{SUPABASE_URL}/auth/v1/admin/users/{user_id}",
        headers={
            "apikey": SERVICE_ROLE_KEY,
            "Authorization": f"Bearer {SERVICE_ROLE_KEY}",
            "Content-Type": "application/json",
        },
        json={"password": SELENIUM_PASSWORD},
        timeout=15,
    )
    if resp.status_code != 200:
        raise RuntimeError(f"GoTrue admin update falhou ({resp.status_code}): {resp.text[:300]}")


def upsert_public_user(user_id: str, email: str, company_id: str, niche: str) -> None:
    psql(
        "insert into public.users (id, company_id, email, full_name, role) "
        f"values ('{user_id}', '{company_id}', '{email}', 'Selenium {niche.title()}', 'admin') "
        "on conflict (id) do update set company_id = excluded.company_id, "
        "role = 'admin', deleted_at = null;"
    )


def main() -> int:
    if not SERVICE_ROLE_KEY:
        print("ERRO: SUPABASE_SERVICE_ROLE_KEY ausente no .env da raiz.")
        return 1
    failures = 0
    for niche, info in NICHE_USERS.items():
        email = info["email"]
        try:
            user_id = find_auth_user(email)
            if user_id:
                set_password(user_id)
                action = "reaproveitado"
            else:
                user_id = create_auth_user(email)
                action = "criado"
            upsert_public_user(user_id, email, info["company_id"], niche)
            print(f"[ok] {niche:10s} {email} ({action}, id={user_id})")
        except Exception as exc:  # noqa: BLE001 — seeder reporta e segue
            failures += 1
            print(f"[FALHA] {niche}: {exc}")
    return 1 if failures else 0


if __name__ == "__main__":
    sys.exit(main())
