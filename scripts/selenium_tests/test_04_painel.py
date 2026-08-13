"""
regra/4 — o painel nasce: login, autorização, navegação e CRUD via SDK+RLS.
============================================================================
Browser de verdade (Chrome headless) sobre o frontend em build de produção +
backend real. Cobre: anti-enumeration no login, barreira de autorização do
layout protegido, navegação sem crash/console SEVERE, o contrato do
GET /admin/me e a escrita do tenant via SDK Supabase (RLS revalida).
"""

import time
import uuid

import pytest
import requests
from selenium.webdriver.common.by import By

from config import API_URL, BASE_URL, CORE_COMPANIES, CORE_USERS, SELENIUM_PASSWORD
from helpers import (
    check_console_errors,
    check_page_errors,
    get_sidebar_links,
    password_grant_token,
    psql,
    ui_login,
    ui_logout,
    wait_for_body,
    wait_for_element,
    wait_for_url_contains,
)

ALPHA = CORE_COMPANIES["alpha"]["id"]
EMAIL_ALPHA = CORE_USERS["alpha"]["email"]


@pytest.fixture(scope="module", autouse=True)
def stack_up():
    """Backend + frontend de pé; limpa o rastro de FAQs de teste ao final."""
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
    assert back == 401, f"backend fora do ar ({back})"
    assert front == 200, f"frontend fora do ar ({front})"
    yield
    psql(f"delete from public.faqs where company_id = '{ALPHA}' and question like 'SELENIUM4%';")


def test_login_invalido_mostra_mensagem_generica(driver):
    ui_logout(driver)
    driver.get(f"{BASE_URL}/login")
    wait_for_element(driver, "#email").send_keys(EMAIL_ALPHA)
    driver.find_element(By.ID, "password").send_keys("senha-completamente-errada")
    driver.find_element(By.CSS_SELECTOR, "button[type='submit']").click()
    err = wait_for_element(driver, "p.text-destructive", timeout=10)
    assert "Email ou senha inválidos" in err.text, f"mensagem inesperada: {err.text!r}"
    assert "/login" in driver.current_url


def test_rota_protegida_sem_sessao_redireciona_pro_login(driver):
    ui_logout(driver)
    driver.get(f"{BASE_URL}/dashboard/faqs")
    wait_for_url_contains(driver, "/login")


def test_login_valido_entra_no_dashboard(driver):
    ui_login(driver, EMAIL_ALPHA)
    assert "/dashboard" in driver.current_url
    erros = check_page_errors(driver)
    assert not erros, f"erros na home: {erros}"


def test_navegacao_sidebar_sem_crash(driver):
    ui_login(driver, EMAIL_ALPHA)
    # Drena o buffer de console: o log é global da sessão do Chrome e ainda carrega o
    # 'login failed' ESPERADO do teste de anti-enumeration acima.
    check_console_errors(driver)
    links = get_sidebar_links(driver)
    assert len(links) >= 5, f"sidebar com poucos links: {links}"
    problemas = []
    for path in links:
        driver.get(f"{BASE_URL}{path}")
        wait_for_body(driver)
        page_errors = check_page_errors(driver)
        console_errors, _ = check_console_errors(driver)
        if page_errors:
            problemas.append(f"{path}: {page_errors}")
        if console_errors:
            problemas.append(f"{path} console: {console_errors[:2]}")
    assert not problemas, "páginas com erro:\n" + "\n".join(problemas)


def test_admin_me_contrato_do_tenant():
    token = password_grant_token(EMAIL_ALPHA)
    resp = requests.get(
        f"{API_URL}/admin/me", headers={"Authorization": f"Bearer {token}"}, timeout=15
    )
    assert resp.status_code == 200, f"{resp.status_code}: {resp.text[:200]}"
    me = resp.json()
    assert me["role"] == "tenant_admin"
    assert me["companyId"] == ALPHA
    resp_sem_token = requests.get(f"{API_URL}/admin/me", timeout=15)
    assert resp_sem_token.status_code == 401


def test_criar_faq_pela_ui_via_sdk_rls(driver):
    ui_login(driver, EMAIL_ALPHA)
    driver.get(f"{BASE_URL}/dashboard/faqs")
    wait_for_body(driver)
    pergunta = f"SELENIUM4 pergunta {uuid.uuid4().hex[:8]}?"

    # abre o diálogo (botão "Nova FAQ" ou o CTA do estado vazio)
    botao = next(
        b for b in driver.find_elements(By.CSS_SELECTOR, "button")
        if "FAQ" in (b.text or "")
    )
    botao.click()
    campos = wait_for_element(driver, "textarea, input[name='question'], #question", timeout=10)

    # preenche pergunta e resposta (1º e 2º campos de texto do diálogo)
    texts = driver.find_elements(By.CSS_SELECTOR, "[role='dialog'] textarea, [role='dialog'] input[type='text']")
    if len(texts) < 2:
        texts = driver.find_elements(By.CSS_SELECTOR, "textarea, input[type='text']")
    assert len(texts) >= 2, "não achei os campos do diálogo de FAQ"
    texts[0].send_keys(pergunta)
    texts[1].send_keys("Resposta criada pela suíte Selenium (regra/4).")
    submit = next(
        b for b in driver.find_elements(By.CSS_SELECTOR, "button[type='submit']")
        if b.is_displayed()
    )
    submit.click()

    # a linha aparece (TanStack invalida a query) e está no banco com o company certo
    deadline = time.time() + 15
    while time.time() < deadline:
        if pergunta in driver.find_element(By.TAG_NAME, "body").text:
            break
        time.sleep(1)
    assert pergunta in driver.find_element(By.TAG_NAME, "body").text, "FAQ não apareceu na lista"
    count = int(psql(
        f"select count(*) from public.faqs where company_id = '{ALPHA}' and question = '{pergunta}';"
    ))
    assert count == 1, "FAQ não persistiu no tenant certo"
