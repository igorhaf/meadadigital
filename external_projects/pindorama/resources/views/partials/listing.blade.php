{{-- Listagem compartilhada do marketplace. Espera: $title, $services (paginator), $facets, $action. --}}
@php
    $sortOptions = [
        'relevance' => 'Relevância',
        'price_asc' => 'Menor preço',
        'price_desc' => 'Maior preço',
        'newest' => 'Novidades',
        'most_booked' => 'Mais agendados',
        'rating' => 'Melhor avaliados',
    ];
    $selected = fn ($key, $value) => in_array((string) $value, array_map('strval', (array) request($key, [])), true);
    $hasFilters = collect(['modality', 'city', 'min', 'max'])->contains(fn ($k) => request()->filled($k));
@endphp

<div class="container-site py-6">
    @isset($breadcrumbs)
        <nav class="mb-4 flex flex-wrap items-center gap-1 text-sm text-neutral-500">
            <a href="{{ route('home') }}" class="hover:text-brand-700">Início</a>
            @foreach($breadcrumbs as $crumb)
                <span>/</span>
                <a href="{{ $crumb->url }}" class="hover:text-brand-700 {{ $loop->last ? 'font-semibold text-neutral-700' : '' }}">{{ $crumb->name }}</a>
            @endforeach
        </nav>
    @endisset

    <div class="mb-6 flex flex-wrap items-end justify-between gap-3">
        <div>
            <h1 class="text-2xl font-extrabold text-neutral-900 sm:text-3xl">{{ $title }}</h1>
            <p class="text-sm text-neutral-500">{{ $services->total() }} {{ $services->total() === 1 ? 'serviço encontrado' : 'serviços encontrados' }}</p>
        </div>

        <form method="GET" action="{{ $action }}" class="flex items-center gap-2">
            @foreach(request()->except(['sort', 'page']) as $k => $v)
                @if(is_array($v)) @foreach($v as $vv)<input type="hidden" name="{{ $k }}[]" value="{{ $vv }}">@endforeach
                @else <input type="hidden" name="{{ $k }}" value="{{ $v }}">@endif
            @endforeach
            <label class="text-sm text-neutral-500">Ordenar:</label>
            <select name="sort" onchange="this.form.submit()" class="rounded-xl border border-neutral-300 bg-white px-3 py-2 text-sm font-medium text-neutral-700 focus:border-brand-400 focus:outline-none focus:ring-2 focus:ring-brand-100">
                @foreach($sortOptions as $value => $label)
                    <option value="{{ $value }}" @selected(request('sort', 'relevance') === $value)>{{ $label }}</option>
                @endforeach
            </select>
        </form>
    </div>

    <div class="grid gap-6 lg:grid-cols-[260px_1fr]">
        {{-- Barra lateral de filtros --}}
        <aside>
            <details data-filters class="rounded-2xl border border-neutral-200 bg-white lg:sticky lg:top-24">
                <summary class="flex cursor-pointer items-center justify-between px-4 py-3 font-bold text-neutral-800 lg:cursor-default">
                    <span>Filtros</span>
                    @if($hasFilters)<a href="{{ $action }}" class="text-xs font-medium text-brand-700 hover:underline">Limpar</a>@endif
                </summary>

                <form method="GET" action="{{ $action }}" class="space-y-5 border-t border-neutral-100 p-4">
                    @if(request('q'))<input type="hidden" name="q" value="{{ request('q') }}">@endif
                    <input type="hidden" name="sort" value="{{ request('sort', 'relevance') }}">

                    {{-- Modalidade --}}
                    <div>
                        <h3 class="mb-2 text-sm font-bold text-neutral-800">Modalidade</h3>
                        <div class="space-y-1.5">
                            @foreach($facets['modalityLabels'] as $value => $label)
                                <label class="flex cursor-pointer items-center gap-2 text-sm text-neutral-600">
                                    <input type="checkbox" name="modality[]" value="{{ $value }}" @checked($selected('modality', $value)) onchange="this.form.submit()" class="rounded border-neutral-300 text-brand-600 focus:ring-brand-500">
                                    {{ $label }}
                                    <span class="ml-auto text-xs text-neutral-400">{{ $facets['modalities'][$value] ?? 0 }}</span>
                                </label>
                            @endforeach
                        </div>
                    </div>

                    {{-- Preço --}}
                    <div>
                        <h3 class="mb-2 text-sm font-bold text-neutral-800">Preço</h3>
                        <div class="flex items-center gap-2">
                            <input type="number" name="min" value="{{ request('min') }}" placeholder="mín" min="0" class="w-full rounded-lg border border-neutral-300 px-2 py-1.5 text-sm focus:border-brand-400 focus:outline-none">
                            <span class="text-neutral-400">—</span>
                            <input type="number" name="max" value="{{ request('max') }}" placeholder="máx" min="0" class="w-full rounded-lg border border-neutral-300 px-2 py-1.5 text-sm focus:border-brand-400 focus:outline-none">
                        </div>
                        <p class="mt-1 text-xs text-neutral-400">Faixa: {{ money($facets['priceMin']) }} – {{ money($facets['priceMax']) }}</p>
                        <button class="btn-outline mt-2 w-full !py-1.5 text-xs">Aplicar preço</button>
                    </div>

                    {{-- Cidade --}}
                    @if(count($facets['cities']))
                        <div>
                            <h3 class="mb-2 text-sm font-bold text-neutral-800">Cidade</h3>
                            <div class="max-h-44 space-y-1.5 overflow-y-auto pr-1">
                                @foreach($facets['cities'] as $city)
                                    <label class="flex cursor-pointer items-center gap-2 text-sm text-neutral-600">
                                        <input type="checkbox" name="city[]" value="{{ $city }}" @checked($selected('city', $city)) onchange="this.form.submit()" class="rounded border-neutral-300 text-brand-600 focus:ring-brand-500">
                                        {{ $city }}
                                    </label>
                                @endforeach
                            </div>
                        </div>
                    @endif
                </form>
            </details>
        </aside>

        {{-- Grade --}}
        <div>
            @if($services->isEmpty())
                <div class="flex flex-col items-center justify-center rounded-2xl border border-dashed border-neutral-300 bg-white py-20 text-center">
                    <div class="text-5xl">🔍</div>
                    <p class="mt-4 text-lg font-semibold text-neutral-700">Nenhum serviço encontrado</p>
                    <p class="mt-1 text-sm text-neutral-500">Tente ajustar os filtros ou buscar por outro termo.</p>
                    <a href="{{ $action }}" class="btn-brand mt-6">Limpar filtros</a>
                </div>
            @else
                <div class="grid grid-cols-2 gap-4 sm:grid-cols-3 xl:grid-cols-4">
                    @foreach($services as $service)
                        @include('partials.service-card', ['service' => $service])
                    @endforeach
                </div>

                <div class="mt-8">
                    {{ $services->onEachSide(1)->links() }}
                </div>
            @endif
        </div>
    </div>
</div>

<script>
    // Painel de filtros aberto por padrão no desktop; recolhido sob "Filtros" no mobile.
    document.querySelectorAll('details[data-filters]').forEach((d) => {
        if (window.matchMedia('(min-width: 1024px)').matches) d.open = true;
    });
</script>
