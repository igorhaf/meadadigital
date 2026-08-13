"""
regra/7.0 — fundação multi-perfil: identidade do produto e match user×subdomínio.
==================================================================================
O tenant tem EXATAMENTE 1 perfil (cravado pelo root; default generic). O /admin/me
carrega a identidade do produto, e o profile-match decide se um usuário pode entrar
pelo subdomínio de um produto — sem nunca vazar em qual produto a conta está.
"""

import time

import pytest
import requests

from config import API_URL, CORE_COMPANIES, CORE_USERS
from helpers import password_grant_token

ALPHA = CORE_COMPANIES["alpha"]["id"]
EMAIL_ALPHA = CORE_USERS["alpha"]["email"]


@pytest.fixture(scope="module", autouse=True)
def backend_up():
    deadline = time.time() + 90
    last = None
    while time.time() < deadline:
        try:
            last = requests.post(f"{API_URL}/webhooks/evolution", json={}, timeout=5).status_code
            if last == 401:
                break
        except requests.ConnectionError:
            last = "sem conexão"
        time.sleep(2)
    assert last == 401, f"backend fora do ar ({last})"


def _get(path: str, token: str | None = None):
    headers = {"Authorization": f"Bearer {token}"} if token else {}
    return requests.get(f"{API_URL}{path}", headers=headers, timeout=15)


def test_me_carrega_identidade_do_produto():
    token = password_grant_token(EMAIL_ALPHA)
    me = _get("/admin/me", token)
    assert me.status_code == 200, me.text[:200]
    body = me.json()
    assert body["profileId"] == "generic"
    assert body["productName"] == "Meada"
    assert body.get("paletteId"), "paletteId ausente no /admin/me"


def test_catalogo_de_perfis_e_ferramenta_do_root():
    from config import ROOT_EMAIL

    assert _get("/admin/profiles").status_code == 401
    # tenant NÃO enxerga o catálogo (é a ferramenta do root pra cravar perfil)
    token_tenant = password_grant_token(EMAIL_ALPHA)
    negado = _get("/admin/profiles", token_tenant)
    assert negado.status_code == 403 and negado.json()["reason"] == "forbidden_not_super_admin"
    # root lista os perfis declarados
    token_root = password_grant_token(ROOT_EMAIL)
    resp = _get("/admin/profiles", token_root)
    assert resp.status_code == 200, resp.text[:200]
    ids = {p["id"] for p in resp.json()["items"]}
    assert {"generic", "sushi", "legal", "dental"} <= ids, f"catálogo incompleto: {ids}"


def test_profile_match_nao_vaza_produto():
    token = password_grant_token(EMAIL_ALPHA)
    # tenant generic num subdomínio de produto → match false (login mostrará credencial inválida)
    errado = _get("/admin/me/profile-match?subdomain=sushi", token)
    assert errado.status_code == 200 and errado.json()["match"] is False
    # subdomínio desconhecido → 400 unknown_subdomain
    invalido = _get("/admin/me/profile-match?subdomain=nao-existe", token)
    assert invalido.status_code == 400 and invalido.json()["reason"] == "unknown_subdomain"


def test_empresa_nasce_generic_por_default():
    from helpers import psql

    profile = psql(f"select profile_id from public.companies where id = '{ALPHA}';")
    assert profile == "generic"
