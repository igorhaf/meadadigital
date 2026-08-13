"""
Fixtures da suíte de testes funcionais do Meada.
=================================================
Semeia o laboratório (empresas + usuários descartáveis) uma vez por sessão —
idempotente, então rodar a suíte é sempre um comando só.
"""

import pytest

import seed_users


@pytest.fixture(scope="session", autouse=True)
def laboratorio():
    """Seed idempotente antes de qualquer teste."""
    assert seed_users.main() == 0, "seed do laboratório falhou"
