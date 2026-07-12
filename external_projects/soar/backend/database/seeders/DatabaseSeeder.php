<?php

namespace Database\Seeders;

use App\Models\Page;
use App\Models\User;
use Illuminate\Database\Seeder;
use Illuminate\Support\Facades\Hash;

class DatabaseSeeder extends Seeder
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

    public function run(): void
    {
        if (User::query()->exists()) {
            return; // idempotente: não duplica no re-boot do compose
        }

        // Senha vem do ambiente (.env, gitignored) — nunca hardcoded no repo.
        $password = Hash::make(env('SEED_USER_PASSWORD', 'password'));

        $aline = User::create([
            'name' => 'Aline',
            'email' => 'alinecarla.rs@gmail.com',
            'password' => $password,
            'google_calendar_id' => 'alinecarla.rs@gmail.com',
        ]);

        $igor = User::create([
            'name' => 'Igor',
            'email' => 'igorhaf@gmail.com',
            'password' => $password,
            'google_calendar_id' => 'igorhaf@gmail.com',
        ]);

        // Categorias FIXAS espelhadas: shared + pessoal de cada usuário.
        $shared = $this->categories(null);
        $this->categories($igor->id);
        $this->categories($aline->id);

        // Conteúdo de exemplo no espaço compartilhado
        $shared['Tarefas']->taskItems()->createMany([
            ['content' => 'Testar o bot do Telegram (/vincular)', 'position' => 0],
            ['content' => 'Cadastrar os remédios de todo mundo', 'position' => 1],
            ['content' => 'Preencher as fichas dos filhos', 'position' => 2],
        ]);

        $shared['Senhas']->vaultEntries()->create([
            'title' => 'Exemplo — Wi-Fi de casa',
            'username' => 'rede-familia',
            'secret' => 'troque-esta-senha',
            'notes' => 'Entrada de exemplo — edite ou exclua.',
            'position' => 0,
        ]);

        foreach (['Filho 1', 'Filho 2', 'Filho 3'] as $i => $nome) {
            $this->item($shared['Filhos'], $nome, ['nome' => $nome], $i);
        }

        $shared['Remédios']->medications()->create([
            'person' => 'Exemplo',
            'name' => 'Vitamina D (exemplo — edite)',
            'dose' => '1 cápsula',
            'schedule_times' => ['08:00'],
            'controlled' => false,
        ]);

        $this->page($shared['Dietas'], 'diet', '🥗', 'Dieta do Igor', meta: ['person' => 'Igor']);
        $this->page($shared['Dietas'], 'diet', '🥗', 'Dieta da Aline', meta: ['person' => 'Aline']);

        $this->page($shared['Notas'], 'note', '🏠', 'Início da Família',
            "Bem-vindos ao Soar da família! 🪁\n\nAs categorias da barra lateral são fixas e existem em versão compartilhada e pessoal. Dentro de cada uma, criem subpáginas à vontade — um cartão, uma ficha, uma lista.\n\nFale com o bot @RosendoFrancaBot no Telegram pra registrar e consultar tudo por lá.");

        $this->command?->info('Família semeada: categorias fixas espelhadas + exemplos.');
    }

    /** @return array<string, Page> categorias por título */
    private function categories(?int $ownerId): array
    {
        $defs = [
            ['calendar', 'Agenda', '📅', null],
            ['tasks', 'Tarefas', '✅', null],
            ['gastos', 'Gastos', '💸', null],
            ['vault', 'Senhas', '🔐', null],
            ['meds', 'Remédios', '💊', null],
            ['registro', 'Cartões', '💳', self::TEMPLATES['Cartões']],
            ['registro', 'Filhos', '👧', self::TEMPLATES['Filhos']],
            ['registro', 'Cachorro', '🐶', self::TEMPLATES['Cachorro']],
            ['note', 'Dietas', '🥗', null],
            ['note', 'Notas', '🗒️', null],
        ];

        $pages = [];
        foreach ($defs as $position => [$kind, $title, $icon, $template]) {
            $page = Page::create([
                'parent_id' => null,
                'owner_id' => $ownerId,
                'scope' => $ownerId ? Page::SCOPE_PERSONAL : Page::SCOPE_SHARED,
                'kind' => $kind,
                'title' => $title,
                'icon' => $icon,
                'meta' => $template ? ['template' => $template] : null,
                'position' => $position,
            ]);
            $page->forceFill(['is_system' => true])->save();
            $pages[$title] = $page;
        }

        return $pages;
    }

    private function item(Page $parent, string $title, array $data, int $position): Page
    {
        return $this->page($parent, 'registro_item', null, $title, meta: ['data' => $data], position: $position);
    }

    private function page(Page $parent, string $kind, ?string $icon, string $title, ?string $content = null, ?array $meta = null, ?int $position = null): Page
    {
        return Page::create([
            'parent_id' => $parent->id,
            'owner_id' => $parent->owner_id,
            'scope' => $parent->scope,
            'kind' => $kind,
            'icon' => $icon,
            'title' => $title,
            'content' => $content,
            'meta' => $meta,
            'position' => $position ?? (Page::where('parent_id', $parent->id)->max('position') ?? -1) + 1,
        ]);
    }
}
