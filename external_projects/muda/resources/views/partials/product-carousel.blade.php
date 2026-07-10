{{-- Horizontal product row. Expects: $title, $products; optional $seeAllUrl, $subtitle. --}}
@if($products->isNotEmpty())
    <section data-row class="container-muda py-6">
        <div class="mb-4 flex items-end justify-between gap-4">
            <div>
                <h2 class="text-xl font-extrabold text-neutral-900 sm:text-2xl">{{ $title }}</h2>
                @isset($subtitle)<p class="text-sm text-neutral-500">{{ $subtitle }}</p>@endisset
            </div>
            <div class="flex items-center gap-2">
                @isset($seeAllUrl)
                    <a href="{{ $seeAllUrl }}" class="hidden text-sm font-semibold text-brand-700 hover:underline sm:inline">Ver todos</a>
                @endisset
                <button type="button" class="hidden h-9 w-9 items-center justify-center rounded-full border border-neutral-300 text-neutral-600 transition hover:border-brand-400 hover:text-brand-700 sm:flex" onclick="this.closest('[data-row]').querySelector('.row-track').scrollBy({left:-340,behavior:'smooth'})" aria-label="Anterior">
                    <svg class="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke-width="2.2" stroke="currentColor"><path stroke-linecap="round" stroke-linejoin="round" d="M15.75 19.5 8.25 12l7.5-7.5" /></svg>
                </button>
                <button type="button" class="hidden h-9 w-9 items-center justify-center rounded-full border border-neutral-300 text-neutral-600 transition hover:border-brand-400 hover:text-brand-700 sm:flex" onclick="this.closest('[data-row]').querySelector('.row-track').scrollBy({left:340,behavior:'smooth'})" aria-label="Próximo">
                    <svg class="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke-width="2.2" stroke="currentColor"><path stroke-linecap="round" stroke-linejoin="round" d="m8.25 4.5 7.5 7.5-7.5 7.5" /></svg>
                </button>
            </div>
        </div>

        <div class="row-track flex snap-x gap-4 overflow-x-auto pb-2 no-scrollbar">
            @foreach($products as $product)
                <div class="w-44 shrink-0 snap-start sm:w-52">
                    @include('partials.product-card', ['product' => $product])
                </div>
            @endforeach
        </div>
    </section>
@endif
