"""
Fixtures da suíte Selenium do Meada.
=====================================
Chrome headless session-scoped (Selenium Manager resolve o driver),
screenshot automático em falha.
"""

import pytest
from selenium import webdriver
from selenium.webdriver.chrome.options import Options

from config import API_URL, BASE_URL
from helpers import take_screenshot


@pytest.fixture(scope="session")
def base_url():
    return BASE_URL


@pytest.fixture(scope="session")
def api_url():
    return API_URL


@pytest.fixture(scope="session")
def driver():
    opts = Options()
    opts.add_argument("--headless=new")
    opts.add_argument("--no-sandbox")
    opts.add_argument("--disable-dev-shm-usage")
    opts.add_argument("--disable-gpu")
    opts.add_argument("--window-size=1920,1080")
    opts.set_capability("goog:loggingPrefs", {"browser": "ALL"})

    d = webdriver.Chrome(options=opts)
    d.implicitly_wait(5)
    yield d
    d.quit()


@pytest.fixture(autouse=True)
def screenshot_on_failure(request):
    yield
    driver = request.node.funcargs.get("driver")
    if driver is None:
        return
    rep = getattr(request.node, "rep_call", None)
    if rep is not None and rep.failed:
        name = request.node.name.replace(" ", "_").replace("/", "_").replace("[", "_").replace("]", "")
        take_screenshot(driver, f"FAIL_{name}")


@pytest.hookimpl(tryfirst=True, hookwrapper=True)
def pytest_runtest_makereport(item, call):
    outcome = yield
    rep = outcome.get_result()
    setattr(item, f"rep_{rep.when}", rep)
