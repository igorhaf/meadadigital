# Roadmap MVP — Meada WhatsApp

Lista de 50 features para o MVP funcional do SaaS de atendimento WhatsApp+IA agnóstico. Marcação `[x]` = implementada (com tag de fase); `[ ]` = pendente; `[~]` = abandonada intencionalmente.

## Infraestrutura & Auth

- [x] 1. Multi-tenant com isolamento via RLS no Postgres
- [x] 2. Login com email/senha via Supabase Auth
- [x] 3. Painel admin separado (super-admin vs tenant-admin)
- [x] 4. Super-admin cria empresas com paleta de tema customizada (fase-5.1.a)
- [~] 5. Recuperação de senha por email (abandonada — reset via psql em dev)
- [ ] 6. Convite de usuário extra pro tenant (multi-user por empresa)
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
- [ ] 20. Marcar conversa como "não lida" pra revisitar depois
- [ ] 21. Filtrar conversas (status, atendente, busca por texto)
- [ ] 22. Tag/etiqueta em conversa ("VIP", "urgente", "follow-up")

## IA e atendimento automático

- [x] 23. IA Gemini responde mensagens automaticamente
- [x] 24. Tenant configura tom, regras, restrições, gatilhos de handoff
- [ ] 25. IA detecta sozinha quando passar pra humano (gatilho automático)
- [x] 26. IA usa as FAQs cadastradas como conhecimento (fase-3.2)
- [x] 27. IA conhece os serviços cadastrados (nome, descrição, preço) (fase-3.2)
- [x] 28. IA respeita horário comercial (responde fora do horário com mensagem padrão) (fase-5.4)
- [ ] 29. IA reconhece intent de agendamento (sem agendar ainda, só sinaliza)
- [ ] 30. Histórico da conversa entra no contexto da IA

## Conteúdo do tenant

- [x] 31. CRUD de Serviços (nome, descrição, preço)
- [x] 32. CRUD de FAQs (pergunta/resposta)
- [x] 33. Horários de funcionamento (7 dias, abre/fecha)
- [x] 34. Edição de FAQ existente (hoje só cria) (fase-5.5)
- [x] 35. Edição de serviço existente (hoje só cria) (fase-5.5)
- [ ] 36. Ativar/desativar FAQ ou serviço sem deletar
- [ ] 37. Upload de documento (PDF, etc.) pra IA usar como conhecimento

## Contatos

- [ ] 38. Lista de contatos (todas as pessoas que mandaram mensagem)
- [ ] 39. Detalhe do contato (histórico de conversas, telefone, nome)
- [ ] 40. Editar nome do contato manualmente
- [ ] 41. Bloquear contato (não recebe mais resposta automática)

## Métricas e dashboard

- [ ] 42. Dashboard com números (conversas hoje, taxa de resolução IA, tempo médio)
- [ ] 43. Gráfico de mensagens por dia (últimos 30 dias)
- [ ] 44. Ranking de FAQs mais usadas pela IA
- [ ] 45. Tempo médio de resposta (IA vs humano)

## Onboarding e UX

- [ ] 46. Tutorial guiado na primeira vez que tenant loga
- [ ] 47. Página vazia com call-to-action quando não tem dados
- [ ] 48. Notificação visual (badge no menu) quando chega mensagem nova
- [ ] 49. Modo escuro (dark mode)

## Monetização

- [ ] 50. Cobrança recorrente (plano mensal, limite de mensagens por tier)

---

Convenção: ao fechar feature, mover de `[ ]` pra `[x]` no mesmo commit da fase técnica, adicionando tag de fase entre parênteses (ex.: "(fase-5.1.a)").
