# Dockerfile do frontend Next.js 16 (Meada WhatsApp) — fase 0.5 (dockerização).
#
# dev  — node:20-alpine rodando `npm run dev` com o código por VOLUME (hot-reload). Target
#        usado pelo compose dev. node_modules e .next ficam em volumes anônimos (não são
#        sobrescritos pelo bind do código-fonte).
# prod — build standalone (futuro): output:'standalone' já configurado no next.config.

# ---- dev (hot-reload via volume) -----------------------------------------
FROM node:20-alpine AS dev

WORKDIR /app

# Deps primeiro (camada cacheável). O código real vem por volume no compose; aqui o COPY . .
# garante que a imagem-base já tenha tudo para o caso de rodar sem volume.
COPY package.json package-lock.json* ./
RUN npm ci

COPY . .

EXPOSE 3000

# --hostname 0.0.0.0: o Next dev só escuta localhost por padrão; sem isso o Caddy/host não
# alcançam o container.
CMD ["npm", "run", "dev", "--", "--hostname", "0.0.0.0"]

# ---- build de produção (futuro) ------------------------------------------
FROM node:20-alpine AS builder

WORKDIR /app

COPY package.json package-lock.json* ./
RUN npm ci

COPY . .
RUN npm run build

FROM node:20-alpine AS prod

WORKDIR /app

ENV NODE_ENV=production

# Next standalone: server.js + deps mínimas. static/ e public/ entram separados (o standalone
# não os inclui por design).
COPY --from=builder /app/.next/standalone ./
COPY --from=builder /app/.next/static ./.next/static
COPY --from=builder /app/public ./public

EXPOSE 3000

CMD ["node", "server.js"]
