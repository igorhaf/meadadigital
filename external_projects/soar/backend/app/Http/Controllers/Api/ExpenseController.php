<?php

namespace App\Http\Controllers\Api;

use App\Http\Controllers\Concerns\AuthorizesPageAccess;
use App\Http\Controllers\Controller;
use App\Models\ExpenseEntry;
use App\Models\Page;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;

class ExpenseController extends Controller
{
    use AuthorizesPageAccess;

    public function index(Request $request, Page $page): JsonResponse
    {
        $this->authorizeAccess($request, $page);
        $this->authorizeKind($page, 'gastos');

        $month = $request->input('month'); // "2026-07"
        $query = $page->expenseEntries();
        if ($month) {
            $query->whereBetween('date', ["$month-01", date('Y-m-t', strtotime("$month-01"))]);
        }
        $entries = $query->get();

        return response()->json([
            'entries' => $entries,
            'total_cents' => $entries->sum('amount_cents'),
            'by_category' => $entries->groupBy(fn ($e) => $e->category ?? 'Sem categoria')
                ->map(fn ($group) => $group->sum('amount_cents')),
        ]);
    }

    public function store(Request $request, Page $page): JsonResponse
    {
        $this->authorizeAccess($request, $page);
        $this->authorizeKind($page, 'gastos');

        $entry = $page->expenseEntries()->create($this->validated($request));

        return response()->json($entry->fresh(), 201);
    }

    public function update(Request $request, Page $page, ExpenseEntry $expense): JsonResponse
    {
        $this->authorizeAccess($request, $page);
        abort_unless($expense->page_id === $page->id, 404);

        $expense->update($this->validated($request, partial: true));

        return response()->json($expense->fresh());
    }

    public function destroy(Request $request, Page $page, ExpenseEntry $expense): JsonResponse
    {
        $this->authorizeAccess($request, $page);
        abort_unless($expense->page_id === $page->id, 404);

        $expense->delete();

        return response()->json(['ok' => true]);
    }

    /** @return array<string, mixed> */
    private function validated(Request $request, bool $partial = false): array
    {
        $req = $partial ? 'sometimes' : 'required';

        return $request->validate([
            'date' => [$req, 'date'],
            'description' => [$req, 'string', 'max:255'],
            'category' => ['nullable', 'string', 'max:100'],
            'amount_cents' => [$req, 'integer', 'min:1'],
            'paid_by' => ['nullable', 'string', 'max:100'],
            'card' => ['nullable', 'string', 'max:100'],
        ]);
    }
}
