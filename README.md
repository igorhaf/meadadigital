# Meada

SaaS multi-empresa de atendimento ao cliente via WhatsApp com IA.

Cada empresa (tenant) tem um atendente de IA treinado com os próprios dados — serviços,
horários, preços, FAQs — respondendo seus clientes pelo WhatsApp, com isolamento total
por tenant (RLS no Postgres).

A visão: **um monolito que se apresenta como N produtos verticais** ("perfis"). O mesmo
core de mensageria + IA + outbound veste-se de produto de nicho — restaurante, clínica,
academia, ateliê — cada um com subdomínio, nome, tom de IA e features próprias.

## Stack

- **Backend:** Spring Boot 3 + Java 17 (JdbcTemplate, sem JPA)
- **Banco/Auth:** Supabase (Postgres + Auth + Storage), RLS por tenant
- **IA:** Gemini Flash
- **WhatsApp:** Evolution API self-hosted
- **Frontend:** Next.js (app router) + React + TypeScript + Tailwind

## Desenvolvimento

O projeto é construído **por regra de negócio**: cada regra nasce numa branch própria
(`regra/<camada>-<slug>`), desenvolvida em passos commitados, e só fecha em `main`
com a suíte de testes funcionais (Selenium) verde. O roadmap completo está em
[docs/RECONSTRUCAO.md](docs/RECONSTRUCAO.md).
