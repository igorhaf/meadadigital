<?php

namespace App\Http\Controllers\Api;

use App\Http\Controllers\Concerns\AuthorizesPageAccess;
use App\Http\Controllers\Controller;
use App\Models\CalendarEvent;
use App\Models\Page;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;

class CalendarController extends Controller
{
    use AuthorizesPageAccess;

    public function index(Request $request, Page $page): JsonResponse
    {
        $this->authorizeAccess($request, $page);
        $this->authorizeKind($page, 'calendar');

        $query = $page->events();
        if ($request->filled('from')) {
            $query->where('starts_at', '>=', $request->date('from'));
        }
        if ($request->filled('to')) {
            $query->where('starts_at', '<', $request->date('to'));
        }

        return response()->json($query->get());
    }

    public function store(Request $request, Page $page): JsonResponse
    {
        $this->authorizeAccess($request, $page);
        $this->authorizeKind($page, 'calendar');

        $data = $this->validated($request);
        $event = $page->events()->create($data);

        return response()->json($event->fresh(), 201);
    }

    public function update(Request $request, Page $page, CalendarEvent $event): JsonResponse
    {
        $this->authorizeAccess($request, $page);
        abort_unless($event->page_id === $page->id, 404);

        $event->update($this->validated($request, partial: true));

        return response()->json($event->fresh());
    }

    public function destroy(Request $request, Page $page, CalendarEvent $event): JsonResponse
    {
        $this->authorizeAccess($request, $page);
        abort_unless($event->page_id === $page->id, 404);

        $event->delete();

        return response()->json(['ok' => true]);
    }

    /** @return array<string, mixed> */
    private function validated(Request $request, bool $partial = false): array
    {
        $req = $partial ? 'sometimes' : 'required';

        return $request->validate([
            'title' => [$req, 'string', 'max:255'],
            'starts_at' => [$req, 'date'],
            'ends_at' => ['nullable', 'date', 'after_or_equal:starts_at'],
            'all_day' => ['sometimes', 'boolean'],
            'recurrence' => ['sometimes', 'in:none,daily,weekly,monthly,yearly'],
            'notes' => ['nullable', 'string'],
        ]);
    }
}
