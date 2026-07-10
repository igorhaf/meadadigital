@extends('layouts.app')

@section('content')
    @php($heroSlides = $heroBanners->map(fn ($b) => [
        'title' => $b->title,
        'subtitle' => $b->subtitle,
        'ctaLabel' => $b->cta_label,
        'link' => $b->link_url,
        'from' => $b->bg_from,
        'to' => $b->bg_to,
    ]))

    {{-- Hero --}}
    @php($heroProps = ['slides' => $heroSlides->values()])
    <section class="container-muda pt-6">
        <div data-island="HeroCarousel" data-props='@json($heroProps)'>
            {{-- SSR fallback: first slide --}}
            @if($heroBanners->first())
                @php($b = $heroBanners->first())
                <div class="flex min-h-[240px] flex-col justify-center rounded-3xl px-8 py-12 text-white" style="background-image: linear-gradient(120deg, {{ $b->bg_from }}, {{ $b->bg_to }})">
                    <h2 class="text-3xl font-extrabold sm:text-4xl">{{ $b->title }}</h2>
                    <p class="mt-3 max-w-md text-white/90">{{ $b->subtitle }}</p>
                    @if($b->link_url)<a href="{{ $b->link_url }}" class="mt-6 inline-block w-fit rounded-xl bg-white px-6 py-3 font-bold text-neutral-900">{{ $b->cta_label }}</a>@endif
                </div>
            @endif
        </div>
    </section>

    {{-- Shop by category --}}
    <section class="container-muda pt-10">
        <h2 class="mb-5 text-xl font-extrabold text-neutral-900 sm:text-2xl">Compre por categoria</h2>
        <div class="grid grid-cols-3 gap-4 sm:grid-cols-6">
            @foreach($categories as $cat)
                <a href="{{ $cat->url }}" class="group flex flex-col items-center gap-2 text-center">
                    <span class="flex h-16 w-16 items-center justify-center rounded-2xl text-3xl transition group-hover:-translate-y-1 sm:h-20 sm:w-20 sm:text-4xl" style="background-color: {{ $cat->accent }}1f">
                        {{ $cat->icon }}
                    </span>
                    <span class="text-xs font-medium text-neutral-600 group-hover:text-brand-700 sm:text-sm">{{ $cat->name }}</span>
                </a>
            @endforeach
        </div>
    </section>

    {{-- Deals --}}
    @include('partials.product-carousel', [
        'title' => '🔥 Ofertas do dia',
        'subtitle' => 'Garimpos com os melhores descontos',
        'products' => $deals,
        'seeAllUrl' => route('search', ['sort' => 'price_asc']),
    ])

    {{-- Promo strip tiles --}}
    <section class="container-muda py-6">
        <div class="grid gap-4 sm:grid-cols-3">
            @foreach($stripBanners as $b)
                <a href="{{ $b->link_url }}" class="group relative flex flex-col justify-between overflow-hidden rounded-2xl p-6 text-white transition hover:shadow-lg" style="background-image: linear-gradient(120deg, {{ $b->bg_from }}, {{ $b->bg_to }})">
                    <div>
                        <p class="text-lg font-extrabold">{{ $b->title }}</p>
                        <p class="text-sm text-white/85">{{ $b->subtitle }}</p>
                    </div>
                    <span class="mt-6 inline-flex w-fit items-center gap-1 text-sm font-bold">
                        {{ $b->cta_label }}
                        <svg class="h-4 w-4 transition group-hover:translate-x-1" fill="none" viewBox="0 0 24 24" stroke-width="2.5" stroke="currentColor"><path stroke-linecap="round" stroke-linejoin="round" d="M13.5 4.5 21 12m0 0-7.5 7.5M21 12H3" /></svg>
                    </span>
                </a>
            @endforeach
        </div>
    </section>

    {{-- Newest --}}
    @include('partials.product-carousel', [
        'title' => '✨ Acabou de chegar',
        'subtitle' => 'Novos achados no marketplace',
        'products' => $newest,
        'seeAllUrl' => route('search', ['sort' => 'newest']),
    ])

    {{-- Best sellers --}}
    @include('partials.product-carousel', [
        'title' => '🏆 Mais vendidos',
        'subtitle' => 'Os queridinhos do brechó',
        'products' => $bestSellers,
        'seeAllUrl' => route('search', ['sort' => 'best_selling']),
    ])

    {{-- Category showcases --}}
    @foreach($showcases as $showcase)
        @include('partials.product-carousel', [
            'title' => $showcase['category']->icon . ' ' . $showcase['category']->name,
            'products' => $showcase['products'],
            'seeAllUrl' => $showcase['category']->url,
        ])
    @endforeach

    {{-- Sustainability CTA --}}
    <section class="container-muda py-10">
        <div class="flex flex-col items-center gap-4 rounded-3xl bg-gradient-to-br from-brand-600 to-brand-800 px-8 py-12 text-center text-white">
            <span class="text-4xl">♻️</span>
            <h2 class="max-w-2xl text-2xl font-extrabold sm:text-3xl">Cada peça garimpada é uma escolha pelo planeta</h2>
            <p class="max-w-xl text-white/90">Ao comprar em brechós você prolonga a vida das roupas, economiza e reduz o impacto da moda. Junte-se à moda circular.</p>
            <a href="{{ route('search') }}" class="mt-2 rounded-xl bg-white px-6 py-3 font-bold text-brand-700 transition hover:bg-neutral-100">Explorar todos os produtos</a>
        </div>
    </section>
@endsection
