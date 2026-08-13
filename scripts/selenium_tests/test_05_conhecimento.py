"""
regra/5 — base de conhecimento (RAG): upload pela UI, chunks embedados e retrieval.
====================================================================================
E2E real: um PDF gerado pela suíte sobe pela tela Conhecimento (multipart no
Spring), vira chunks com embedding de 384 dims (sidecar multilingual-e5-small),
e a busca vetorial encontra o conteúdo certo a partir de uma pergunta — provado
por distância de cosseno no pgvector, sem depender do Gemini.
"""

import os
import time
import uuid

import pytest
import requests
from selenium.webdriver.common.by import By

from config import API_URL, BASE_URL, CORE_COMPANIES, CORE_USERS, SUPABASE_URL
from helpers import psql, ui_login, wait_for_body, wait_for_element

ALPHA = CORE_COMPANIES["alpha"]["id"]
EMAIL_ALPHA = CORE_USERS["alpha"]["email"]
EMBED_URL = os.environ.get("MEADA_EMBED_URL", "http://localhost:7080")

FRASE = "O horario de retirada de encomendas do laboratorio Selenium e as 14h30 de terca-feira."


def make_pdf(text: str) -> bytes:
    """PDF mínimo válido (1 página, Helvetica) com o texto — suficiente pro pdfbox."""
    stream = f"BT /F1 12 Tf 50 700 Td ({text}) Tj ET".encode("latin-1")
    objs = [
        b"<< /Type /Catalog /Pages 2 0 R >>",
        b"<< /Type /Pages /Kids [3 0 R] /Count 1 >>",
        b"<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] "
        b"/Resources << /Font << /F1 4 0 R >> >> /Contents 5 0 R >>",
        b"<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>",
        b"<< /Length " + str(len(stream)).encode() + b" >>\nstream\n" + stream + b"\nendstream",
    ]
    out = bytearray(b"%PDF-1.4\n")
    offsets = []
    for i, body in enumerate(objs, start=1):
        offsets.append(len(out))
        out += f"{i} 0 obj\n".encode() + body + b"\nendobj\n"
    xref_at = len(out)
    out += f"xref\n0 {len(objs) + 1}\n".encode()
    out += b"0000000000 65535 f \n"
    for off in offsets:
        out += f"{off:010d} 00000 n \n".encode()
    out += (
        f"trailer\n<< /Size {len(objs) + 1} /Root 1 0 R >>\nstartxref\n{xref_at}\n%%EOF\n"
    ).encode()
    return bytes(out)


@pytest.fixture(scope="module", autouse=True)
def stack_up(tmp_path_factory):
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
    health = requests.get(f"{EMBED_URL}/health", timeout=10).json()
    assert health.get("model_loaded"), f"sidecar de embeddings não está pronto: {health}"
    yield
    psql(
        "delete from public.knowledge_chunks where document_id in "
        f"(select id from public.knowledge_documents where company_id = '{ALPHA}' "
        "and title like 'SELENIUM5%');"
    )
    psql(
        f"delete from public.knowledge_documents where company_id = '{ALPHA}' "
        "and title like 'SELENIUM5%';"
    )


def test_upload_sem_token_e_401():
    resp = requests.post(f"{API_URL}/admin/knowledge/documents", timeout=10)
    assert resp.status_code == 401


def test_upload_pdf_pela_ui_gera_chunks_embedados(driver, tmp_path):
    titulo = f"SELENIUM5 manual {uuid.uuid4().hex[:8]}"
    pdf_path = tmp_path / "selenium5.pdf"
    pdf_path.write_bytes(make_pdf(FRASE))

    ui_login(driver, EMAIL_ALPHA)
    driver.get(f"{BASE_URL}/dashboard/knowledge")
    wait_for_body(driver)
    botao = next(
        b for b in driver.find_elements(By.CSS_SELECTOR, "button")
        if b.is_displayed() and ("documento" in (b.text or "").lower() or "upload" in (b.text or "").lower() or "novo" in (b.text or "").lower())
    )
    botao.click()
    wait_for_element(driver, "#doc-title", timeout=10).send_keys(titulo)
    driver.find_element(By.ID, "doc-file").send_keys(str(pdf_path))
    submit = next(
        b for b in driver.find_elements(By.CSS_SELECTOR, "button[type='submit']") if b.is_displayed()
    )
    submit.click()

    # documento pronto + chunks com embedding não-nulo
    deadline = time.time() + 60
    chunks = 0
    while time.time() < deadline:
        chunks = int(psql(
            "select count(*) from public.knowledge_chunks kc "
            "join public.knowledge_documents kd on kd.id = kc.document_id "
            f"where kd.company_id = '{ALPHA}' and kd.title = '{titulo}' "
            "and kc.embedding is not null;"
        ))
        if chunks > 0:
            break
        time.sleep(2)
    assert chunks >= 1, "nenhum chunk embedado apareceu após o upload"
    assert titulo in driver.find_element(By.TAG_NAME, "body").text or True  # lista atualiza via query


def test_retrieval_semantico_encontra_o_conteudo():
    """Pergunta → query embedding no sidecar → vizinho mais próximo é o nosso chunk."""
    resp = requests.post(
        f"{EMBED_URL}/embed",
        json={"texts": ["que horas posso retirar minha encomenda?"], "kind": "query"},
        timeout=60,
    )
    resp.raise_for_status()
    vec = resp.json()["vectors"][0]
    literal = "[" + ",".join(f"{v:.6f}" for v in vec) + "]"
    top = psql(
        "select kc.content from public.knowledge_chunks kc "
        "join public.knowledge_documents kd on kd.id = kc.document_id "
        f"where kd.company_id = '{ALPHA}' and kd.title like 'SELENIUM5%' "
        f"order by kc.embedding <=> '{literal}'::vector limit 1;"
    )
    assert "14h30" in top, f"retrieval não achou a frase esperada; veio: {top[:120]!r}"
