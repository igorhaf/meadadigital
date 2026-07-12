<?php

namespace App\Services\Telegram;

use App\Models\CalendarEvent;
use App\Models\Medication;
use App\Models\Page;
use App\Models\TaskItem;
use App\Models\User;
use App\Services\DietGenerator;
use Carbon\Carbon;
use Illuminate\Support\Str;

/**
 * Executa as ações pedidas pela IA (tags <acao>) em nome de um usuário.
 * Toda leitura/escrita respeita o escopo: compartilhado ou pessoal do próprio usuário.
 * O VAULT fica DELIBERADAMENTE de fora — senha nunca sai pelo Telegram.
 */
class ToolExecutor
{
    public function __construct(private readonly DietGenerator $dietGenerator)
    {
    }

    /** @param array<string, mixed> $args */
    public function execute(User $user, string $tool, array $args): array
    {
        return match ($tool) {
            'listar_agenda' => $this->listarAgenda($user, $args),
            'criar_evento' => $this->criarEvento($user, $args),
            'listar_tarefas' => $this->listarTarefas($user),
            'criar_tarefa' => $this->criarTarefa($user, $args),
            'concluir_tarefa' => $this->concluirTarefa($user, $args),
            'listar_remedios' => $this->listarRemedios($user, $args),
            'registrar_tomada' => $this->registrarTomada($user, $args),
            'registrar_gasto' => $this->registrarGasto($user, $args),
            'resumo_gastos' => $this->resumoGastos($user, $args),
            'criar_pagina_registro' => $this->criarPaginaRegistro($user, $args),
            'adicionar_registro' => $this->adicionarRegistro($user, $args),
            'gerar_dieta' => $this->gerarDieta($user, $args),
            'buscar_paginas' => $this->buscarPaginas($user, $args),
            'anotar' => $this->anotar($user, $args),
            default => ['erro' => "Ação desconhecida: $tool"],
        };
    }

    // ── Agenda ────────────────────────────────────────────────────────────

    private function listarAgenda(User $user, array $args): array
    {
        [$from, $to] = $this->periodo($args);

        $events = CalendarEvent::whereHas('page', fn ($q) => $q->accessibleBy($user)->where('kind', 'calendar'))
            ->whereBetween('starts_at', [$from, $to])
            ->orderBy('starts_at')
            ->with('page:id,title,scope')
            ->get();

        return [
            'periodo' => [$from->toDateString(), $to->toDateString()],
            'eventos' => $events->map(fn ($e) => [
                'titulo' => $e->title,
                'inicio' => $e->starts_at->format('d/m/Y H:i'),
                'dia_inteiro' => $e->all_day,
                'agenda' => $e->page->scope === 'shared' ? 'casal' : 'pessoal',
                'notas' => $e->notes,
            ])->all(),
        ];
    }

    private function criarEvento(User $user, array $args): array
    {
        $quem = $args['quem'] ?? 'ambos';
        $page = $quem === 'eu'
            ? $this->pageOfKind($user, 'calendar', Page::SCOPE_PERSONAL, 'Minha Agenda', '📅')
            : $this->pageOfKind($user, 'calendar', Page::SCOPE_SHARED, 'Agenda da Família', '📅');

        $date = $args['data'] ?? now()->toDateString();
        $time = $args['hora'] ?? null;
        $startsAt = Carbon::parse($time ? "$date $time" : $date, config('app.timezone'));
        $endsAt = $time ? $startsAt->copy()->addMinutes((int) ($args['duracao_min'] ?? 60)) : null;

        $event = $page->events()->create([
            'title' => $args['titulo'] ?? 'Evento',
            'starts_at' => $startsAt,
            'ends_at' => $endsAt,
            'all_day' => $time === null,
            'notes' => $args['notas'] ?? null,
        ]);

        return [
            'ok' => true,
            'evento' => $event->title,
            'quando' => $startsAt->format('d/m/Y').($time ? ' às '.$startsAt->format('H:i') : ' (dia inteiro)'),
            'agenda' => $page->scope === 'shared' ? 'do casal (Igor e Aline)' : 'pessoal',
        ];
    }

    // ── Tarefas ───────────────────────────────────────────────────────────

    private function listarTarefas(User $user): array
    {
        $tasks = TaskItem::whereHas('page', fn ($q) => $q->accessibleBy($user)->where('kind', 'tasks'))
            ->where('done', false)
            ->orderBy('due_date')
            ->with(['page:id,title,scope', 'assignee:id,name'])
            ->limit(40)
            ->get();

        return [
            'pendentes' => $tasks->map(fn ($t) => [
                'tarefa' => $t->content,
                'lista' => $t->page->title,
                'responsavel' => $t->assignee?->name ?? 'ambos',
                'prazo' => $t->due_date?->format('d/m/Y'),
            ])->all(),
        ];
    }

