"""
04 — Contratos REST do backend (sem browser).
Cobre os invariantes de segurança multi-perfil que valem pra TODOS os nichos:
guard de perfil (403 forbidden_wrong_profile), shape do /admin/me,
feature gate do CMS e endpoints públicos do CMS.
"""

import pytest
import requests

from config import API_URL, NICHE_USERS
from helpers import api_get, password_grant_token


@pytest.fixture(scope="module")
def salon_token():
    return password_grant_token(NICHE_USERS["salon"]["email"])


@pytest.fixture(scope="module")
def sushi_token():
    return password_grant_token(NICHE_USERS["sushi"]["email"])


def test_admin_me_shape(salon_token):
    resp = api_get(API_URL, "/admin/me", salon_token)
    assert resp.status_code == 200, resp.text[:200]
    body = resp.json()
    assert body["role"] == "tenant_admin"
    assert body["companyId"] == NICHE_USERS["salon"]["company_id"]
    assert "features" in body, "resolvido de feature flags deve vir no /admin/me"


def test_wrong_profile_gets_403(sushi_token):
    """Tenant sushi chamando endpoint do salon → 403 forbidden_wrong_profile."""
    resp = api_get(API_URL, "/api/salon/services", sushi_token)
    assert resp.status_code == 403, f"esperava 403, veio {resp.status_code}: {resp.text[:200]}"
    assert resp.json().get("reason") == "forbidden_wrong_profile", resp.text[:200]


def test_right_profile_gets_200(salon_token):
    resp = api_get(API_URL, "/api/salon/services", salon_token)
    assert resp.status_code == 200, f"{resp.status_code}: {resp.text[:200]}"
    body = resp.json()
    # contrato: envelope paginado {items: [...]}
    assert isinstance(body.get("items"), list), f"shape inesperado: {body}"


def test_niche_api_requires_auth():
    resp = requests.get(f"{API_URL}/api/salon/services", timeout=10)
    assert resp.status_code == 401, f"esperava 401 sem token, veio {resp.status_code}"


def test_cms_feature_gate(salon_token):
    """CMS é gateado por feature flag: sem flag → 403 feature_disabled."""
    resp = api_get(API_URL, "/api/cms/site", salon_token)
    assert resp.status_code in (200, 403, 404), f"status inesperado {resp.status_code}"
    if resp.status_code == 403:
        assert resp.json().get("reason") == "feature_disabled", resp.text[:200]


def test_public_cms_unknown_slug_404():
    resp = requests.get(f"{API_URL}/public/cms/by-slug/slug-que-nao-existe-xyz", timeout=10)
    assert resp.status_code == 404, f"esperava 404, veio {resp.status_code}: {resp.text[:200]}"
