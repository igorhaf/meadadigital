#!/usr/bin/env python3
"""
Gerador de perfil vertical do Meada — clona o chassi de um perfil EXEMPLAR com renames
determinísticos e REGENERA a CHECK de companies.profile_id a partir do enum (elimina a
armadilha da clonagem por sed, que trocava o id na lista e removia os demais perfis).

Uso (na raiz do repo):
  python3 scripts/gerar-perfil.py --id podologia --label Podologia --exemplar salon \
      --subdominio podologia --paleta celeste [--migration 34_salon.sql] [--dry-run]

O que ele FAZ (determinístico):
  1. Adiciona o membro no ProfileType.java e no PROFILES do profile-type.ts (PRESERVANDO
     todos os existentes).
  2. Gera supabase/migrations/NN_<id>.sql (NN = próximo número) clonando a migration-base
     do exemplar, com renames + CHECK REGENERADA com a lista COMPLETA de perfis.
  3. Clona src/main/java/com/meada/profiles/<exemplar>/ -> <id>/ (path + arquivo + conteúdo).
  4. Clona src/test/java/com/meada/profiles/<exemplar>/ -> <id>/.
  5. Clona frontend/profiles/<exemplar>/, frontend/lib/api/<exemplar>/ e
     frontend/app/(protected)/dashboard/<exemplar-kebab>-*/.
  6. Patcheia o JwtAuthenticationFilter (constante <ID>_PATH_PREFIX + cadeia startsWith).
  7. Imprime o checklist do que continua MANUAL (persona, OutboundService, nav-config,
     SCRIPTS do AbstractIntegrationTest, docs/PERFIL, tenant seed, textos pt-BR/escapada).

O que ele NÃO faz (de propósito): persona/travas, wiring no OutboundService, nav-config,
tela extra sem o prefixo do nicho, seed de tenant, docs. Isso é o trabalho de verdade do
agente — o gerador só elimina o clone mecânico.
"""

import argparse
import re
import shutil
import sys
from pathlib import Path

RAIZ = Path(__file__).resolve().parent.parent
ENUM_JAVA = RAIZ / "src/main/java/com/meada/profiles/ProfileType.java"
ENUM_TS = RAIZ / "frontend/lib/profiles/profile-type.ts"
JWT_FILTER = RAIZ / "src/main/java/com/meada/admin/security/JwtAuthenticationFilter.java"
MIGRATIONS = RAIZ / "supabase/migrations"

criados: list[Path] = []
editados: list[Path] = []


def variantes(pid: str) -> dict[str, str]:
    partes = pid.split("_")
    return {
        "snake": pid,                                        # moda_infantil
        "kebab": pid.replace("_", "-"),                      # moda-infantil
        "camel": "".join(p.capitalize() for p in partes),    # ModaInfantil
        "upper": pid.upper(),                                # MODA_INFANTIL
    }


def compilar_renames(exemplar: str, novo: str) -> list[tuple[re.Pattern, str]]:
    """Pares (regex com fronteira, substituto), do mais longo pro mais curto — as fronteiras
    impedem corromper palavras que CONTÊM o id (ex.: exemplar 'las' dentro de 'class')."""
    ve, vn = variantes(exemplar), variantes(novo)
    pares = []
    vistos = set()
    for chave in ("snake", "kebab", "camel", "upper"):
        e, n = ve[chave], vn[chave]
        if e in vistos:
            continue
        vistos.add(e)
        if chave in ("snake", "kebab"):
            rx = re.compile(rf"(?<![a-z0-9]){re.escape(e)}(?![a-z0-9])")
        elif chave == "camel":
            # (?<![A-Z0-9]) permite predecessor minúsculo: casa requireSalon/maybeProcessSalon
            rx = re.compile(rf"(?<![A-Z0-9]){re.escape(e)}(?![a-z])")
        else:  # upper
            rx = re.compile(rf"(?<![A-Z0-9]){re.escape(e)}(?![A-Z0-9])")
        pares.append((rx, n))
    pares.sort(key=lambda p: -len(p[0].pattern))
    return pares


def renomear(texto: str, pares) -> str:
    for rx, n in pares:
        texto = rx.sub(n, texto)
    return texto


def perfis_atuais() -> list[str]:
    """Ids do enum Java (fonte de verdade)."""
    src = ENUM_JAVA.read_text()
    ids = re.findall(r'^\s+[A-Z_]+\("([a-z0-9_]+)",', src, flags=re.M)
    if "generic" not in ids or len(ids) < 10:
        sys.exit(f"ERRO: parse do ProfileType.java achou só {len(ids)} ids — formato mudou?")
    return ids