    private function criarTarefa(User $user, array $args): array
    {
        $quem = $args['quem'] ?? 'eu';
        $page = $quem === 'eu'
            ? $this->pageOfKind($user, 'tasks', Page::SCOPE_PERSONAL, 'Minhas Tarefas', '✅')
            : $this->pageOfKind($user, 'tasks', Page::SCOPE_SHARED, 'Tarefas da Casa', '✅');

        $assignedId = match ($quem) {
            'eu' => $user->id,
            'parceiro' => User::where('id', '!=', $user->id)->whereNotNull('telegram_chat_id')->value('id')
                ?? User::where('id', '!=', $user->id)->value('id'),
            default => null,
        };

        $task = $page->taskItems()->create([
            'content' => $args['conteudo'] ?? 'Tarefa',
            'due_date' => $args['prazo'] ?? null,
            'assigned_user_id' => $assignedId,
            'position' => ($page->taskItems()->max('position') ?? -1) + 1,
        ]);

        return ['ok' => true, 'tarefa' => $task->content, 'lista' => $page->title, 'prazo' => $task->due_date?->format('d/m/Y')];
    }

    private function concluirTarefa(User $user, array $args): array
    {
        $busca = $args['busca'] ?? '';
        $matches = TaskItem::whereHas('page', fn ($q) => $q->accessibleBy($user)->where('kind', 'tasks'))
            ->where('done', false)
            ->where('content', 'ilike', '%'.$busca.'%')
            ->get();

        if ($matches->isEmpty()) {
            return ['erro' => "Nenhuma tarefa pendente contém '$busca'."];
        }
        if ($matches->count() > 1) {
            return [
                'erro' => 'Mais de uma tarefa encontrada — peça pro usuário especificar.',
                'opcoes' => $matches->pluck('content')->all(),
            ];
        }

        $matches->first()->update(['done' => true]);

        return ['ok' => true, 'concluida' => $matches->first()->content];
    }

    // ── Remédios ──────────────────────────────────────────────────────────

    private function listarRemedios(User $user, array $args): array
    {
        $query = Medication::whereHas('page', fn ($q) => $q->accessibleBy($user)->where('kind', 'meds'))
            ->where('active', true);
        if (! empty($args['pessoa'])) {
            $query->where('person', 'ilike', '%'.$args['pessoa'].'%');
        }

        return [
            'remedios' => $query->get()->map(fn ($m) => [
                'pessoa' => $m->person,
                'nome' => $m->name,
                'dose' => $m->dose,
                'horarios' => $m->schedule_times,
                'controlado' => $m->controlled,
                'receita_ate' => $m->prescription_until?->format('d/m/Y'),
                'estoque' => $m->stock,
            ])->all(),
        ];
    }

    private function registrarTomada(User $user, array $args): array
    {
        $query = Medication::whereHas('page', fn ($q) => $q->accessibleBy($user)->where('kind', 'meds'))
            ->where('active', true)
            ->where('name', 'ilike', '%'.($args['remedio'] ?? '').'%');
        if (! empty($args['pessoa'])) {
            $query->where('person', 'ilike', '%'.$args['pessoa'].'%');
        }
        $matches = $query->get();

        if ($matches->isEmpty()) {
            return ['erro' => 'Remédio não encontrado.'];
        }
        if ($matches->count() > 1) {
            return [
                'erro' => 'Mais de um remédio encontrado — peça pra especificar a pessoa.',
                'opcoes' => $matches->map(fn ($m) => $m->person.' — '.$m->name)->all(),
            ];
        }

        $med = $matches->first();
        $med->logs()->create(['taken_at' => now(), 'taken_by' => $user->id]);
        if ($med->stock !== null && $med->stock > 0) {
            $med->decrement('stock');
        }

        return ['ok' => true, 'registrado' => $med->person.' tomou '.$med->name.' às '.now()->format('H:i'), 'estoque_restante' => $med->fresh()->stock];
    }

    // ── Gastos ────────────────────────────────────────────────────────────

