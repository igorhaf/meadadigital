/*
 * Meada WhatsApp — widget de chat web embutível (camada 5.25 #73).
 *
 * Vanilla JS, ZERO dependências, sem build. Embute num site arbitrário do tenant assim:
 *
 *   <script src="https://app.meada/widget.js"
 *           data-company="slug-da-empresa"
 *           data-api="https://api.meada"></script>
 *
 * Cria um botão flutuante + painel de chat, gera um sessionId persistido em localStorage e
 * faz POST para `${api}/api/chat/${slug}` (endpoint público, sem auth). Estilos inline (não
 * depende do CSS do site hospedeiro). Mantido enxuto (~150 linhas).
 */
(function () {
  'use strict';

  // O <script> corrente: lê os data-attributes (company slug + api base).
  var script = document.currentScript;
  if (!script) {
    return; // sem o script de origem não há como resolver slug/api.
  }
  var SLUG = script.getAttribute('data-company');
  var API = (script.getAttribute('data-api') || '').replace(/\/+$/, ''); // tira barra final
  if (!SLUG || !API) {
    console.error('[meada-widget] faltam data-company e/ou data-api no <script>.');
    return;
  }

  // sessionId estável por navegador (localStorage), para o backend correlacionar a conversa.
  var SESSION_KEY = 'meada_webchat_session';
  var sessionId = '';
  try {
    sessionId = localStorage.getItem(SESSION_KEY) || '';
    if (!sessionId) {
      sessionId = 's-' + Date.now() + '-' + Math.random().toString(36).slice(2, 10);
      localStorage.setItem(SESSION_KEY, sessionId);
    }
  } catch (e) {
    // localStorage bloqueado (modo privado/cookies off): sessão efêmera só nesta visita.
    sessionId = 's-' + Date.now() + '-' + Math.random().toString(36).slice(2, 10);
  }

  var PRIMARY = '#16a34a'; // verde (WhatsApp-ish); o tenant pode trocar via fork se quiser.

  // ---- elementos --------------------------------------------------------------
  var button = document.createElement('button');
  button.setAttribute('aria-label', 'Abrir chat');
  button.textContent = '💬';
  button.style.cssText =
    'position:fixed;bottom:20px;right:20px;width:56px;height:56px;border-radius:50%;border:none;' +
    'background:' + PRIMARY + ';color:#fff;font-size:24px;cursor:pointer;z-index:2147483646;' +
    'box-shadow:0 4px 12px rgba(0,0,0,.25);';

  var panel = document.createElement('div');
  panel.style.cssText =
    'position:fixed;bottom:88px;right:20px;width:340px;max-width:calc(100vw - 40px);height:460px;' +
    'max-height:calc(100vh - 120px);display:none;flex-direction:column;background:#fff;color:#111;' +
    'border-radius:12px;overflow:hidden;z-index:2147483647;box-shadow:0 8px 30px rgba(0,0,0,.3);' +
    'font-family:system-ui,-apple-system,Segoe UI,Roboto,sans-serif;';

  var header = document.createElement('div');
  header.textContent = 'Atendimento';
  header.style.cssText =
    'background:' + PRIMARY + ';color:#fff;padding:12px 16px;font-weight:600;font-size:15px;';

  var log = document.createElement('div');
  log.style.cssText =
    'flex:1;overflow-y:auto;padding:12px;display:flex;flex-direction:column;gap:8px;font-size:14px;' +
    'background:#f8f8f8;';

  var form = document.createElement('form');
  form.style.cssText = 'display:flex;gap:6px;padding:10px;border-top:1px solid #eee;background:#fff;';

  var input = document.createElement('input');
  input.type = 'text';
  input.placeholder = 'Escreva sua mensagem…';
  input.style.cssText =
    'flex:1;border:1px solid #ddd;border-radius:8px;padding:8px 10px;font-size:14px;outline:none;';

  var send = document.createElement('button');
  send.type = 'submit';
  send.textContent = 'Enviar';
  send.style.cssText =
    'border:none;background:' + PRIMARY + ';color:#fff;border-radius:8px;padding:8px 14px;' +
    'font-size:14px;cursor:pointer;';

  form.appendChild(input);
  form.appendChild(send);
  panel.appendChild(header);
  panel.appendChild(log);
  panel.appendChild(form);
  document.body.appendChild(button);
  document.body.appendChild(panel);

  // ---- helpers ----------------------------------------------------------------
  function addBubble(text, mine) {
    var b = document.createElement('div');
    b.textContent = text;
    b.style.cssText =
      'max-width:80%;padding:8px 12px;border-radius:12px;line-height:1.35;word-wrap:break-word;' +
      (mine
        ? 'align-self:flex-end;background:' + PRIMARY + ';color:#fff;'
        : 'align-self:flex-start;background:#fff;border:1px solid #eee;color:#111;');
    log.appendChild(b);
    log.scrollTop = log.scrollHeight;
    return b;
  }

  var open = false;
  button.addEventListener('click', function () {
    open = !open;
    panel.style.display = open ? 'flex' : 'none';
    button.textContent = open ? '✕' : '💬';
    if (open && log.childElementCount === 0) {
      addBubble('Olá! Como posso ajudar?', false);
    }
    if (open) {
      input.focus();
    }
  });

  form.addEventListener('submit', function (ev) {
    ev.preventDefault();
    var text = input.value.trim();
    if (!text) {
      return;
    }
    addBubble(text, true);
    input.value = '';
    send.disabled = true;
    var typing = addBubble('…', false);

    fetch(API + '/api/chat/' + encodeURIComponent(SLUG), {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ sessionId: sessionId, message: text }),
    })
      .then(function (r) {
        return r.ok ? r.json() : Promise.reject(new Error('HTTP ' + r.status));
      })
      .then(function (data) {
        typing.textContent = (data && data.reply) || 'Desculpe, não consegui responder agora.';
      })
      .catch(function () {
        typing.textContent = 'Desculpe, houve um erro de conexão. Tente novamente.';
      })
      .finally(function () {
        send.disabled = false;
        log.scrollTop = log.scrollHeight;
        input.focus();
      });
  });
})();