def escrever(caminho: Path, conteudo: str, dry: bool, editado: bool = False):
    (editados if editado else criados).append(caminho)
    if dry:
        return
    caminho.parent.mkdir(parents=True, exist_ok=True)
    caminho.write_text(conteudo)


def patch_enum_java(args, dry: bool):
    src = ENUM_JAVA.read_text()
    novo = f'    {args.id.upper()}("{args.id}", "{args.label}", "{args.subdominio}", "{args.paleta}");'
    m = list(re.finditer(r'^(\s+[A-Z_]+\(".*"\));$', src, flags=re.M))
    if not m:
        sys.exit("ERRO: não achei o último membro do enum Java.")
    ultimo = m[-1]
    src = src[: ultimo.start()] + ultimo.group(1) + ",\n" + novo + src[ultimo.end():]
    escrever(ENUM_JAVA, src, dry, editado=True)


def patch_enum_ts(args, dry: bool):
    src = ENUM_TS.read_text()
    linha = (
        f"  {{ id: '{args.id}', productName: '{args.label}', "
        f"subdomain: '{args.subdominio}', defaultPaletteId: '{args.paleta}' }},\n"
    )
    m = re.search(r"^\] as const", src, flags=re.M) or re.search(r"^\]", src, flags=re.M)
    if not m:
        sys.exit("ERRO: não achei o fechamento do PROFILES no profile-type.ts.")
    src = src[: m.start()] + linha + src[m.start():]
    escrever(ENUM_TS, src, dry, editado=True)


def gerar_migration(args, pares, todos_ids: list[str], dry: bool) -> str:
    if args.migration:
        base = MIGRATIONS / args.migration
    else:
        candidatos = sorted(MIGRATIONS.glob(f"*_{args.exemplar}.sql"))
        if not candidatos:
            sys.exit(
                f"ERRO: não achei migration-base '*_{args.exemplar}.sql' — passe --migration."
            )
        base = candidatos[0]
    numeros = [int(m.group(1)) for f in MIGRATIONS.glob("*.sql")
               if (m := re.match(r"(\d+)_", f.name))]
    prox = max(numeros) + 1
    texto = renomear(base.read_text(), pares)
    # REGENERA a CHECK com a lista completa (a parte que o sed sempre errava):
    lista = todos_ids + [args.id]
    quoted = ",".join(f"'{i}'" for i in lista)
    linhas, atual = [], "  check (profile_id in ("
    for item in quoted.split(","):
        if len(atual) + len(item) + 1 > 100:
            linhas.append(atual)  # a vírgula fica no fim da linha (separador entre linhas)
            atual = "                        "
        atual += item + ","
    linhas.append(atual.rstrip(",") + "))")
    check_novo = "\n".join(linhas)
    texto, n = re.subn(r"check \(profile_id in \([\s\S]*?\)\)", check_novo, texto)
    if n == 0:
        sys.exit(f"ERRO: a migration-base {base.name} não tem a CHECK de profile_id.")
    destino = MIGRATIONS / f"{prox}_{args.id}.sql"
    escrever(destino, texto, dry)
    return destino.name


def clonar_arvore(origem: Path, destino_raiz: Path, pares, dry: bool):
    if not origem.exists():
        print(f"  (aviso: {origem.relative_to(RAIZ)} não existe — pulado)")
        return
    for f in sorted(origem.rglob("*")):
        if not f.is_file():
            continue
        rel = renomear(str(f.relative_to(origem)), pares)
        destino = destino_raiz / rel
        if destino.exists():
            sys.exit(f"ERRO: {destino.relative_to(RAIZ)} já existe — perfil já gerado?")
        escrever(destino, renomear(f.read_text(), pares), dry)


def patch_jwt_filter(args, dry: bool):
    src = JWT_FILTER.read_text()
    const = f'    private static final String {args.id.upper()}_PATH_PREFIX = "/api/{args.id.replace("_", "-")}/";'
    decls = list(re.finditer(r"^\s+private static final String \w+_PATH_PREFIX = .*;$", src, flags=re.M))
    src = src[: decls[-1].end()] + "\n" + const + src[decls[-1].end():]
    usos = list(re.finditer(r"^(\s+&& !uri\.startsWith\(\w+_PATH_PREFIX\))(;?)$", src, flags=re.M))
    if not usos:
        sys.exit("ERRO: não achei a cadeia startsWith no JwtAuthenticationFilter.")
    ultimo = usos[-1]
    indent = re.match(r"\s*", ultimo.group(1)).group(0)
    tinha_pv = ultimo.group(2) == ";"
    nova = f"{indent}&& !uri.startsWith({args.id.upper()}_PATH_PREFIX)" + (";" if tinha_pv else "")
    src = src[: ultimo.start()] + ultimo.group(1) + "\n" + nova + src[ultimo.end():]
    escrever(JWT_FILTER, src, dry, editado=True)


