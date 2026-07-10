# Integrações — como ativar

As integrações já estão no código. Basta gerar as credenciais (grátis) e colar no
`.env`. Depois: `docker compose exec app php artisan config:clear`.

---

## 💳 Mercado Pago — Checkout Transparente (Payment Brick)

O pagamento acontece **dentro da loja** (formulário de cartão/PIX na nossa página
`/pagamento/{pedido}`), sem redirecionar pro Mercado Pago. Já está configurado no
`.env` com as credenciais de teste.

```env
MP_ENABLED=true
MP_PUBLIC_KEY=TEST-xxxxxxxx        # usada no navegador (Brick) p/ tokenizar o cartão
MP_ACCESS_TOKEN=TEST-xxxxxxxx      # usada no backend p/ criar o pagamento (/v1/payments)
MP_BACK_URL_BASE=                  # base pública (ngrok) p/ o webhook; vazio = APP_URL
```

⚠️ **A Public Key e o Access Token precisam ser da MESMA aplicação** no painel MP —
senão o token do cartão gerado no navegador dá `Card Token not found` no backend.

**Testar:**
- Cartão: Mastercard `5031 4332 1540 6351`, validade futura, CVV `123`, titular **APRO**
  (aprova) ou **OTHE** (recusa). Mais em *MP Developers → Cartões de teste*.
- PIX: funciona no sandbox e gera QR (fica `pendente` até o webhook confirmar).

**Webhook (localhost):** o MP não alcança `localhost`. Para receber a confirmação
automática (essencial p/ PIX/boleto), rode um túnel (ngrok) e aponte
`MP_BACK_URL_BASE=https://seu-tunel.ngrok.io`. O cartão aprova na hora, sem depender
disso.

> Fluxo no código: `CheckoutController` cria o pedido (pendente) → `payment.show`
> renderiza o Brick → `payment.process` chama `MercadoPagoService::createPayment`
> (`/v1/payments`) → atualiza o pedido. O webhook re-consulta o pagamento no MP.

---

## 🔐 Google OAuth (login/cadastro)

1. Acesse **https://console.cloud.google.com/apis/credentials** e crie um projeto.
2. **Tela de consentimento OAuth**: tipo *Externo*, modo *Testing*, e adicione seu
   e-mail em **Usuários de teste**.
3. **Criar credenciais → ID do cliente OAuth → Aplicativo da Web**:
   - **Origens JavaScript autorizadas:** `http://localhost:8095`
   - **URIs de redirecionamento autorizados:** `http://localhost:8095/auth/google/callback`
4. Copie o **Client ID** e **Client Secret** para o `.env`:
   ```env
   GOOGLE_CLIENT_ID=xxxxxxxx.apps.googleusercontent.com
   GOOGLE_CLIENT_SECRET=xxxxxxxx
   GOOGLE_REDIRECT_URI="${APP_URL}/auth/google/callback"
   ```
5. O botão **"Continuar com Google"** aparece automaticamente no login e no cadastro
   quando `GOOGLE_CLIENT_ID` está preenchido.

> A URI de redirecionamento no Google **precisa** bater exatamente com `APP_URL`
> (`http://localhost:8095`). Se mudar a porta/domínio, atualize nos dois lugares.

---

## 🚚 Frete — Melhor Envio (agregador)

Usamos o **Melhor Envio**, que numa única cotação devolve **Correios (PAC/SEDEX),
Jadlog, Loggi, Azul Cargo** e outras — com **token self-service e sandbox grátis**.
Enquanto não estiver configurado, a loja usa uma **estimativa** (ViaCEP + peso) para
não travar. Origem/regras em `config/shipping.php` (`SHIPPING_ORIGIN_CEP`).

### Conexão via OAuth (aplicativo)

O Melhor Envio usa **OAuth2**: você cadastra um **aplicativo** (Client ID + Secret +
Redirect URI) e conecta pelo painel admin — o token é obtido e **renovado
automaticamente** (guardado na tabela `integration_tokens`).

1. No Melhor Envio (**sandbox** `sandbox.melhorenvio.com.br` ou produção
   `melhorenvio.com.br`) → **Configurações → Tokens → Novo aplicativo**. Anote
   **Client ID** e **Secret** e defina o **Redirect/Callback** =
   `https://SEU_DOMINIO/frete/callback` (permissão *calcular fretes*).
2. `.env`:
   ```env
   MELHORENVIO_ENABLED=true
   MELHORENVIO_CLIENT_ID=10280
   MELHORENVIO_CLIENT_SECRET=xxxxxxxx
   MELHORENVIO_REDIRECT_URI=https://SEU_DOMINIO/frete/callback
   MELHORENVIO_SANDBOX=true          # false se cadastrou o app na produção
   MELHORENVIO_USER_AGENT="Muda (seu-email@dominio.com)"   # obrigatório pelo ME
   ```
3. `docker compose exec app php artisan config:clear`.
4. Logue como **root** → aba **Frete** → **Conectar Melhor Envio** → autorize.
   Pronto: as cotações passam a usar as transportadoras reais.

**Observações importantes:**
- O `MELHORENVIO_REDIRECT_URI` **precisa ser idêntico** ao cadastrado no app do ME.
  Como está `https://meadadigital.com/frete/callback`, a conexão só completa **rodando
  em produção** (meadadigital.com). Para testar **localmente**, adicione também
  `http://localhost:8095/frete/callback` como redirect no app do ME e ajuste o `.env`.
- `MELHORENVIO_SANDBOX` deve corresponder a **onde o app foi criado** (sandbox × produção).
- No sandbox, habilite as **transportadoras** na conta (Configurações → Transportadoras).
- O `User-Agent` com e-mail é **obrigatório** pelo Melhor Envio.
