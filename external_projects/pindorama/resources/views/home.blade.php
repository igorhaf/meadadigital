@extends('layouts.app')

@section('content')
    @php($heroSlides = $heroBanners->map(fn ($b) => [
        'title' => $b->title,
        'subtitle' => $b->subtitle,
        'ctaLabel' => $b->cta_label,
        'link' => $b->link_url,
        'from' => $b->bg_from,
        'to' => $b->bg_to,
        'image' => $b->image_path,
    ]))

    {{-- Hero --}}
    @php($heroProps = ['slides' => $heroSlides->values()])
    <section class="container-site pt-6">
        <div data-island="HeroCarousel" data-props='@json($heroProps)'>
            {{-- SSR fallback: first slide --}}
            @if($heroBanners->first())
                @php($b = $heroBanners->first())
                <div class="relative flex min-h-[240px] flex-col justify-center overflow-hidden rounded-3xl px-8 py-12 text-white sm:min-h-[320px] sm:px-14" style="background-image: linear-gradient(120deg, {{ $b->bg_from }}, {{ $b->bg_to }})">
                    @if($b->image_path)
                        <img src="{{ $b->image_path }}" alt="" class="absolute inset-0 h-full w-full object-cover">
                        <div class="absolute inset-0" style="background-image: linear-gradient(100deg, {{ $b->bg_from }}f2 0%, {{ $b->bg_from }}cc 38%, {{ $b->bg_from }}55 68%, transparent 95%)"></div>
                    @endif
                    <div class="relative max-w-xl">
                        <h2 class="text-3xl font-extrabold sm:text-4xl">{{ $b->title }}</h2>
                        <p class="mt-3 max-w-md text-white/90">{{ $b->subtitle }}</p>
                        @if($b->link_url)<a href="{{ $b->link_url }}" class="mt-6 inline-block w-fit rounded-full bg-white px-6 py-3 font-bold text-brand-800">{{ $b->cta_label }}</a>@endif
                    </div>
                </div>
            @endif
        </div>
    </section>

    {{-- Explore por prática --}}
    @if($categories->isNotEmpty())
        <section class="container-site pt-10">
            <h2 class="mb-5 text-xl font-extrabold text-neutral-900 sm:text-2xl">Explore por prática</h2>
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
    @endif

    {{-- Ofertas --}}
    @include('partials.service-carousel', [
        'title' => '🏷️ Ofertas da semana',
        'subtitle' => 'Sessões com preços especiais para começar',
        'services' => $deals,
        'seeAllUrl' => route('search', ['sort' => 'price_asc']),
    ])

    {{-- Promo strip tiles --}}
    @if($stripBanners->isNotEmpty())
        <section class="container-site py-6">
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
    @endif

    {{-- Novidades --}}
    @include('partials.service-carousel', [
        'title' => '✨ Novidades',
        'subtitle' => 'Terapias recém-chegadas ao marketplace',
        'services' => $newest,
        'seeAllUrl' => route('search', ['sort' => 'newest']),
    ])

    {{-- Mais agendados --}}
    @include('partials.service-carousel', [
        'title' => '🌿 Mais agendados',
        'subtitle' => 'As sessões preferidas de quem cuida do bem-estar',
        'services' => $mostBooked,
        'seeAllUrl' => route('search', ['sort' => 'most_booked']),
    ])

    {{-- Terapeutas em destaque --}}
    @if($featuredProfessionals->isNotEmpty())
        <section data-row class="container-site py-6">
            <div class="mb-4 flex items-end justify-between gap-4">
                <div>
                    <h2 class="text-xl font-extrabold text-neutral-900 sm:text-2xl">Terapeutas em destaque</h2>
                    <p class="text-sm text-neutral-500">Profissionais verificados prontos para te atender</p>
                </div>
                <a href="{{ route('professionals.index') }}" class="hidden text-sm font-semibold text-brand-700 hover:underline sm:inline">Ver todos</a>
            </div>

            <div class="row-track flex snap-x gap-4 overflow-x-auto pb-2 no-scrollbar">
                @foreach($featuredProfessionals as $pro)
                    @php($proName = $pro->professional_name ?: ($pro->store_name ?: $pro->name))
                    @php($proCity = $pro->professional_city ?: $pro->store_location)
                    @php($proSlug = $professionalSlugAvailable ? $pro->professional_slug : null)
                    @php($proInitial = mb_strtoupper(mb_substr($proName, 0, 1)))
                    @if($proSlug)
                        <a href="{{ route('professionals.show', $proSlug) }}" class="card flex w-44 shrink-0 snap-start flex-col items-center gap-3 p-5 text-center transition hover:shadow-lg sm:w-52">
                            <span class="flex h-16 w-16 items-center justify-center rounded-full bg-brand-100 text-2xl font-extrabold text-brand-700">{{ $proInitial }}</span>
                            <span class="line-clamp-2 text-sm font-bold text-neutral-900">{{ $proName }}</span>
                            @if($proCity)<span class="text-xs text-neutral-500">{{ $proCity }}</span>@endif
                        </a>
                    @else
                        <div class="card flex w-44 shrink-0 snap-start flex-col items-center gap-3 p-5 text-center sm:w-52">
                            <span class="flex h-16 w-16 items-center justify-center rounded-full bg-brand-100 text-2xl font-extrabold text-brand-700">{{ $proInitial }}</span>
                            <span class="line-clamp-2 text-sm font-bold text-neutral-900">{{ $proName }}</span>
                            @if($proCity)<span class="text-xs text-neutral-500">{{ $proCity }}</span>@endif
                        </div>
                    @endif
                @endforeach
            </div>
        </section>
    @endif

    {{-- Vitrines por prática --}}
    @foreach($showcases as $showcase)
        @include('partials.service-carousel', [
            'title' => $showcase['category']->icon . ' ' . $showcase['category']->name,
            'subtitle' => 'Terapeutas de ' . $showcase['category']->name,
            'services' => $showcase['services'],
            'seeAllUrl' => $showcase['category']->url,
        ])
    @endforeach

    {{-- Wellness CTA --}}
    <section class="container-site py-10">
        <div class="flex flex-col items-center gap-4 rounded-3xl bg-gradient-to-br from-brand-600 to-brand-800 px-8 py-12 text-center text-white">
            <span class="text-4xl">🧘</span>
            <h2 class="max-w-2xl text-2xl font-extrabold sm:text-3xl">Cuidar de você é um ato de equilíbrio</h2>
            <p class="max-w-xl text-white/90">Conecte-se a terapeutas de práticas integrativas — acupuntura, reiki, ayurveda, medicina tradicional chinesa e muito mais. Encontre a sessão certa para o seu momento e agende em minutos.</p>
            <a href="{{ route('search') }}" class="mt-2 rounded-full bg-white px-6 py-3 font-bold text-brand-700 transition hover:bg-neutral-100">Explorar todas as terapias</a>
        </div>
    </section>
@endsection
