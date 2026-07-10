# Dockerfile do backend Spring Boot (Meada) — fase 0.5 (dockerização).
#
# 3 estágios:
#   builder  — compila o jar (sem testes; testes batem no Supabase real e rodam host-side
#              como gate pré-commit).
#   runtime  — imagem fina (JRE) com o jar pronto; usada em produção (futuro).
#   dev      — JDK + Maven; NÃO precompila. O código vem por VOLUME (./src, pom.xml) e
#              o container roda `mvn spring-boot:run` com hot-reload. É o target do compose dev.
#
# Banco: Supabase REMOTO (lido do .env via env_file no compose). Nada de Postgres aqui.

# ---- builder -------------------------------------------------------------
# Imagem com Maven + JDK 17 (o temurin puro NÃO traz o mvn). Compila o jar de produção.
FROM maven:3.9-eclipse-temurin-17 AS builder

WORKDIR /build

# Dependências primeiro (camada cacheável): copia só o pom e baixa o que dá, depois o código.
COPY pom.xml .
COPY src ./src
COPY supabase ./supabase

# Compila o jar. -DskipTests: os testes de integração exigem o Supabase real + Testcontainers
# (Docker-in-Docker), que não cabe no build da imagem — eles rodam no host como gate.
RUN mvn -B -DskipTests package

# ---- runtime (produção — futuro) -----------------------------------------
FROM eclipse-temurin:17-jre AS runtime

WORKDIR /app

COPY --from=builder /build/target/*.jar /app/app.jar

EXPOSE 8095

ENTRYPOINT ["java", "-jar", "/app/app.jar"]

# ---- dev (hot-reload via volume) -----------------------------------------
# NÃO precompila: o source é montado via volume (./src, pom.xml, supabase) e o Maven roda
# DENTRO do container. Mudou um .java → spring-boot:run recompila sem rebuild da imagem.
# O ~/.m2 é um volume nomeado (maven-cache) para não rebaixar deps a cada `up`.
FROM eclipse-temurin:17-jdk AS dev

WORKDIR /app

# Maven não vem na imagem temurin; instala via apt (camada estável, cacheável).
RUN apt-get update \
    && apt-get install -y --no-install-recommends maven curl \
    && rm -rf /var/lib/apt/lists/*

EXPOSE 8095

# spring-boot:run lê as envs do ambiente do container (env_file do compose → Supabase remoto).
CMD ["mvn", "-B", "spring-boot:run"]