    private function registrarGasto(User $user, array $args): array
    {
        $page = $this->pageOfKind($user, 'gastos', Page::SCOPE_SHARED, 'Gastos da Família', '💸');

        $valor = $args['valor'] ?? 0;
        $amountCents = (int) round(((float) str_replace(',', '.', (string) $valor)) * 100);
        if ($amountCents <= 0) {
            return ['erro' => 'Valor do gasto inválido.'];
        }

        $entry = $page->expenseEntries()->create([
            'date' => $args['data'] ?? now()->toDateString(),
            'description' => $args['descricao'] ?? 'Gasto',
            'category' => $args['categoria'] ?? null,
            'amount_cents' => $amountCents,
            'paid_by' => $args['quem_pagou'] ?? $user->name,
            'card' => $args['cartao'] ?? null,
        ]);

        return [
            'ok' => true,
            'gasto' => $entry->description,
            'valor' => 'R$ '.number_format($amountCents / 100, 2, ',', '.'),
        ];
    }

    private function resumoGastos(User $user, array $args): array
    {
        $month = $args['mes'] ?? now()->format('Y-m');
        $pages = Page::accessibleBy($user)->where('kind', 'gastos')->pluck('id');

        $entries = \App\Models\ExpenseEntry::whereIn('page_id', $pages)
            ->whereBetween('date', ["$month-01", date('Y-m-t', strtotime("$month-01"))])
            ->get();

        return [
            'mes' => $month,
            'total' => 'R$ '.number_format($entries->sum('amount_cents') / 100, 2, ',', '.'),
            'quantidade' => $entries->count(),
            'por_categoria' => $entries->groupBy(fn ($e) => $e->category ?? 'Sem categoria')
                ->map(fn ($g) => 'R$ '.number_format($g->sum('amount_cents') / 100, 2, ',', '.'))->all(),
            'ultimos' => $entries->sortByDesc('date')->take(8)->map(fn ($e) => $e->date->format('d/m').' — '.$e->description.' — R$ '.number_format($e->amount_cents / 100, 2, ',', '.'))->values()->all(),
        ];
    }

    // ── Registros dinâmicos (cartões etc.) ───────────────────────────────

    private function criarPaginaRegistro(User $user, array $args): array
    {
        $campos = collect($args['campos'] ?? [])->map(fn ($c) => [
            'key' => Str::slug(is_array($c) ? ($c['key'] ?? $c['label'] ?? 'campo') : $c, '_'),
            'label' => is_array($c) ? ($c['label'] ?? $c['key'] ?? 'Campo') : $c,
            'type' => is_array($c) ? ($c['type'] ?? 'text') : 'text',
        ])->values()->all();

        if (empty($campos)) {
            return ['erro' => 'Informe os campos do registro.'];
        }

        $shared = $args['compartilhada'] ?? true;
        $scope = $shared ? Page::SCOPE_SHARED : Page::SCOPE_PERSONAL;

        // categorias raiz são fixas — o novo registro nasce como SUBcategoria
        $parent = null;
        if (! empty($args['categoria'])) {
            $parent = Page::whereNull('parent_id')->where('is_system', true)
                ->where('scope', $scope)
                ->when(! $shared, fn ($q) => $q->where('owner_id', $user->id))
                ->where('title', 'ilike', '%'.$args['categoria'].'%')
                ->first();
        }
        $parent ??= $this->pageOfKind($user, 'note', $scope, 'Notas', '🗒️');

        $page = Page::create([
            'scope' => $scope,
            'owner_id' => $shared ? null : $user->id,
            'kind' => 'registro',
            'parent_id' => $parent->id,
            'title' => $args['titulo'] ?? 'Registro',
            'icon' => $args['icone'] ?? '📋',
            'meta' => ['template' => $campos],
            'position' => (Page::where('parent_id', $parent->id)->max('position') ?? -1) + 1,
        ]);

        return ['ok' => true, 'pagina' => $page->title, 'dentro_de' => $parent->title, 'campos' => collect($campos)->pluck('label')->all()];
    }

    private function adicionarRegistro(User $user, array $args): array
    {
        $page = Page::accessibleBy($user)
            ->where('kind', 'registro')
            ->where('title', 'ilike', '%'.($args['pagina'] ?? '').'%')
            ->first();

        if (! $page) {
            return ['erro' => 'Página de registro não encontrada: '.($args['pagina'] ?? '')];
        }

        $template = collect($page->meta['template'] ?? []);
        $dados = (array) ($args['dados'] ?? []);

        // normaliza chaves do LLM pros keys do template (por key OU label)
        $normalized = [];
        foreach ($dados as $k => $v) {
            $field = $template->first(fn ($f) => $f['key'] === Str::slug($k, '_')
                || mb_strtolower($f['label']) === mb_strtolower((string) $k));
            $normalized[$field['key'] ?? Str::slug($k, '_')] = $v;
        }

        $title = null;
        foreach ($template as $field) {
            if (! empty($normalized[$field['key']])) {
                $title = (string) $normalized[$field['key']];
                break;
            }
        }

        $item = Page::create([
            'scope' => $page->scope,
            'owner_id' => $page->owner_id,
            'kind' => 'registro_item',
            'parent_id' => $page->id,
            'title' => $title ?? 'Item',
            'meta' => ['data' => $normalized],
            'position' => (Page::where('parent_id', $page->id)->max('position') ?? -1) + 1,
        ]);

        return ['ok' => true, 'pagina' => $page->title, 'item' => $item->title, 'registrado' => $normalized];
    }

