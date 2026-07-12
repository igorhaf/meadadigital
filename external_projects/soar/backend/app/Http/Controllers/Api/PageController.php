<?php

namespace App\Http\Controllers\Api;

use App\Http\Controllers\Controller;
use App\Models\Page;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;
use Illuminate\Support\Collection;
use Illuminate\Validation\Rule;

class PageController extends Controller
{
    /**
     * As duas árvores do dashboard. As raízes são as CATEGORIAS FIXAS
     * (is_system) — espelhadas em shared e personal; todo o resto é subpágina.
     */
    public function tree(Request $request): JsonResponse
    {
        $userId = $request->user()->id;
        $columns = ['id', 'parent_id', 'scope', 'kind', 'title', 'icon', 'position', 'is_system'];

        $shared = Page::shared()->orderBy('position')->get($columns);
        $personal = Page::personalOf($userId)->orderBy('position')->get($columns);

        return response()->json([
            'shared' => $this->buildTree($shared),
            'personal' => $this->buildTree($personal),
        ]);
    }

    public function show(Request $request, Page $page): JsonResponse
    {
        $this->authorizeAccess($request, $page);

        return response()->json([
            ...$page->only([
                'id', 'parent_id', 'owner_id', 'scope', 'kind', 'title', 'icon',
                'content', 'meta', 'position', 'is_system', 'updated_at',
            ]),
            'parent' => $page->parent?->only(['id', 'title', 'kind', 'meta']),
            'children' => $page->children()
                ->get(['id', 'parent_id', 'kind', 'title', 'icon', 'position', 'meta']),
        ]);
    }

    public function store(Request $request): JsonResponse
    {
        $data = $request->validate([
            'scope' => ['required', Rule::in([Page::SCOPE_SHARED, Page::SCOPE_PERSONAL])],
            'kind' => ['sometimes', Rule::in(Page::KINDS)],
            'parent_id' => ['required', 'integer', 'exists:pages,id'], // categorias raiz são fixas
            'title' => ['required', 'string', 'max:255'],
            'icon' => ['nullable', 'string', 'max:16'],
            'content' => ['nullable', 'string'],
            'meta' => ['sometimes', 'nullable', 'array'],
        ]);

        $user = $request->user();
        $kind = $data['kind'] ?? 'note';

        $parent = Page::findOrFail($data['parent_id']);
        $this->authorizeAccess($request, $parent);

        if ($parent->scope !== $data['scope']) {
            return response()->json([
                'error' => 'A página filha deve ter o mesmo escopo da página pai.',
                'reason' => 'scope_mismatch',
            ], 422);
        }

        if ($kind === 'registro_item' && $parent->kind !== 'registro') {
            return response()->json([
                'error' => 'Item de registro só pode ser criado dentro de um registro.',
                'reason' => 'registro_item_needs_registro',
            ], 422);
        }

        $position = Page::where('parent_id', $parent->id)->max('position');

        $page = Page::create([
            'scope' => $data['scope'],
            'kind' => $kind,
            'parent_id' => $parent->id,
            'owner_id' => $data['scope'] === Page::SCOPE_PERSONAL ? $user->id : null,
            'title' => $data['title'],
            'icon' => $data['icon'] ?? null,
            'content' => $data['content'] ?? null,
            'meta' => $data['meta'] ?? null,
            'position' => $position === null ? 0 : $position + 1,
        ]);

        return response()->json($page, 201);
    }

    public function update(Request $request, Page $page): JsonResponse
    {
        $this->authorizeAccess($request, $page);

        $data = $request->validate([
            'title' => ['sometimes', 'required', 'string', 'max:255'],
            'icon' => ['sometimes', 'nullable', 'string', 'max:16'],
            'content' => ['sometimes', 'nullable', 'string'],
            'meta' => ['sometimes', 'nullable', 'array'],
        ]);

        $page->update($data);

        return response()->json($page->fresh());
    }

    public function move(Request $request, Page $page): JsonResponse
    {
        $this->authorizeAccess($request, $page);

        if ($page->is_system) {
            return response()->json([
                'error' => 'Categorias fixas não podem ser movidas.',
                'reason' => 'system_page',
            ], 422);
        }

        $data = $request->validate([
            'parent_id' => ['required', 'integer', 'exists:pages,id'], // raiz é só de categoria
            'position' => ['required', 'integer', 'min:0'],
        ]);

        $parent = Page::findOrFail($data['parent_id']);
        $this->authorizeAccess($request, $parent);

        if ($parent->scope !== $page->scope) {
            return response()->json([
                'error' => 'Não é possível mover a página para outro escopo.',
                'reason' => 'scope_mismatch',
            ], 422);
        }

        // Impede ciclo: o novo pai não pode ser a própria página nem um descendente dela.
        $cursor = $parent;
        while ($cursor !== null) {
            if ($cursor->id === $page->id) {
                return response()->json([
                    'error' => 'Não é possível mover a página para dentro de si mesma.',
                    'reason' => 'circular_move',
                ], 422);
            }
            $cursor = $cursor->parent;
        }

        $page->update([
            'parent_id' => $data['parent_id'],
            'position' => $data['position'],
        ]);

        return response()->json($page->fresh());
    }

    public function destroy(Request $request, Page $page): JsonResponse
    {
        $this->authorizeAccess($request, $page);

        if ($page->is_system) {
            return response()->json([
                'error' => 'Esta categoria é fixa e não pode ser excluída.',
                'reason' => 'system_page',
            ], 422);
        }

        $page->delete(); // filhos caem em cascata (FK cascadeOnDelete)

        return response()->json(['ok' => true]);
    }

    /**
     * @param  Collection<int, Page>  $pages
     * @return array<int, array<string, mixed>>
     */
    private function buildTree(Collection $pages, ?int $parentId = null): array
    {
        return $pages
            ->where('parent_id', $parentId)
            ->map(fn (Page $page) => [
                'id' => $page->id,
                'parent_id' => $page->parent_id,
                'scope' => $page->scope,
                'kind' => $page->kind,
                'title' => $page->title,
                'icon' => $page->icon,
                'position' => $page->position,
                'is_system' => $page->is_system,
                'children' => $this->buildTree($pages, $page->id),
            ])
            ->values()
            ->all();
    }

    private function authorizeAccess(Request $request, Page $page): void
    {
        abort_unless(
            $page->isAccessibleBy($request->user()),
            403,
            'Você não tem acesso a esta página.',
        );
    }
}
