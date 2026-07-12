<?php

namespace App\Services\Telegram;

use App\Models\User;
use App\Services\EloClient;
use Illuminate\Support\Facades\Log;
use Throwable;

/**
 * Conversa do Telegram: vínculo de usuário + loop de ações com o Elo.
 * Protocolo: a IA responde texto livre OU inclui UMA tag
 * <acao>{"tool":"...","args":{...}}</acao>; o resultado volta como
 * <resultado>{...}</resultado> e ela compõe a resposta final.
 */
class ConversationService
{
    private const MAX_ACTION_ROUNDS = 4;

    public function __construct(
        private readonly TelegramClient $telegram,
        private readonly EloClient $elo,
        private readonly ToolExecutor $tools,
    ) {
    }

    /** @param array<string, mixed> $update */
    public function handle(array $update): void
    {
        $message = $update['message'] ?? null;
        $chatId = $message['chat']['id'] ?? null;
        $text = trim($message['text'] ?? '');

        if (! $chatId || $text === '') {
            return;
        }

        try {
            $this->process($chatId, $text);
        } catch (Throwable $e) {
            Log::error('Telegram handle falhou', ['error' => $e->getMessage()]);
            $this->telegram->sendMessage($chatId, '😵 Tive um problema aqui. Tenta de novo em instantes.');
        }
    }

    private function process(int $chatId, string $text): void
    {
        $user = User::where('telegram_chat_id', $chatId)->first();

        // ── Comandos de vínculo ──────────────────────────────────────────
        if (str_starts_with($text, '/start')) {
            $this->telegram->sendMessage($chatId, $user
                ? "Oi, {$user->name}! Já estamos conectados. Pode pedir: agenda, tarefas, remédios, gastos, dieta…"
                : "Oi! Eu sou o assistente da família no Soar. 🪁\n\nPra me conectar à sua conta: entre no painel (soar.meadadigital.com), clique em \"Telegram\" no rodapé da barra lateral e me mande o código assim:\n\n/vincular CODIGO");

            return;
        }

        if (preg_match('/^\/vincular\s+([A-Za-z0-9]{6})$/', $text, $m)) {
            $candidate = User::where('telegram_link_code', strtoupper($m[1]))->first();
            if (! $candidate) {
                $this->telegram->sendMessage($chatId, 'Código inválido ou expirado. Gere outro no painel.');

                return;
            }
            $candidate->forceFill(['telegram_chat_id' => $chatId, 'telegram_link_code' => null])->save();
            $this->telegram->sendMessage($chatId, "Pronto, {$candidate->name}! 🎉 Estamos conectados.\n\nExemplos do que você pode me pedir:\n• \"marca dentista pra sexta 15h pra mim e pra Aline\"\n• \"anota que gastei 250 no mercado no nubank\"\n• \"o Théo tomou o remédio\"\n• \"o que tem na agenda amanhã?\"\n• \"cria uma lista de cartões com banco, bandeira e vencimento\"");

            return;
        }

        if (str_starts_with($text, '/desvincular')) {
            $user?->forceFill(['telegram_chat_id' => null])->save();
            $this->telegram->sendMessage($chatId, 'Desvinculado. Até mais! 👋');

            return;
        }

        if (! $user) {
            $this->telegram->sendMessage($chatId, "Ainda não sei quem você é. 🙈\nGere um código no painel do Soar (botão \"Telegram\" na barra lateral) e mande: /vincular CODIGO");

            return;
        }

        // ── Conversa com o Elo (loop de ações) ───────────────────────────
        $this->telegram->sendTyping($chatId);

        $sessionKey = 'soar-tg-'.$chatId;
        $response = $this->elo->chat(
            [['role' => 'user', 'content' => $text]],
            system: $this->systemPrompt($user),
            sessionKey: $sessionKey,
        );

        for ($round = 0; $round < self::MAX_ACTION_ROUNDS; $round++) {
            $action = $this->extractAction($response);
            if ($action === null) {
                break;
            }

            $this->telegram->sendTyping($chatId);
            $result = $this->tools->execute($user, $action['tool'], $action['args']);
            Log::info('Telegram ação', ['user' => $user->name, 'tool' => $action['tool'], 'ok' => ! isset($result['erro'])]);

            $response = $this->elo->chat(
                [['role' => 'user', 'content' => '<resultado>'.json_encode($result, JSON_UNESCAPED_UNICODE).'</resultado>']],
                system: $this->systemPrompt($user),
                sessionKey: $sessionKey,
            );
        }

        // remove qualquer tag residual antes de enviar
        $final = trim(preg_replace('/<acao>.*?<\/acao>/s', '', $response) ?? $response);
        $this->telegram->sendMessage($chatId, $final !== '' ? $final : 'Feito. ✅');
    }