    // ── Dieta / busca / notas ────────────────────────────────────────────

    private function gerarDieta(User $user, array $args): array
    {
        $page = Page::accessibleBy($user)
            ->where('kind', 'diet')
            ->when(! empty($args['pessoa']), fn ($q) => $q->where(fn ($q2) => $q2
                ->where('title', 'ilike', '%'.$args['pessoa'].'%')
                ->orWhere('meta->person', 'ilike', '%'.$args['pessoa'].'%')))
            ->first();

        if (! $page) {
            return ['erro' => 'Página de dieta não encontrada pra '.($args['pessoa'] ?? '?').'. Crie no painel primeiro.'];
        }

        // O bot roda na mesma máquina do Elo → gera sincronamente.
        $plan = $this->dietGenerator->generate($page);

        return ['ok' => true, 'pessoa' => $page->meta['person'] ?? $page->title, 'dieta' => Str::limit($plan, 900)];
    }

    private function buscarPaginas(User $user, array $args): array
    {
        $texto = $args['texto'] ?? '';
        $pages = Page::accessibleBy($user)
            ->where(fn ($q) => $q->where('title', 'ilike', "%$texto%")->orWhere('content', 'ilike', "%$texto%"))
            ->limit(8)
            ->get();

        return [
            'resultados' => $pages->map(fn ($p) => [
                'pagina' => $p->title,
                'tipo' => $p->kind,
                'trecho' => $p->content ? Str::limit($p->content, 300) : null,
            ])->all(),
        ];
    }

    private function anotar(User $user, array $args): array
    {
        $texto = $args['texto'] ?? '';
        if (! empty($args['pagina'])) {
            $page = Page::accessibleBy($user)
                ->where('kind', 'note')
                ->where('title', 'ilike', '%'.$args['pagina'].'%')
                ->first();
            if (! $page) {
                return ['erro' => 'Página não encontrada: '.$args['pagina']];
            }
        } else {
            $page = Page::whereNull('parent_id')->where('is_system', true)->where('title', 'Notas')
                ->personalOf($user->id)->firstOrFail();
        }

        $stamp = now()->format('d/m/Y H:i');
        $page->update(['content' => trim(($page->content ?? '')."\n\n[$stamp] $texto")]);

        return ['ok' => true, 'pagina' => $page->title];
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    /** Categoria fixa (raiz is_system) do tipo/escopo — criada pela migração. */
    private function pageOfKind(User $user, string $kind, string $scope, string $defaultTitle, string $icon): Page
    {
        $query = Page::whereNull('parent_id')->where('is_system', true)
            ->where('kind', $kind)->where('scope', $scope)->orderBy('position');
        if ($scope === Page::SCOPE_PERSONAL) {
            $query->where('owner_id', $user->id);
        }

        $page = $query->first();
        if (! $page) {
            $page = Page::create([
                'scope' => $scope,
                'owner_id' => $scope === Page::SCOPE_PERSONAL ? $user->id : null,
                'kind' => $kind,
                'title' => $defaultTitle,
                'icon' => $icon,
                'position' => 99,
            ]);
            $page->forceFill(['is_system' => true])->save();
        }

        return $page;
    }

    /** @return array{0: Carbon, 1: Carbon} */
    private function periodo(array $args): array
    {
        $tz = config('app.timezone');
        if (! empty($args['data'])) {
            $d = Carbon::parse($args['data'], $tz);

            return [$d->copy()->startOfDay(), $d->copy()->endOfDay()];
        }

        return match ($args['periodo'] ?? 'hoje') {
            'amanha' => [now($tz)->addDay()->startOfDay(), now($tz)->addDay()->endOfDay()],
            'semana' => [now($tz)->startOfDay(), now($tz)->addDays(7)->endOfDay()],
            default => [now($tz)->startOfDay(), now($tz)->endOfDay()],
        };
    }
}
