<?php

namespace App\Http\Controllers\Professional;

use App\Http\Controllers\Controller;
use App\Models\Service;
use App\Models\ServiceCategory;
use Illuminate\Contracts\View\View;
use Illuminate\Http\RedirectResponse;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\Storage;
use Illuminate\Support\Str;

class ServiceController extends Controller
{
    public function index(Request $request): View
    {
        $services = Service::forProfessional(auth()->user())
            ->when($request->query('q'), fn ($q, $term) => $q->where('title', 'ilike', "%{$term}%"))
            ->latest()
            ->paginate(15)
            ->withQueryString();

        return view('professional.services.index', compact('services'));
    }

    public function create(): View
    {
        return view('professional.services.create', [
            'service' => new Service([
                'modality' => 'presencial',
                'duration_minutes' => 60,
                'buffer_minutes' => 0,
                'max_installments' => 1,
                'requires_prepayment' => true,
                'is_active' => true,
            ]),
            'categories' => $this->categoryOptions(),
            'locations' => $this->locationOptions(),
            'selectedLocations' => [],
        ]);
    }

    public function store(Request $request): RedirectResponse
    {
        $data = $this->validated($request);
        $locationIds = $this->validLocationIds($request);
        $user = auth()->user();

        $service = Service::create($data + [
            'professional_id' => $user->id,
            'slug' => $this->uniqueSlug($data['title']),
            'professional_name' => $user->professional_name ?: $user->name,
            'professional_city' => $user->city,
            'professional_state' => $user->state,
            'cover_path' => $this->storeCover($request),
        ]);

        $service->locations()->sync($locationIds);

        return redirect()->route('professional.services.index')->with('status', 'Serviço publicado com sucesso!');
    }

    public function edit(Service $service): View
    {
        $this->authorize('update', $service);

        return view('professional.services.edit', [
            'service' => $service,
            'categories' => $this->categoryOptions(),
            'locations' => $this->locationOptions(),
            'selectedLocations' => $service->locations()->pluck('attendance_locations.id')->all(),
        ]);
    }

    public function update(Request $request, Service $service): RedirectResponse
    {
        $this->authorize('update', $service);

        $data = $this->validated($request);
        $locationIds = $this->validLocationIds($request);

        if ($cover = $this->storeCover($request)) {
            $data['cover_path'] = $cover;
        }

        $service->update($data);
        $service->locations()->sync($locationIds);

        return redirect()->route('professional.services.index')->with('status', 'Serviço atualizado.');
    }

    public function destroy(Service $service): RedirectResponse
    {
        $this->authorize('delete', $service);
        $service->delete();

        return redirect()->route('professional.services.index')->with('status', 'Serviço removido.');
    }

    public function toggle(Service $service): RedirectResponse
    {
        $this->authorize('update', $service);
        $service->update(['is_active' => ! $service->is_active]);

        return back()->with('status', $service->is_active ? 'Serviço reativado.' : 'Serviço pausado.');
    }

    /* --------------------------------------------------------------- helpers */

    /**
     * Validate + normalize the service payload.
     *
     * The `cover` upload is validated here but handled separately (it is not a
     * fillable column), so it is stripped before mass assignment.
     *
     * @return array<string,mixed>
     */
    private function validated(Request $request): array
    {
        $data = $request->validate([
            'service_category_id' => ['required', 'exists:service_categories,id'],
            'title' => ['required', 'string', 'max:255'],
            'description' => ['nullable', 'string'],
            'modality' => ['required', 'in:presencial,online,ambos'],
            'duration_minutes' => ['required', 'integer', 'min:5', 'max:480'],
            'buffer_minutes' => ['nullable', 'integer', 'min:0', 'max:120'],
            'price' => ['required', 'numeric', 'min:0', 'max:1000000'],
            'compare_at_price' => ['nullable', 'numeric', 'min:0', 'max:1000000'],
            'max_installments' => ['required', 'integer', 'min:1', 'max:12'],
            'requires_prepayment' => ['nullable', 'boolean'],
            'is_active' => ['nullable', 'boolean'],
            'is_featured' => ['nullable', 'boolean'],
            'cover' => ['nullable', 'image', 'mimes:jpeg,jpg,png,webp', 'max:8192'],
        ]);

        unset($data['cover']);
        $data['buffer_minutes'] = $data['buffer_minutes'] ?? 0;

        return $data;
    }

    private function categoryOptions()
    {
        return ServiceCategory::with('children')->roots()->orderBy('position')->get();
    }

    /** The current professional's active attendance locations (for assignment). */
    private function locationOptions()
    {
        return auth()->user()->attendanceLocations()->where('is_active', true)->orderBy('name')->get();
    }

    /**
     * Requested location ids, filtered to ones the professional actually owns.
     *
     * @return array<int>
     */
    private function validLocationIds(Request $request): array
    {
        $requested = array_map('intval', (array) $request->input('locations', []));
        $owned = auth()->user()->attendanceLocations()->pluck('id')->all();

        return array_values(array_intersect($requested, $owned));
    }

    private function uniqueSlug(string $title): string
    {
        $base = Str::slug($title) ?: 'servico';
        $slug = $base;
        $i = 1;
        while (Service::where('slug', $slug)->exists()) {
            $slug = $base . '-' . (++$i);
        }

        return $slug;
    }

    /**
     * Store the optional cover upload on the public disk and return its public
     * path (`/storage/...`), or null when no valid file was sent.
     */
    private function storeCover(Request $request): ?string
    {
        $file = $request->file('cover');
        if (! $file || ! $file->isValid()) {
            return null;
        }

        // Reencoda para o tamanho usado na galeria/zoom (economia de disco).
        $stored = \App\Support\ImageOptimizer::store($file, 'services/covers');

        return '/storage/' . $stored;
    }
}
