# gerar-perfil.py — gerador de perfil vertical

Elimina o custo do loop "ler exemplar → clonar → adaptar" ao criar um nicho novo: o clone
mecânico (enum + migration + pacote backend + testes + types/api/telas frontend + filtro JWT)
vira UM comando determinístico; o agente gasta esforço só no que é novo de verdade
(escapada, persona/travas, wiring).

## Uso

```bash
python3 scripts/gerar-perfil.py --id podologia --label Podologia --exemplar salon \
    --paleta celeste [--subdominio podologia] [--migration 34_salon.sql] [--dry-run]
```

Escolha o `--exemplar` pelo CHASSI do nicho novo (tabela dos 7 chassis no CLAUDE.md):

| Chassi do nicho novo | Exemplar recomendado |
|----------------------|----------------------|
| Agenda por profissional | `salon` (ou `dermatologia` p/ tipos em tabela + sub-entidade paciente) |
| Pedido + gate de aceite | `comida` (variações: `padaria` lead time, `floricultura` entrega agendada) |
| Varejo variantes+estoque | `lingerie` (ou `moda_infantil` p/ restock ao cancelar) |
| Proposta 2 fases | `eventos` (ou `atelie`/`casamento` conforme sub-itens) |
| Assinatura/recorrência | `academia` (ou `escola` p/ sub-entidade aluno) |
| Entrega read-only | `nutri` (ou `cursos` p/ progresso) |
| Sub-entidade do contato | `pet` |

## O que ele faz / não faz

FAZ (determinístico, com fronteiras de palavra nos renames e CHECK regenerada da lista
completa do enum — a armadilha do sed morreu): ver docstring do script.

NÃO FAZ (de propósito — é o trabalho do agente): persona/travas, tag com namespace novo
(+ rename do handler se o nome não carrega o id — colisão de bean Spring), wiring no
OutboundService, nav-config, telas sem o prefixo do nicho, SCRIPTS de teste, tenant seed,
textos pt-BR, docs/PERFIL. O script imprime o checklist completo ao final.

**O build FALHA até a persona existir** (switch exhaustivo do `ProfilePromptContext` sobre o
enum) — é proposital: impede esquecer o passo.

## Validação (feita em 2026-07-03)

Gerado um perfil descartável (`zoologico` ← salon, 40 arquivos): renames sem resíduo do
exemplar, CHECK com os 35 ids sem duplicata, clone compila (`mvn test-compile` só acusa o
switch exhaustivo da persona, como esperado). O descartável foi revertido — nenhum perfil
fake existe no repo.