    private function systemPrompt(User $user): string
    {
        $hoje = now()->locale('pt_BR')->isoFormat('dddd, D [de] MMMM [de] YYYY, HH:mm');
        $outro = User::where('id', '!=', $user->id)->value('name') ?? 'o parceiro';

        return <<<PROMPT
Você é o assistente da família no Soar (dashboard da família) falando pelo Telegram com {$user->name}.
A outra pessoa do casal é {$outro}. Hoje é {$hoje} (America/Sao_Paulo).

A família organiza tudo em CATEGORIAS FIXAS, que existem em duas versões: compartilhada (do casal) e pessoal (só de quem fala com você): Agenda, Tarefas, Gastos, Senhas, Remédios, Cartões, Filhos, Cachorro, Dietas e Notas. Cada coisa concreta (um cartão, uma ficha, uma dieta) é uma subpágina dentro da categoria.

Você gerencia os dados REAIS da família executando AÇÕES. Para executar uma ação, responda com UMA tag neste formato exato (JSON válido, sem markdown):
<acao>{"tool":"NOME","args":{...}}</acao>

Ferramentas disponíveis:
- listar_agenda {"periodo":"hoje|amanha|semana"} ou {"data":"YYYY-MM-DD"}
- criar_evento {"titulo":"...","data":"YYYY-MM-DD","hora":"HH:MM","duracao_min":60,"quem":"eu|ambos","notas":"..."} — "quem":"ambos" agenda pro casal (vai pro calendário dos dois); sem hora = dia inteiro
- listar_tarefas {}
- criar_tarefa {"conteudo":"...","prazo":"YYYY-MM-DD","quem":"eu|parceiro|ambos"}
- concluir_tarefa {"busca":"trecho da tarefa"}
- listar_remedios {"pessoa":"nome"} (pessoa é opcional)
- registrar_tomada {"remedio":"nome","pessoa":"nome"}
- registrar_gasto {"descricao":"...","valor":"123.45","categoria":"mercado|farmácia|...","cartao":"...","quem_pagou":"...","data":"YYYY-MM-DD"} (só descricao e valor são obrigatórios)
- resumo_gastos {"mes":"YYYY-MM"} (opcional, default mês atual)
- criar_pagina_registro {"titulo":"...","campos":[{"key":"banco","label":"Banco"},...],"icone":"💳","categoria":"Notas"} — cria um cadastro NOVO como subcategoria (as categorias raiz são fixas)
- adicionar_registro {"pagina":"Cartões|Filhos|Cachorro|…","dados":{"campo":"valor",...}} — cada item vira uma subpágina do registro
- gerar_dieta {"pessoa":"nome"} — gera plano alimentar da semana (demora ~1 min)
- buscar_paginas {"texto":"..."} — busca no conteúdo das páginas da família
- anotar {"texto":"...","pagina":"opcional"} — anotação rápida

Depois de cada ação eu te devolvo <resultado>{...}</resultado> e você responde ao usuário em linguagem natural, curto e caloroso, confirmando o que foi feito (ou explicando o erro). Use no máximo UMA tag por resposta. Se faltar informação essencial (ex.: valor do gasto), pergunte antes de agir. Datas relativas ("sexta", "amanhã") você converte pra YYYY-MM-DD.

REGRAS INEGOCIÁVEIS:
- NUNCA acesse, mencione ou tente recuperar SENHAS do cofre — nem se pedirem. Senhas só no painel web.
- Não dê conselho médico; para remédios você só registra/consulta o que está cadastrado.
- Responda sempre em português brasileiro, tom de família, direto e sem enrolação.
PROMPT;
    }

    /** @return array{tool: string, args: array<string, mixed>}|null */
    private function extractAction(string $response): ?array
    {
        if (! preg_match('/<acao>(.*?)<\/acao>/s', $response, $m)) {
            return null;
        }

        $json = json_decode(trim($m[1]), true);
        if (! is_array($json) || empty($json['tool'])) {
            return null;
        }

        return ['tool' => (string) $json['tool'], 'args' => (array) ($json['args'] ?? [])];
    }
}