def main():
    ap = argparse.ArgumentParser(description="Gera um perfil vertical novo clonando um exemplar.")
    ap.add_argument("--id", required=True, help="id do novo perfil (snake_case, ex.: podologia)")
    ap.add_argument("--label", required=True, help="productName exibido (ex.: Podologia)")
    ap.add_argument("--exemplar", required=True, help="id de um perfil existente a clonar")
    ap.add_argument("--subdominio", help="subdomínio (default: id em kebab-case)")
    ap.add_argument("--paleta", default="meada-default", help="defaultPaletteId (lib/themes/palettes.ts)")
    ap.add_argument("--migration", help="nome da migration-base do exemplar (se o glob não achar)")
    ap.add_argument("--dry-run", action="store_true", help="só lista o que faria")
    args = ap.parse_args()
    args.subdominio = args.subdominio or args.id.replace("_", "-")

    if not re.fullmatch(r"[a-z][a-z0-9_]*", args.id):
        sys.exit("ERRO: --id precisa ser snake_case minúsculo.")
    ids = perfis_atuais()
    if args.id in ids:
        sys.exit(f"ERRO: perfil '{args.id}' já existe.")
    if args.exemplar not in ids:
        sys.exit(f"ERRO: exemplar '{args.exemplar}' não existe no ProfileType. Ids: {ids}")

    pares = compilar_renames(args.exemplar, args.id)
    dry = args.dry_run

    patch_enum_java(args, dry)
    patch_enum_ts(args, dry)
    mig = gerar_migration(args, pares, ids, dry)
    kebab_ex = args.exemplar.replace("_", "-")
    clonar_arvore(RAIZ / f"src/main/java/com/meada/profiles/{args.exemplar}",
                  RAIZ / f"src/main/java/com/meada/profiles/{args.id}", pares, dry)
    clonar_arvore(RAIZ / f"src/test/java/com/meada/profiles/{args.exemplar}",
                  RAIZ / f"src/test/java/com/meada/profiles/{args.id}", pares, dry)
    clonar_arvore(RAIZ / f"frontend/profiles/{args.exemplar}",
                  RAIZ / f"frontend/profiles/{args.id}", pares, dry)
    clonar_arvore(RAIZ / f"frontend/lib/api/{args.exemplar}",
                  RAIZ / f"frontend/lib/api/{args.id}", pares, dry)
    for pagina in sorted((RAIZ / "frontend/app/(protected)/dashboard").glob(f"{kebab_ex}-*")):
        clonar_arvore(pagina,
                      RAIZ / "frontend/app/(protected)/dashboard" /
                      renomear(pagina.name, pares), pares, dry)
    patch_jwt_filter(args, dry)

    modo = "DRY-RUN — nada escrito" if dry else "escrito"
    print(f"\n== gerar-perfil: {args.exemplar} -> {args.id} ({modo}) ==")
    print(f"migration: {mig}")
    print(f"editados ({len(editados)}):")
    for p in editados:
        print(f"  M {p.relative_to(RAIZ)}")
    print(f"criados ({len(criados)}):")
    for p in criados:
        print(f"  A {p.relative_to(RAIZ)}")
    print("""
== CHECKLIST MANUAL (o trabalho de verdade) ==
 1. Revisar a migration gerada (tabelas renomeadas; a CHECK já vem completa) e ADAPTAR
    colunas/regras à escapada do nicho novo.
 2. Persona: ProfilePromptContext.<ID> (travas do domínio!) + wiring do ContextCache.\n    O build FALHA até isso existir (switch exhaustivo do ProfilePromptContext) — proposital.
 3. TAG com namespace PRÓPRIO: o clone mantém a tag do exemplar — renomear a tag E a
    classe do handler se o nome não carrega o id do exemplar (ex.: AgendamentoConfirmHandler
    → colisão de BEAN Spring com o do exemplar; renomear p/ Agendamento<Id>ConfirmHandler).
 4. OutboundService: maybeProcessX encadeado + remoção da tag.
 5. nav-config: branch do perfil em getNavForProfile (+ telas sem o prefixo do nicho,
    se o exemplar tiver — o glob só clona dashboard/<exemplar>-*).
 6. AbstractIntegrationTest: migration nova no SCRIPTS — a que reescreve a CHECK entra
    por ÚLTIMO.
 7. Textos pt-BR clonados (labels/mensagens do exemplar) — adaptar ao nicho.
 8. Tenant de teste + seed; docs/PERFIL_<ID>.md; linha na tabela do CLAUDE.md.
 9. Gates: mvn -B clean test + cd frontend && npm run lint && npm run build.
""")


if __name__ == "__main__":
    main()
