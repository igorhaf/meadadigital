"""
regra/2 — contratos do webhook inbound (Evolution → backend).
==============================================================
A porta de entrada do produto: secret em tempo constante, guards em ordem
(evento, fromMe, frescor, duplicata) sempre devolvendo 200 à Evolution, e a
inbound válida persistida de forma idempotente. Exercitado contra o backend
REAL rodando na porta 8095.
"""

import time
import uuid

import pytest
import requests

from config import API_URL, CORE_COMPANIES, CORE_INSTANCE, WEBHOOK_SECRET
from helpers import psql

WEBHOOK = f"{API_URL}/webhooks/evolution"
ALPHA = CORE_COMPANIES["alpha"]["id"]
PHONE_JID = "5511977770001@s.whatsapp.net"


def payload(text="oi, quero informações", from_me=False, msg_id=None, ts=None,
            event="messages.upsert", jid=PHONE_JID):
    return {
        "event": event,
        "instance": CORE_INSTANCE["instance_name"],
        "data": {
            "key": {
                "id": msg_id or f"SEL2-{uuid.uuid4().hex[:12]}",
                "remoteJid": jid,
                "fromMe": from_me,
            },
            "pushName": "Selenium Cliente",
            "messageTimestamp": ts if ts is not None else int(time.time()),
            "message": {"conversation": text},
        },
    }


def post(body, secret=WEBHOOK_SECRET):
    headers = {"Content-Type": "application/json"}
    if secret is not None:
        headers["apikey"] = secret
    return requests.post(WEBHOOK, json=body, headers=headers, timeout=15)


def count_msg(msg_id: str) -> int:
    return int(psql(f"select count(*) from public.messages where evolution_message_id = '{msg_id}';"))


@pytest.fixture(scope="module", autouse=True)
def backend_up():
    """Espera o backend responder (401 sem secret = filtro de pé) e limpa o rastro ao final."""
    deadline = time.time() + 90
    last = None
    while time.time() < deadline:
        try:
            last = requests.post(WEBHOOK, json={}, timeout=5).status_code
            if last == 401:
                break
        except requests.ConnectionError:
            last = "sem conexão"
        time.sleep(2)
    assert last == 401, f"backend não respondeu 401 sem secret na {WEBHOOK} (último: {last})"
    # O assunto deste módulo é o WEBHOOK, não a IA: pré-cria a conversa do contato-alvo em
    # modo humano — o pipeline async cai em SKIPPED_NOT_AI (não chama Gemini, não envia,
    # e não corre contra a limpeza do teardown).
    psql(
        "insert into public.contacts (id, company_id, phone_number, name) "
        f"values ('{uuid.uuid4()}', '{ALPHA}', '+5511977770001', 'Webhook Lab') "
        "on conflict do nothing;"
    )
    psql(
        "insert into public.conversations (company_id, contact_id, whatsapp_instance_id, status, handled_by) "
        f"select '{ALPHA}', id, '{CORE_INSTANCE['id']}', 'open', 'human' "
        f"from public.contacts where company_id = '{ALPHA}' and phone_number = '+5511977770001' "
        "and not exists (select 1 from public.conversations c2 where c2.contact_id = public.contacts.id and c2.status = 'open');"
    )
    yield
    time.sleep(4)  # deixa qualquer retry async em voo terminar antes de apagar o rastro
    psql(
        "delete from public.messages where conversation_id in "
        f"(select c.id from public.conversations c join public.contacts ct on ct.id = c.contact_id "
        f" where ct.company_id = '{ALPHA}' and ct.phone_number like '+5511977770%');"
    )
    psql(
        "delete from public.conversations where contact_id in "
        f"(select id from public.contacts where company_id = '{ALPHA}' and phone_number like '+5511977770%');"
    )
    psql(f"delete from public.contacts where company_id = '{ALPHA}' and phone_number like '+5511977770%';")


def test_sem_secret_e_401():
    assert post(payload(), secret=None).status_code == 401


def test_secret_errado_e_401():
    assert post(payload(), secret="segredo-invalido").status_code == 401


def test_evento_nao_message_e_ignorado_com_200():
    body = payload(event="connection.update")
    resp = post(body)
    assert resp.status_code == 200
    assert count_msg(body["data"]["key"]["id"]) == 0


def test_from_me_e_ignorado():
    body = payload(from_me=True)
    resp = post(body)
    assert resp.status_code == 200
    assert count_msg(body["data"]["key"]["id"]) == 0


def test_inbound_valida_persiste_contato_conversa_mensagem():
    body = payload()
    msg_id = body["data"]["key"]["id"]
    resp = post(body)
    assert resp.status_code == 200
    assert count_msg(msg_id) == 1, "mensagem inbound não foi persistida"
    abertas = int(psql(
        "select count(*) from public.conversations c "
        "join public.contacts ct on ct.id = c.contact_id "
        f"where ct.company_id = '{ALPHA}' and ct.phone_number = '+5511977770001' "
        "and c.status = 'open';"
    ))
    assert abertas == 1, f"esperava 1 conversa aberta do contato, achei {abertas}"


def test_reentrega_e_idempotente():
    body = payload()
    msg_id = body["data"]["key"]["id"]
    assert post(body).status_code == 200
    assert post(body).status_code == 200
    assert count_msg(msg_id) == 1, "reentrega duplicou a mensagem"


def test_mensagem_antiga_e_rejeitada_pelo_guard_de_frescor():
    body = payload(ts=int(time.time()) - 4000)
    resp = post(body)
    assert resp.status_code == 200
    assert count_msg(body["data"]["key"]["id"]) == 0, "mensagem velha passou pelo guard de frescor"
