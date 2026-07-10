"""
03 — CRUD core via UI real (FAQs — caminho SDK+RLS, vale pra todos os nichos).
Cria uma FAQ com marcador único, confere na tabela, e limpa via SQL no teardown.
"""

import subprocess
import time

import pytest
from selenium.webdriver.common.by import By
from selenium.webdriver.support.ui import WebDriverWait

from config import DB_HOST, DB_PASSWORD, DB_PORT, NICHE_USERS
from helpers import login, logout, wait_for_element

MARKER = f"Selenium FAQ {int(time.time())}"


def _cleanup_marker_faqs():
    subprocess.run(
        ["psql", "-h", DB_HOST, "-p", DB_PORT, "-U", "postgres", "-d", "postgres",
         "-c", "delete from public.faqs where question like 'Selenium FAQ %';"],
        env={"PGPASSWORD": DB_PASSWORD, "PATH": "/usr/bin:/bin"},
        capture_output=True, text=True, timeout=15,
    )


@pytest.fixture(autouse=True)
def cleanup_after():
    yield
    _cleanup_marker_faqs()


def test_create_faq_via_ui(driver, base_url):
    logout(driver)
    login(driver, NICHE_USERS["salon"]["email"])
    driver.get(f"{base_url}/dashboard/faqs")

    # Abre o diálogo de criação
    WebDriverWait(driver, 15).until(
        lambda d: any("Nova FAQ" in b.text for b in d.find_elements(By.TAG_NAME, "button"))
    )
    for b in driver.find_elements(By.TAG_NAME, "button"):
        if "Nova FAQ" in b.text:
            b.click()
            break

    wait_for_element(driver, "#question").send_keys(MARKER)
    driver.find_element(By.ID, "answer").send_keys("Resposta criada pela suíte Selenium.")
    for b in driver.find_elements(By.CSS_SELECTOR, "button[type='submit']"):
        if b.text.strip() == "Criar":
            b.click()
            break

    # A linha nova aparece na tabela (invalidação do TanStack funcionou)
    WebDriverWait(driver, 15).until(
        lambda d: MARKER in d.find_element(By.TAG_NAME, "body").text
    )
