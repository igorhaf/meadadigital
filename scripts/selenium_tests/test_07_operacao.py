"""
regra/6 — operação de atendimento e plataforma.
================================================
O humano assume: inbox real alimentada pelo webhook, tags, convites virando
usuários do tenant, webchat público, trilha de acesso no login e auditoria
imutável das ações do root.
"""

import time
import uuid

import pytest
import requests
from selenium.webdriver.common.by import By

from config import (
    API_URL,
    BASE_URL,
    CORE_COMPANIES,
    CORE_INSTANCE,
    CORE_USERS,
    ROOT_EMAIL,
    SELENIUM_PASSWORD,
    WEBHOOK_SECRET,
)
from helpers import password_grant_token, psql, ui_login, wait_for_body, wait_for_element

ALPHA = CORE_COMPANIES["alpha"]["id"]
EMAIL_ALPHA = CORE_USERS["alpha"]["email"]
PHONE_INBOX = "+5511977772001"
INVITEE_EMAIL = "selenium.invitee@meada.test"


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
    time.sleep(3)
    psql(
        "delete from public.messages where conversation_id in "
        "(select c.id from public.conversations c join public.contacts ct on ct.id = c.contact_id "
        f" where ct.company_id = '{ALPHA}' and ct.phone_number = '{PHONE_INBOX}');"
    )
    psql(
        "delete from public.conversations where contact_id in "
        f"(select id from public.contacts where company_id = '{ALPHA}' and phone_number = '{PHONE_INBOX}');"
    )
    psql(f"delete from public.contacts where company_id = '{ALPHA}' and phone_number = '{PHONE_INBOX}';")
    psql(f"delete from public.tags where company_id = '{ALPHA}' and name like 'SELENIUM7%';")


def test_inbox_mostra_conversa_que_chegou_pelo_webhook(driver):
    # conversa em modo humano (o assunto é a INBOX, não a IA)
    psql(
        "insert into public.contacts (id, company_id, phone_number, name) "
        f"values ('{uuid.uuid4()}', '{ALPHA}', '{PHONE_INBOX}', 'Cliente Inbox') "
        "on conflict do nothing;"
    )
    psql(
        "insert into public.conversations (company_id, contact_id, whatsapp_instance_id, status, handled_by) "
        f"select '{ALPHA}', id, '{CORE_INSTANCE['id']}', 'open', 'human' "
        f"from public.contacts where company_id = '{ALPHA}' and phone_number = '{PHONE_INBOX}' "
        "and not exists (select 1 from public.conversations c2 where c2.contact_id = public.contacts.id and c2.status = 'open');"
    )
    marcador = f"pedido inbox {uuid.uuid4().hex[:6]}"
    body = {
        "event": "messages.upsert",
        "instance": CORE_INSTANCE["instance_name"],
        "data": {
            "key": {"id": f"SEL7-{uuid.uuid4().hex[:10]}", "remoteJid": PHONE_INBOX.lstrip('+') + "@s.whatsapp.net", "fromMe": False},
            "pushName": "Cliente Inbox",
            "messageTimestamp": int(time.time()),
            "message": {"conversation": marcador},
        },
    }
    resp = requests.post(f"{API_URL}/webhooks/evolution", json=body, headers={"apikey": WEBHOOK_SECRET}, timeout=15)
    assert resp.status_code == 200

    ui_login(driver, EMAIL_ALPHA)
    driver.get(f"{BASE_URL}/dashboard/conversations")
    wait_for_body(driver)
    deadline = time.time() + 20
    while time.time() < deadline:
        if "Cliente Inbox" in driver.find_element(By.TAG_NAME, "body").text:
            break
        time.sleep(2)
        driver.refresh()
        wait_for_body(driver)
    assert "Cliente Inbox" in driver.find_element(By.TAG_NAME, "body").text, "conversa não apareceu na inbox"


def test_convite_aceito_vira_usuario_do_tenant():
    token = uuid.uuid4().hex
    psql(
        "insert into public.tenant_invitations (company_id, email, token, expires_at) "
        f"values ('{ALPHA}', '{INVITEE_EMAIL}', '{token}', now() + interval '7 days');"
    )
    # o convidado cria conta (GoTrue admin, como faria pelo signup) e aceita
    import seed_users

    invitee_id = seed_users.find_auth_user(INVITEE_EMAIL)
    if invitee_id:
        seed_users.set_password(invitee_id)
    else:
        invitee_id = seed_users.create_auth_user(INVITEE_EMAIL)
    psql(f"delete from public.users where id = '{invitee_id}';")
    bearer = password_grant_token(INVITEE_EMAIL)
    resp = requests.post(
        f"{API_URL}/api/invitations/{token}/accept",
        headers={"Authorization": f"Bearer {bearer}"},
        timeout=15,
    )
    assert resp.status_code in (200, 201), f"{resp.status_code}: {resp.text[:200]}"
    company = psql(f"select company_id from public.users where id = '{invitee_id}';")
    assert company == ALPHA, "convidado não entrou no tenant do convite"
    psql(f"delete from public.users where id = '{invitee_id}';")
    psql(f"delete from public.tenant_invitations where token = '{token}';")


def test_webchat_publico_responde_sem_auth():
    slug = CORE_COMPANIES["alpha"]["slug"]
    resp = requests.post(
        f"{API_URL}/api/chat/{slug}",
        json={"message": "olá, qual o horário de vocês?", "sessionId": f"sel7-{uuid.uuid4().hex[:8]}"},
        timeout=60,
    )
    assert resp.status_code == 200, f"{resp.status_code}: {resp.text[:200]}"
    assert resp.json().get("reply"), "webchat sem reply (nem fallback)"


def test_login_deixa_trilha_no_access_log(driver):
    antes = int(psql(
        f"select count(*) from public.access_logs where email = '{EMAIL_ALPHA}' and action = 'login_success';"
    ))
    ui_login(driver, EMAIL_ALPHA)
    deadline = time.time() + 15
    depois = antes
    while time.time() < deadline and depois <= antes:
        depois = int(psql(
            f"select count(*) from public.access_logs where email = '{EMAIL_ALPHA}' and action = 'login_success';"
        ))
        time.sleep(1)
    assert depois > antes, "login_success não registrou no access_logs"


def test_acao_do_root_e_auditada():
    token_root = password_grant_token(ROOT_EMAIL)
    slug = f"selenium-audit-{uuid.uuid4().hex[:6]}"
    resp = requests.post(
        f"{API_URL}/admin/companies",
        headers={"Authorization": f"Bearer {token_root}", "Content-Type": "application/json"},
        json={"name": "Selenium Audit Co", "slug": slug, "paletteId": "meada-default"},
        timeout=15,
    )
    assert resp.status_code in (200, 201), f"{resp.status_code}: {resp.text[:200]}"
    company_id = resp.json()["company"]["id"]
    trilha = int(psql(
        f"select count(*) from public.audit_log where entity_id = '{company_id}';"
    ))
    assert trilha >= 1, "criação de empresa pelo root não deixou trilha no audit_log"
    # o create provisiona um tenant-admin determinístico — remover users antes da company
    psql(f"delete from public.audit_log where entity_id = '{company_id}' or company_id = '{company_id}';")
    psql(f"delete from auth.users where id in (select id from public.users where company_id = '{company_id}');")
    psql(f"delete from public.users where company_id = '{company_id}';")
    psql(f"delete from public.companies where id = '{company_id}';")
