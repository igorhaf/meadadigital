"""
02 — Varredura funcional da navegação por nicho (1 tenant por chassi).
Para cada nicho: loga, coleta TODOS os links da sidebar e visita um a um,
falhando se qualquer tela renderizar erro (404/500/crash React) ou logar
erro SEVERE real no console. É o detector de "experiência ruim" cross-nicho.
"""

import pytest

from config import NICHE_USERS
from helpers import (
    check_console_errors,
    check_page_errors,
    get_sidebar_links,
    login,
    logout,
    take_screenshot,
    wait_for_body,
)


@pytest.mark.parametrize("niche", list(NICHE_USERS.keys()))
def test_sidebar_full_walk(driver, base_url, niche):
    info = NICHE_USERS[niche]
    logout(driver)
    login(driver, info["email"])
    take_screenshot(driver, f"dashboard_{niche}")

    links = get_sidebar_links(driver)
    assert links, f"[{niche}] sidebar sem links de dashboard — nav quebrada?"

    # limpa o buffer de console acumulado no login
    check_console_errors(driver)

    failures = []
    for path in links:
        driver.get(f"{base_url}{path}")
        try:
            wait_for_body(driver)
        except Exception:
            failures.append(f"{path}: página não renderizou corpo em 20s")
            continue

        page_errors = check_page_errors(driver)
        if page_errors:
            take_screenshot(driver, f"ERR_{niche}_{path.replace('/', '_')}")
            failures.append(f"{path}: {', '.join(page_errors)}")

        console_real, _noise = check_console_errors(driver)
        if console_real:
            first = console_real[0][:200]
            failures.append(f"{path}: console SEVERE ({len(console_real)}): {first}")

    assert not failures, (
        f"[{niche} / chassi {info['chassis']}] {len(failures)} tela(s) com problema "
        f"de {len(links)} visitadas:\n" + "\n".join(failures)
    )
