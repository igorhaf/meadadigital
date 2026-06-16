# Roadmap MVP — Meada WhatsApp

Lista de 50 features para o MVP funcional do SaaS de atendimento WhatsApp+IA agnóstico. Marcação `[x]` = implementada (com tag de fase); `[ ]` = pendente; `[~]` = abandonada intencionalmente.

## Infraestrutura & Auth

- [x] 1. Multi-tenant com isolamento via RLS no Postgres
- [x] 2. Login com email/senha via Supabase Auth
- [x] 3. Painel admin separado (super-admin vs tenant-admin)
- [x] 4. Super-admin cria empresas com paleta de tema customizada (fase-5.1.a)
- [~] 5. Recuperação de senha por email (abandonada — reset via psql em dev)
- [x] 6. Convite de usuário extra pro tenant (multi-user por empresa) (fase-5.16)
- [x] 7. Logs de auditoria (fase-5.3)

## Conexão com WhatsApp

- [x] 8. Integração Evolution API (webhook recebe mensagens)
- [ ] 9. Painel pra tenant escanear QR code e conectar WhatsApp dele
- [ ] 10. Status visual da conexão (conectado/desconectado/reconectando)
- [ ] 11. Reconexão automática quando WhatsApp cai
- [ ] 12. Envio de mensagem outbound via Evolution (parcial — falta 4.10)

## Conversas

- [x] 13. Lista de conversas com último status, contato, atendente
- [x] 14. Tela de detalhe da conversa com histórico de mensagens
- [x] 15. Polling automático (mensagem nova aparece sem refresh)
- [x] 16. Tenant troca handled_by (IA ↔ humano)
- [x] 17. Tenant fecha/reabre conversa
- [ ] 18. Tenant responde manualmente (envia mensagem pelo painel)
- [ ] 19. Anexar foto/arquivo na resposta manual
- [x] 20. Marcar conversa como "não lida" pra revisitar depois (fase-5.14)
- [x] 21. Filtrar conversas (busca por contato/telefone) (fase-5.7)
- [x] 22. Tag/etiqueta em conversa ("VIP", "urgente", "follow-up") (fase-5.14)

## IA e atendimento automático

- [x] 23. IA Gemini responde mensagens automaticamente
- [x] 24. Tenant configura tom, regras, restrições, gatilhos de handoff
- [x] 25. IA detecta sozinha quando passar pra humano (gatilho automático) (fase-3.2 + fase-3.3)
- [x] 26. IA usa as FAQs cadastradas como conhecimento (fase-3.2)
- [x] 27. IA conhece os serviços cadastrados (nome, descrição, preço) (fase-3.2)
- [x] 28. IA respeita horário comercial (responde fora do horário com mensagem padrão) (fase-5.4)
- [x] 29. IA reconhece intent de agendamento (sem agendar ainda, só sinaliza) (fase-5.15)
- [x] 30. Histórico da conversa entra no contexto da IA (fase-3.2)

## Conteúdo do tenant

- [x] 31. CRUD de Serviços (nome, descrição, preço)
- [x] 32. CRUD de FAQs (pergunta/resposta)
- [x] 33. Horários de funcionamento (7 dias, abre/fecha)
- [x] 34. Edição de FAQ existente (hoje só cria) (fase-5.5)
- [x] 35. Edição de serviço existente (hoje só cria) (fase-5.5)
- [x] 36. Ativar/desativar FAQ ou serviço sem deletar (fase-5.6)
- [x] 37. Upload de documento (PDF, etc.) pra IA usar como conhecimento (fase-5.13: RAG completo — sidecar de embeddings via docker-compose, pgvector, backend de ingestão, retrieval semântico no prompt, painel do tenant)

## Contatos

- [x] 38. Lista de contatos (todas as pessoas que mandaram mensagem) (fase-5.11)
- [x] 39. Detalhe do contato (histórico de conversas, telefone, nome) (fase-5.11)
- [x] 40. Editar nome do contato manualmente (fase-5.11)
- [x] 41. Bloquear contato (não recebe mais resposta automática) (fase-5.11)

## Métricas e dashboard

- [x] 42. Dashboard com números (conversas hoje, taxa de resolução IA, tempo médio) (fase-5.12)
- [x] 43. Gráfico de mensagens por dia (últimos 30 dias) (fase-5.12)
- [x] 44. Ranking de FAQs mais usadas pela IA (fase-5.12)
- [x] 45. Tempo médio de resposta (IA vs humano) (fase-5.12)

## Onboarding e UX

- [x] 46. Tutorial guiado na primeira vez que tenant loga (fase-5.14)
- [x] 47. Página vazia com call-to-action quando não tem dados (fase-5.8)
- [x] 48. Notificação visual (badge no menu) quando chega mensagem nova (fase-5.10)
- [x] 49. Modo escuro (dark mode) (fase-5.9)

## Monetização

- [ ] 50. Cobrança recorrente (plano mensal, limite de mensagens por tier)

---

## Maratona pós-MVP (features #51+) — camada 5.17 em diante

### IA mais inteligente
- [x] 51. Intent de cancelamento
- [x] 52. Intent de reclamação (força handoff)
- [x] 53. Coleta de dados estruturados (extracted_data jsonb)
- [x] 54. Sugestões de FAQ
- [x] 55. Memória de longo prazo do contato (contact_memory jsonb)
- [ ] 57. Modo treinamento (melhorar resposta da IA)
- [x] 58. Tom dinâmico por perfil do contato

### Agendamento real
- [x] 59. Calendário visual
- [x] 60. IA agenda de fato
- [x] 61. Slots de disponibilidade (fase-5.17)
- [ ] 62. Integração Google Calendar
- [x] 63. Lembretes automáticos
- [x] 64. Remarcar/cancelar via WhatsApp

### Análise
- [ ] 65. Dashboard exportável PDF
- [ ] 66. Comparação mês a mês
- [ ] 68. Top contatos

### Multi-canal
- [ ] 72. Suporte email
- [ ] 73. Widget de chat no site
- [ ] 74. Unificação multi-canal

### Multi-tenant avançado
- [x] 75. Hierarquia de roles (owner/admin/agent) (fase-5.17)
- [x] 76. Times/departamentos
- [x] 77. Limites por plano
- [x] 78. Audit log visível

### Engajamento
- [x] 81. Reativação automática
- [x] 82. Boas-vindas

### UX painel
- [x] 83. Notificações tempo real
- [x] 84. Busca global
- [x] 88. Saved replies

### Compliance
- [ ] 89. LGPD exclusão
- [ ] 90. LGPD exportação
- [ ] 92. Logs de acesso

---

Convenção: ao fechar feature, mover de `[ ]` pra `[x]` no mesmo commit da fase técnica, adicionando tag de fase entre parênteses (ex.: "(fase-5.1.a)").
