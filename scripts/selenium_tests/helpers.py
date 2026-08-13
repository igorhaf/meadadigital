"""
Helpers compartilhados da suíte de testes funcionais.
======================================================
psql fora do RLS (setup/teardown), password grant real do Supabase Auth,
chamadas REST (PostgREST) autenticadas como um tenant — e, a partir da
regra/4 (painel), login/logout pela UI real, esperas e captura de erros de
página e de console.
"""

import os
import subprocess
from datetime import datetime

import requests
from selenium.webdriver.common.by import By
from selenium.webdriver.support import expected_conditions as EC
from selenium.webdriver.support.ui import WebDriverWait

from config import (
    ANON_KEY,
    BASE_URL,
    DB_HOST,
    DB_PASSWORD,
    DB_PORT,
    SELENIUM_PASSWORD,
    SUPABASE_URL,
)

SCREENSHOTS_DIR = os.path.join(os.path.dirname(__file__), "screenshots")

# Ruído esperado no console (não conta como falha funcional).
NOISE_PATTERNS = [
    "favicon.ico",
    "net::ERR_CONNECTION_REFUSED",
    "chrome-extension://",
    "Download the React DevTools",
    "was preloaded using link preload",
    # 401 transitório do refresh de sessão do Supabase durante navegação de login/logout
    "/auth/v1/token",
]


def take_screenshot(driver, name: str) -> str:
    os.makedirs(SCREENSHOTS_DIR, exist_ok=True)
    ts = datetime.now().strftime("%Y%m%d_%H%M%S")
    filepath = os.path.join(SCREENSHOTS_DIR, f"{ts}_{name}.png")
    driver.save_screenshot(filepath)
    return filepath


def wait_for_body(driver, timeout=20):
    WebDriverWait(driver, timeout).until(
        lambda d: len(d.find_element(By.TAG_NAME, "body").text.strip()) > 5
    )


def wait_for_element(driver, selector: str, timeout=15, by=By.CSS_SELECTOR):
    return WebDriverWait(driver, timeout).until(
        EC.presence_of_element_located((by, selector))
    )


def wait_for_url_contains(driver, fragment: str, timeout=20):
    WebDriverWait(driver, timeout).until(EC.url_contains(fragment))


def ui_login(driver, email: str, password: str = SELENIUM_PASSWORD):
    """Login via UI real (/login) e espera o redirect pro /dashboard."""
    driver.get(f"{BASE_URL}/login")
    wait_for_element(driver, "#email").clear()
    driver.find_element(By.ID, "email").send_keys(email)
    driver.find_element(By.ID, "password").clear()
    driver.find_element(By.ID, "password").send_keys(password)
    driver.find_element(By.CSS_SELECTOR, "button[type='submit']").click()
    wait_for_url_contains(driver, "/dashboard")
    wait_for_body(driver)


def ui_logout(driver):
    """Logout determinístico: derruba cookies+storage da sessão Supabase."""
    driver.delete_all_cookies()
    try:
        driver.execute_script("window.localStorage.clear(); window.sessionStorage.clear();")
    except Exception:
        pass  # página about:blank não tem storage
    driver.get(f"{BASE_URL}/login")


def get_sidebar_links(driver) -> list:
    """Coleta os hrefs únicos da sidebar que apontam pro dashboard."""
    anchors = driver.find_elements(By.CSS_SELECTOR, "a[href^='/dashboard']")
    hrefs = []
    for a in anchors:
        href = a.get_attribute("href") or ""
        path = href.replace(BASE_URL, "")
        if path and path not in hrefs:
            hrefs.append(path)
    return hrefs


def check_page_errors(driver) -> list:
    """Erros visíveis na página (crash de renderização, 404, 500)."""
    body = driver.find_element(By.TAG_NAME, "body").text.lower()
    errors = []
    for pattern, label in [
        ("internal server error", "Internal Server Error"),
        ("application error", "Application error (client exception)"),
        ("unhandled runtime error", "Unhandled Runtime Error"),
        ("cannot read properties", "TypeError: cannot read properties"),
        ("minified react error", "Minified React error"),
    ]:
        if pattern in body:
            errors.append(label)
    if "this page could not be found" in body or ("404" in body and "not found" in body):
        errors.append("404 Not Found")
    return errors


def check_console_errors(driver) -> tuple:
    """(erros_reais, ruido) — só entradas SEVERE do console do browser."""
    real, noise = [], []
    try:
        for entry in driver.get_log("browser"):
            if entry.get("level") != "SEVERE":
                continue
            msg = entry.get("message", "")
            (noise if any(p in msg for p in NOISE_PATTERNS) else real).append(msg)
    except Exception:
        pass
    return real, noise


def psql(sql: str) -> str:
    """Roda SQL no Supabase local como postgres (fora do RLS). Retorna stdout cru."""
    result = subprocess.run(
        ["psql", "-h", DB_HOST, "-p", DB_PORT, "-U", "postgres", "-d", "postgres",
         "-t", "-A", "-c", sql],
        env={"PGPASSWORD": DB_PASSWORD or "postgres", "PATH": "/usr/bin:/bin"},
        capture_output=True, text=True, timeout=15,
    )
    if result.returncode != 0:
        raise RuntimeError(f"psql falhou: {result.stderr.strip()}")
    return result.stdout.strip()


def password_grant_token(email: str, password: str = SELENIUM_PASSWORD) -> str:
    """Token de acesso via Supabase password grant (fluxo real de login)."""
    resp = requests.post(
        f"{SUPABASE_URL}/auth/v1/token?grant_type=password",
        headers={"apikey": ANON_KEY, "Content-Type": "application/json"},
        json={"email": email, "password": password},
        timeout=15,
    )
    resp.raise_for_status()
    return resp.json()["access_token"]


def rest_get(path: str, token: str | None = None, key: str | None = None) -> requests.Response:
    """GET no PostgREST. key default = anon; token = sessão de um usuário logado."""
    apikey = key or ANON_KEY
    headers = {"apikey": apikey, "Authorization": f"Bearer {token or apikey}"}
    return requests.get(f"{SUPABASE_URL}/rest/v1{path}", headers=headers, timeout=15)


def rest_post(path: str, payload, token: str | None = None, key: str | None = None) -> requests.Response:
    """POST no PostgREST com return=representation."""
    apikey = key or ANON_KEY
    headers = {
        "apikey": apikey,
        "Authorization": f"Bearer {token or apikey}",
        "Content-Type": "application/json",
        "Prefer": "return=representation",
    }
    return requests.post(f"{SUPABASE_URL}/rest/v1{path}", headers=headers, json=payload, timeout=15)
