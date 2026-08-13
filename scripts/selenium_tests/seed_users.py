"""
Seeder idempotente do laboratório da suíte (Supabase LOCAL).
=============================================================
Cria (ou reaproveita) as 2 empresas-laboratório e 1 tenant-admin por empresa,
via GoTrue admin API (que cuida de instance_id/tokens corretamente) + upsert
em public.users. Nunca toca em dados reais.

Uso:  python3 scripts/selenium_tests/seed_users.py   (o conftest também roda)
Pré:  supabase local rodando (127.0.0.1:54321 / 54322).
"""

import sys

import requests

from config import CORE_COMPANIES, CORE_USERS, SELENIUM_PASSWORD, SERVICE_ROLE_KEY, SUPABASE_URL
from helpers import psql


def _admin_headers() -> dict:
    return {
        "apikey": SERVICE_ROLE_KEY,
        "Authorization": f"Bearer {SERVICE_ROLE_KEY}",
        "Content-Type": "application/json",
    }


def ensure_company(info: dict) -> None:
    psql(
        "insert into public.companies (id, name, slug) "
        f"values ('{info['id']}', '{info['name']}', '{info['slug']}') "
        "on conflict (id) do nothing;"
    )


def find_auth_user(email: str) -> str | None:
    out = psql(f"select id from auth.users where email = '{email}' limit 1;")
    return out or None


def create_auth_user(email: str) -> str:
    resp = requests.post(
        f"{SUPABASE_URL}/auth/v1/admin/users",
        headers=_admin_headers(),
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
        headers=_admin_headers(),
        json={"password": SELENIUM_PASSWORD},
        timeout=15,
    )
    if resp.status_code != 200:
        raise RuntimeError(f"GoTrue admin update falhou ({resp.status_code}): {resp.text[:300]}")


def upsert_public_user(user_id: str, email: str, company_id: str, label: str) -> None:
    psql(
        "insert into public.users (id, company_id, email, full_name, role) "
        f"values ('{user_id}', '{company_id}', '{email}', 'Selenium {label.title()}', 'admin') "
        "on conflict (id) do update set company_id = excluded.company_id, role = 'admin';"
    )


def main() -> int:
    if not SERVICE_ROLE_KEY:
        print("ERRO: SUPABASE_SERVICE_ROLE_KEY ausente no .env da raiz.")
        return 1
    failures = 0
    for label, info in CORE_USERS.items():
        company = CORE_COMPANIES[info["company"]]
        try:
            ensure_company(company)
            email = info["email"]
            user_id = find_auth_user(email)
            if user_id:
                set_password(user_id)
                action = "reaproveitado"
            else:
                user_id = create_auth_user(email)
                action = "criado"
            upsert_public_user(user_id, email, company["id"], label)
            print(f"[ok] core-{label}: {email} ({action}, id={user_id})")
        except Exception as exc:  # noqa: BLE001 — seeder reporta e segue
            failures += 1
            print(f"[FALHA] core-{label}: {exc}")
    return 1 if failures else 0


if __name__ == "__main__":
    sys.exit(main())
