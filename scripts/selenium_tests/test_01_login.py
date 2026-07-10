"""
01 — Fluxo de login pela UI real.
Cobre: mensagem genérica anti-enumeration, login válido, logout.
"""

from selenium.webdriver.common.by import By
from selenium.webdriver.support.ui import WebDriverWait

from config import NICHE_USERS
from helpers import login, logout, wait_for_element


def test_invalid_password_shows_generic_error(driver, base_url):
    logout(driver)
    driver.get(f"{base_url}/login")
    wait_for_element(driver, "#email").send_keys(NICHE_USERS["salon"]["email"])
    driver.find_element(By.ID, "password").send_keys("senha-errada-de-proposito")
    driver.find_element(By.CSS_SELECTOR, "button[type='submit']").click()

    WebDriverWait(driver, 15).until(
        lambda d: "Email ou senha inválidos." in d.find_element(By.TAG_NAME, "body").text
    )
    assert "/login" in driver.current_url, "não deveria sair do /login com senha errada"


def test_valid_login_reaches_dashboard(driver, base_url):
    logout(driver)
    login(driver, NICHE_USERS["salon"]["email"])
    assert "/dashboard" in driver.current_url
    # Sidebar do painel presente
    wait_for_element(driver, "aside")


def test_logout_blocks_protected_route(driver, base_url):
    logout(driver)
    driver.get(f"{base_url}/dashboard")
    # Layout protegido deve redirecionar pro /login sem sessão
    WebDriverWait(driver, 15).until(lambda d: "/login" in d.current_url)
