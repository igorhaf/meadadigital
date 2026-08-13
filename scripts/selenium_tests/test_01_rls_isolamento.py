"""
regra/1 — contratos de isolamento multi-tenant (RLS).
======================================================
O schema é a primeira regra de negócio do Meada: NENHUM dado de um tenant
pode ser lido ou escrito por outro. Estes testes exercitam o RLS pela API
real do Supabase (PostgREST + Auth), como um cliente de verdade faria.
"""

import uuid

import pytest

from config import CORE_COMPANIES, CORE_USERS, SERVICE_ROLE_KEY
from helpers import password_grant_token, psql, rest_get, rest_post

ALPHA = CORE_COMPANIES["alpha"]["id"]
BETA = CORE_COMPANIES["beta"]["id"]


@pytest.fixture(scope="module")
def token_alpha():
    return password_grant_token(CORE_USERS["alpha"]["email"])


def test_anon_sem_login_nao_le_companies():
    resp = rest_get("/companies?select=id")
    assert resp.status_code != 200 or resp.json() == [], (
        f"anon sem login leu companies: {resp.status_code} {resp.text[:200]}"
    )


def test_tenant_ve_apenas_a_propria_empresa(token_alpha):
    resp = rest_get("/companies?select=id", token=token_alpha)
    assert resp.status_code == 200, resp.text[:200]
    ids = {row["id"] for row in resp.json()}
    assert ids == {ALPHA}, f"tenant alpha enxergou: {ids}"


def test_leitura_cross_tenant_volta_vazia(token_alpha):
    # Insert idempotente (on conflict SEM alvo: cobre tanto o id quanto a UNIQUE
    # company+phone de rodadas anteriores); o id REAL é relido por company+phone.
    psql(
        "insert into public.contacts (id, company_id, phone_number, name) "
        f"values ('{uuid.uuid4()}', '{BETA}', '+5511900000001', 'Contato Beta') "
        "on conflict do nothing;"
    )
    contact_id = psql(
        "select id from public.contacts "
        f"where company_id = '{BETA}' and phone_number = '+5511900000001' limit 1;"
    )
    assert contact_id, "contato-laboratório do beta não existe"
    resp = rest_get("/contacts?select=id", token=token_alpha)
    assert resp.status_code == 200, resp.text[:200]
    ids = {row["id"] for row in resp.json()}
    assert contact_id not in ids, "contato do tenant beta vazou para o alpha"


def test_escrita_cross_tenant_bloqueada(token_alpha):
    resp = rest_post(
        "/contacts",
        {"company_id": BETA, "phone_number": "+5511900000002", "name": "Invasor"},
        token=token_alpha,
    )
    assert resp.status_code in (401, 403), (
        f"WITH CHECK deveria bloquear escrita cross-tenant: {resp.status_code} {resp.text[:200]}"
    )


def test_escrita_no_proprio_tenant_funciona(token_alpha):
    phone = "+5511911110001"
    resp = rest_post(
        "/contacts",
        {"company_id": ALPHA, "phone_number": phone, "name": "Contato Alpha"},
        token=token_alpha,
    )
    assert resp.status_code in (200, 201), f"{resp.status_code} {resp.text[:200]}"
    psql(f"delete from public.contacts where company_id = '{ALPHA}' and phone_number = '{phone}';")


def test_service_role_e_plataforma_ve_tudo():
    resp = rest_get("/companies?select=id", key=SERVICE_ROLE_KEY)
    assert resp.status_code == 200, resp.text[:200]
    ids = {row["id"] for row in resp.json()}
    assert {ALPHA, BETA} <= ids, "service_role não enxergou as empresas-laboratório"
