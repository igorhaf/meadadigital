"""
regra/7.1 — perfil sushi: o primeiro produto vertical.
=======================================================
O mesmo monolito agora É um restaurante de sushi para o tenant certo: guard de
perfil (403 forbidden_wrong_profile para os demais), cardápio via REST, telas
próprias na sidebar e persona/cardápio no prompt. O gate de aceite do pedido é
HUMANO — a IA nunca aceita.
"""

import time
import uuid

import pytest
import requests
from selenium.webdriver.common.by import By

from config import API_URL, BASE_URL, CORE_USERS, NICHE_COMPANIES
from helpers import password_grant_token, psql, ui_login, wait_for_body

SUSHI = NICHE_COMPANIES["sushi"]
EMAIL_GENERIC = CORE_USERS["alpha"]["email"]

SUSHI_PAGES = [
    "/dashboard/sushi-menu",
    "/dashboard/sushi-categories",
    "/dashboard/sushi-orders",
    "/dashboard/sushi-statuses",
    "/dashboard/sushi-coupons",
    "/dashboard/sushi-loyalty",
    "/dashboard/sushi-settings",
]


@pytest.fixture(scope="module", autouse=True)
def stack_up():
    deadline = time.time() + 90
    back = front = None
    while time.time() < deadline:
        try:
            back = requests.post(f"{API_URL}/webhooks/evolution", json={}, timeout=5).status_code
            front = requests.get(f"{BASE_URL}/login", timeout=5).status_code
            if back == 401 and front == 200:
                break
        except requests.ConnectionError:
            pass
        time.sleep(2)
    assert back == 401 and front == 200, f"stack fora do ar (back={back}, front={front})"
    yield
    psql(f"delete from public.sushi_menu_items where company_id = '{SUSHI['id']}' and name like 'SELENIUM8%';")


def _get(path, token):
    return requests.get(f"{API_URL}{path}", headers={"Authorization": f"Bearer {token}"}, timeout=15)


def test_guard_bloqueia_tenant_de_outro_perfil():
    token_generic = password_grant_token(EMAIL_GENERIC)
    resp = _get("/api/sushi/menu", token_generic)
    assert resp.status_code == 403, f"{resp.status_code}: {resp.text[:150]}"
    assert resp.json()["reason"] == "forbidden_wrong_profile"


def test_sem_token_e_401():
    assert requests.get(f"{API_URL}/api/sushi/menu", timeout=10).status_code == 401


def test_tenant_sushi_gerencia_o_cardapio():
    token = password_grant_token(SUSHI["email"])
    nome = f"SELENIUM8 Filadélfia {uuid.uuid4().hex[:6]}"
    criado = requests.post(
        f"{API_URL}/api/sushi/menu",
        headers={"Authorization": f"Bearer {token}", "Content-Type": "application/json"},
        json={"name": nome, "priceCents": 3490},
        timeout=15,
    )
    assert criado.status_code in (200, 201), f"{criado.status_code}: {criado.text[:200]}"
    lista = _get("/api/sushi/menu", token)
    assert lista.status_code == 200
    assert nome in lista.text, "item criado não apareceu na listagem"


def test_me_do_tenant_sushi_carrega_o_produto():
    token = password_grant_token(SUSHI["email"])
    me = _get("/admin/me", token)
    assert me.status_code == 200
    body = me.json()
    assert body["profileId"] == "sushi"
    assert body["productName"], "productName vazio"


def test_telas_do_sushi_sem_crash(driver):
    from helpers import check_console_errors, check_page_errors

    ui_login(driver, SUSHI["email"])
    check_console_errors(driver)  # drena buffer
    problemas = []
    for path in SUSHI_PAGES:
        driver.get(f"{BASE_URL}{path}")
        wait_for_body(driver)
        page_errors = check_page_errors(driver)
        console_errors, _ = check_console_errors(driver)
        if page_errors:
            problemas.append(f"{path}: {page_errors}")
        if console_errors:
            problemas.append(f"{path} console: {console_errors[:2]}")
    assert not problemas, "telas com erro:\n" + "\n".join(problemas)
