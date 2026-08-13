"""
regra/3 — E2E da resposta da IA (Gemini REAL + Evolution em dry-run).
======================================================================
O ciclo inteiro do produto: inbound pelo webhook → prompt com contexto do
tenant → Gemini → resposta persistida como outbound. O envio fica em dry-run
(EVOLUTION_DRY_RUN=true no .env — lição do incidente de re-sync): o id da
mensagem enviada é sintético 'dry-run-*', provando que NADA saiu de verdade.
"""

import time
import uuid

import pytest
import requests

from config import API_URL, CORE_COMPANIES, CORE_INSTANCE, WEBHOOK_SECRET
from helpers import psql

WEBHOOK = f"{API_URL}/webhooks/evolution"
ALPHA = CORE_COMPANIES["alpha"]["id"]
PHONE_HUMANO = "+5511977771001"
PHONE_IA = "+5511977771002"


def inbound(phone_e164: str, text: str):
    jid = phone_e164.lstrip("+") + "@s.whatsapp.net"
    body = {
        "event": "messages.upsert",
        "instance": CORE_INSTANCE["instance_name"],
        "data": {
            "key": {"id": f"SEL3-{uuid.uuid4().hex[:12]}", "remoteJid": jid, "fromMe": False},
            "pushName": "Selenium Cliente",
            "messageTimestamp": int(time.time()),
            "message": {"conversation": text},
        },
    }
    return requests.post(WEBHOOK, json=body, headers={"apikey": WEBHOOK_SECRET}, timeout=15)


def _outbound_where(phone_e164: str) -> str:
    return (
        "from public.messages m "
        "join public.conversations c on c.id = m.conversation_id "
        "join public.contacts ct on ct.id = c.contact_id "
        f"where ct.company_id = '{ALPHA}' and ct.phone_number = '{phone_e164}' "
        "and m.direction = 'outbound' and m.sender = 'ai'"
    )


def count_outbound(phone_e164: str) -> int:
    return int(psql(f"select count(*) {_outbound_where(phone_e164)};"))


@pytest.fixture(scope="module", autouse=True)
def backend_up():
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
    assert last == 401, f"backend não está de pé em {WEBHOOK} (último: {last})"
    yield
    psql(
        "delete from public.messages where conversation_id in "
        "(select c.id from public.conversations c join public.contacts ct on ct.id = c.contact_id "
        f" where ct.company_id = '{ALPHA}' and ct.phone_number like '+55119777710%');"
    )
    psql(
        "delete from public.conversations where contact_id in "
        f"(select id from public.contacts where company_id = '{ALPHA}' and phone_number like '+55119777710%');"
    )
    psql(f"delete from public.contacts where company_id = '{ALPHA}' and phone_number like '+55119777710%';")


def test_conversa_com_humano_silencia_a_ia():
    """Gate handled_by: conversa em atendimento humano NÃO dispara IA."""
    contact_id = str(uuid.uuid4())
    psql(
        "insert into public.contacts (id, company_id, phone_number, name) "
        f"values ('{contact_id}', '{ALPHA}', '{PHONE_HUMANO}', 'Cliente do Humano') "
        "on conflict do nothing;"
    )
    psql(
        "insert into public.conversations (company_id, contact_id, whatsapp_instance_id, status, handled_by) "
        f"select '{ALPHA}', id, '{CORE_INSTANCE['id']}', 'open', 'human' "
        f"from public.contacts where company_id = '{ALPHA}' and phone_number = '{PHONE_HUMANO}' "
        "on conflict do nothing;"
    )
    assert inbound(PHONE_HUMANO, "quero falar com alguém").status_code == 200
    time.sleep(5)
    assert count_outbound(PHONE_HUMANO) == 0, "a IA respondeu numa conversa de humano"


def _handled_by(phone_e164: str) -> str:
    return psql(
        "select c.handled_by from public.conversations c "
        "join public.contacts ct on ct.id = c.contact_id "
        f"where ct.company_id = '{ALPHA}' and ct.phone_number = '{phone_e164}' "
        "and c.status = 'open' limit 1;"
    )


def test_ia_responde_ou_flipa_para_humano():
    """Contrato do pipeline sob Gemini REAL: ou a resposta é persistida (feliz), ou —
    em falha permanente do provedor (ex.: 429 exaurindo o retry) — a conversa FLIPA
    para humano (nunca fica sem dono). Qualquer outro desfecho é vermelho."""
    assert inbound(PHONE_IA, "Olá! Vocês estão abertos agora? O que vocês oferecem?").status_code == 200
    deadline = time.time() + 90
    while time.time() < deadline:
        if count_outbound(PHONE_IA) > 0 or _handled_by(PHONE_IA) == "human":
            break
        time.sleep(3)

    if count_outbound(PHONE_IA) >= 1:
        tem_conteudo = psql(
            f"select bool_and(length(trim(m.content)) > 0) {_outbound_where(PHONE_IA)};"
        )
        assert tem_conteudo == "t", "resposta da IA vazia"
        so_dry_run = psql(
            f"select bool_and(m.evolution_message_id like 'dry-run-%') {_outbound_where(PHONE_IA)};"
        )
        assert so_dry_run == "t", "houve envio com id real — dry-run não segurou"
    else:
        assert _handled_by(PHONE_IA) == "human", (
            "nem resposta da IA nem flip para humano em 90s — pipeline mudo"
        )
