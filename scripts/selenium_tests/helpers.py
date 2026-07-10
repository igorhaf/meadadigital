"""
Helpers compartilhados da suíte Selenium do Meada.
===================================================
Login/logout, esperas, captura de erros de página e de console.
Padrão espelhado da suíte do Orbit (~/orbit/scripts/selenium_tests).
"""

import os
from datetime import datetime

import requests
from selenium.webdriver.common.by import By
from selenium.webdriver.support import expected_conditions as EC
from selenium.webdriver.support.ui import WebDriverWait

from config import ANON_KEY, BASE_URL, SELENIUM_PASSWORD, SUPABASE_URL

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


def login(driver, email: str, password: str = SELENIUM_PASSWORD):
    """Login via UI real (/login) e espera o redirect pro /dashboard."""
    driver.get(f"{BASE_URL}/login")
    wait_for_element(driver, "#email").clear()
    driver.find_element(By.ID, "email").send_keys(email)
    driver.find_element(By.ID, "password").clear()
    driver.find_element(By.ID, "password").send_keys(password)
    driver.find_element(By.CSS_SELECTOR, "button[type='submit']").click()
    wait_for_url_contains(driver, "/dashboard")
    wait_for_body(driver)


def logout(driver):
    """Logout determinístico: derruba cookies+storage da sessão Supabase."""
    driver.delete_all_cookies()
    try:
        driver.execute_script("window.localStorage.clear(); window.sessionStorage.clear();")
    except Exception:
        pass  # página about:blank não tem storage
    driver.get(f"{BASE_URL}/login")


def get_sidebar_links(driver) -> list:
    """Coleta os hrefs únicos da sidebar (aside) que apontam pro dashboard."""
    anchors = driver.find_elements(By.CSS_SELECTOR, "aside a[href^='/dashboard']")
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


# ---- API helpers (contratos REST, sem browser) ----

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


def api_get(api_url: str, endpoint: str, token: str | None = None) -> requests.Response:
    headers = {}
    if token:
        headers["Authorization"] = f"Bearer {token}"
    return requests.get(f"{api_url}{endpoint}", headers=headers, timeout=20)
