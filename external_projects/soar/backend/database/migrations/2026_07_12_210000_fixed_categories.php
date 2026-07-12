<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Facades\Schema;

/**
 * Categorias FIXAS espelhadas em shared e personal (is_system, indeletáveis).
 * Nada é criado na raiz — tudo vira subcategoria. Itens de registro deixam de
 * ser linhas (registro_entries) e viram PÁGINAS FILHAS (kind registro_item,
 * dados em meta->data).
 */
return new class extends Migration
{
    private const TEMPLATES = [
        'Cartões' => [
            ['key' => 'banco', 'label' => 'Banco', 'type' => 'text'],
            ['key' => 'bandeira', 'label' => 'Bandeira', 'type' => 'text'],
            ['key' => 'final', 'label' => 'Final (4 dígitos)', 'type' => 'text'],
            ['key' => 'vencimento_fatura', 'label' => 'Vencimento da fatura', 'type' => 'text'],
            ['key' => 'limite', 'label' => 'Limite', 'type' => 'text'],
        ],
        'Filhos' => [
            ['key' => 'nome', 'label' => 'Nome', 'type' => 'text'],
            ['key' => 'nascimento', 'label' => 'Nascimento', 'type' => 'date'],
            ['key' => 'escola', 'label' => 'Escola', 'type' => 'text'],
            ['key' => 'tamanho_roupa', 'label' => 'Tamanho de roupa', 'type' => 'text'],
            ['key' => 'observacoes', 'label' => 'Observações', 'type' => 'text'],
        ],
        'Cachorro' => [
            ['key' => 'item', 'label' => 'Item (vacina/vermífugo/banho)', 'type' => 'text'],
            ['key' => 'data', 'label' => 'Data', 'type' => 'date'],
            ['key' => 'proxima', 'label' => 'Próxima dose', 'type' => 'date'],
            ['key' => 'observacoes', 'label' => 'Observações', 'type' => 'text'],
        ],
    ];

    public function up(): void
    {
        Schema::table('pages', function (Blueprint $table) {
            $table->boolean('is_system')->default(false);
        });

        DB::transaction(function () {
            $owners = [null, ...DB::table('users')->orderBy('id')->pluck('id')->all()];
            foreach ($owners as $ownerId) {
                $this->ensureCategories($ownerId);
            }
            $this->convertRegistroEntries();
            $this->adoptStrayRoots();
        });

        Schema::dropIfExists('registro_entries');
    }

    public function down(): void
    {
        Schema::table('pages', function (Blueprint $table) {
            $table->dropColumn('is_system');
        });
        // registro_entries não é recriada — conversão de dados é one-way.
    }

    /** Garante as 10 categorias fixas de um escopo (null = shared). */
    private function ensureCategories(?int $ownerId): void
    {
        $defs = [
            // [kind, título final, ícone, títulos antigos aceitos, template]
            ['calendar', 'Agenda', '📅', ['Agenda', 'Agenda da Família', 'Minha Agenda'], null],
            ['tasks', 'Tarefas', '✅', ['Tarefas', 'Tarefas da Casa', 'Minhas Tarefas'], null],
            ['gastos', 'Gastos', '💸', ['Gastos', 'Gastos da Família'], null],
            ['vault', 'Senhas', '🔐', ['Senhas', 'Cofre da Família'], null],
            ['meds', 'Remédios', '💊', ['Remédios', 'Remédios da Família'], null],
            ['registro', 'Cartões', '💳', ['Cartões'], self::TEMPLATES['Cartões']],
            ['registro', 'Filhos', '👧', ['Filhos', 'Fichas dos Filhos'], self::TEMPLATES['Filhos']],
            ['registro', 'Cachorro', '🐶', ['Cachorro'], self::TEMPLATES['Cachorro']],
            ['note', 'Dietas', '🥗', ['Dietas'], null],
            ['note', 'Notas', '🗒️', ['Notas', 'Notas Rápidas'], null],
        ];

        $position = 0;
        foreach ($defs as [$kind, $title, $icon, $matches, $template]) {
            $query = DB::table('pages')
                ->whereNull('parent_id')
                ->where('kind', $kind)
                ->whereIn('title', $matches)
                ->where('scope', $ownerId === null ? 'shared' : 'personal');
            $ownerId === null ? $query->whereNull('owner_id') : $query->where('owner_id', $ownerId);
            $existing = $query->orderBy('id')->first();

            if ($existing) {
                $meta = json_decode($existing->meta ?? 'null', true) ?? [];
                if ($template && empty($meta['template'])) {
                    $meta['template'] = $template;
                }
                DB::table('pages')->where('id', $existing->id)->update([
                    'title' => $title,
                    'icon' => $icon,
                    'is_system' => true,
                    'position' => $position,
                    'meta' => $meta ? json_encode($meta) : null,
                ]);
            } else {
                DB::table('pages')->insert([
                    'parent_id' => null,
                    'owner_id' => $ownerId,
                    'scope' => $ownerId === null ? 'shared' : 'personal',
                    'kind' => $kind,
                    'title' => $title,
                    'icon' => $icon,
                    'meta' => $template ? json_encode(['template' => $template]) : null,
                    'is_system' => true,
                    'position' => $position,
                    'created_at' => now(),
                    'updated_at' => now(),
                ]);
            }
            $position++;
        }
    }

    /** Linhas de registro viram páginas filhas (kind registro_item). */
    private function convertRegistroEntries(): void
    {
        if (! Schema::hasTable('registro_entries')) {
            return;
        }

        foreach (DB::table('registro_entries')->orderBy('page_id')->orderBy('position')->get() as $entry) {
            $page = DB::table('pages')->find($entry->page_id);
            if (! $page) {
                continue;
            }
            $data = json_decode($entry->data, true) ?? [];
            $template = json_decode($page->meta ?? 'null', true)['template'] ?? [];
            $title = null;
            foreach ($template as $field) {
                if (! empty($data[$field['key']])) {
                    $title = (string) $data[$field['key']];
                    break;
                }
            }

            DB::table('pages')->insert([
                'parent_id' => $page->id,
                'owner_id' => $page->owner_id,
                'scope' => $page->scope,
                'kind' => 'registro_item',
                'title' => $title ?? 'Item '.$entry->id,
                'icon' => null,
                'meta' => json_encode(['data' => $data]),
                'is_system' => false,
                'position' => $entry->position,
                'created_at' => $entry->created_at ?? now(),
                'updated_at' => $entry->updated_at ?? now(),
            ]);
        }
    }

    /** Raízes que não são categoria (ex.: Início da Família) viram filhas de Notas. */
    private function adoptStrayRoots(): void
    {
        foreach (DB::table('pages')->whereNull('parent_id')->where('is_system', false)->get() as $stray) {
            $notas = DB::table('pages')
                ->whereNull('parent_id')
                ->where('is_system', true)
                ->where('title', 'Notas')
                ->where('scope', $stray->scope)
                ->when($stray->owner_id === null, fn ($q) => $q->whereNull('owner_id'), fn ($q) => $q->where('owner_id', $stray->owner_id))
                ->first();
            if ($notas) {
                DB::table('pages')->where('id', $stray->id)->update(['parent_id' => $notas->id]);
            }
        }
    }
};
