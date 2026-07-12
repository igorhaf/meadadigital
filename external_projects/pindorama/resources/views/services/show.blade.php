@extends('layouts.app')

@section('title', $service->title)
@section('description', \Illuminate\Support\Str::limit(strip_tags($service->description), 150))

@section('content')
    <div class="container-site py-6">
        {{-- Breadcrumb --}}
        <nav class="mb-5 flex flex-wrap items-center gap-1 text-sm text-neutral-500">
            <a href="{{ route('home') }}" class="hover:text-brand-700">Início</a>
            <span>/</span>
            <a href="{{ $service->category->url }}" class="hover:text-brand-700">{{ $service->category->name }}</a>
            <span>/</span>
            <span class="line-clamp-1 font-medium text-neutral-700">{{ $service->title }}</span>
        </nav>

        <div class="grid gap-8 lg:grid-cols-12">
            {{-- Gallery --}}
            @php($galleryImages = $service->images->map(fn ($i) => ['path' => $i->path, 'alt' => $i->alt ?: $service->title])->values())
            @php($galleryProps = ['images' => $galleryImages->isNotEmpty() ? $galleryImages : [['path' => $service->cover_url, 'alt' => $service->title]]])
            <div class="lg:col-span-7">
                <div data-island="ServiceGallery" data-props='@json($galleryProps)'>
                    <div class="aspect-square overflow-hidden rounded-2xl border border-neutral-200 bg-white">
                        <img src="{{ $service->cover_url }}" alt="{{ $service->title }}" class="h-full w-full object-cover">
                    </div>
                </div>
            </div>

            {{-- Scheduling box --}}
            <div class="lg:col-span-5">
                <div class="flex flex-wrap items-center gap-2">
                    <span class="chip bg-brand-50 text-brand-700">{{ $service->modality_label }}</span>
                    @if($service->is_featured)<span class="chip bg-gold-600 text-white">Destaque</span>@endif
                </div>

                <h1 class="mt-3 text-2xl font-extrabold leading-tight text-neutral-900 sm:text-3xl">{{ $service->title }}</h1>

                <div class="mt-2 flex flex-wrap items-center gap-3 text-sm text-neutral-500">
                    <span class="flex items-center gap-1.5 font-medium text-neutral-700">
                        <svg class="h-4 w-4 text-brand-600" fill="none" viewBox="0 0 24 24" stroke-width="1.8" stroke="currentColor"><path stroke-linecap="round" stroke-linejoin="round" d="M12 6v6h4.5m4.5 0a9 9 0 1 1-18 0 9 9 0 0 1 18 0Z" /></svg>
                        {{ $service->duration_label }}
                    </span>
                    <span>·</span>
                    <span class="flex items-center gap-1 text-amber-500">
                        @for($i = 1; $i <= 5; $i++)
                            <svg class="h-4 w-4 {{ $i <= round($service->rating) ? '' : 'text-neutral-300' }}" fill="currentColor" viewBox="0 0 20 20"><path d="M10 15l-5.878 3.09 1.123-6.545L.489 6.91l6.572-.955L10 0l2.939 5.955 6.572.955-4.756 4.635 1.123 6.545z"/></svg>
                        @endfor
                        <span class="ml-1 font-semibold text-neutral-600">{{ number_format($service->rating, 1, ',', '') }}</span>
                    </span>
                    <span>·</span>
                    <span>{{ $service->reviews_count }} avaliações</span>
                    <span>·</span>
                    <span>{{ $service->bookings_count }} agendamentos</span>
                </div>

                {{-- Price + scheduling card --}}
                <div class="mt-5 rounded-2xl border border-neutral-200 bg-white p-5">
                    @if($service->discount_percent)
                        <div class="flex items-center gap-2">
                            <span class="text-sm text-neutral-400 line-through">{{ money($service->compare_at_price) }}</span>
                            <span class="chip bg-gold-600 text-white">-{{ $service->discount_percent }}%</span>
                        </div>
                    @endif
                    <p class="mt-1 text-4xl font-extrabold text-neutral-900">{{ money($service->price) }}</p>
                    @if($service->max_installments > 1)
                        <p class="mt-1 text-sm text-neutral-500">em até <strong class="text-neutral-700">{{ $service->max_installments }}x de {{ money($service->installment_value) }}</strong> sem juros</p>
                    @endif

                    {{-- Scheduling island: pick location → date → slot → book --}}
                    @php($slotLocations = $service->locations()->where('is_active', true)->orderBy('name')->get()
                        ->map(fn ($l) => ['id' => $l->id, 'name' => $l->name, 'isOnline' => (bool) $l->is_online])->values())
                    @php($slotProps = [
                        'serviceId' => $service->id,
                        'durationMinutes' => (int) $service->duration_minutes,
                        'requiresPrepayment' => (bool) $service->requires_prepayment,
                        'locations' => $slotLocations,
                        'isAuthenticated' => auth()->check(),
                        'loginUrl' => route('login'),
                        'slotsUrl' => route('booking.slots'),
                        'bookingUrl' => route('booking.store'),
                        'csrf' => csrf_token(),
                        'patient' => [
                            'name' => auth()->user()->name ?? '',
                            'email' => auth()->user()->email ?? '',
                        ],
                    ])
                    <div class="mt-5 border-t border-neutral-100 pt-5" data-island="SlotPicker" data-props='@json($slotProps)'>
                        <p class="text-sm text-neutral-500">Carregando agenda…</p>
                    </div>
                </div>

                {{-- Therapist card --}}
                @php($professional = $service->professional)
                @php($therapistName = $service->professional_name ?: 'Terapeuta')
                @php($therapistSlug = $professional?->professional_slug)
                <div class="mt-4 flex items-center gap-3 rounded-2xl border border-neutral-200 bg-white p-4">
                    <div class="flex h-12 w-12 items-center justify-center rounded-full bg-brand-100 text-lg font-bold text-brand-700">
                        {{ \Illuminate\Support\Str::of($therapistName)->substr(0, 1)->upper() }}
                    </div>
                    <div class="min-w-0 flex-1">
                        @if($therapistSlug)
                            <a href="{{ route('professionals.show', $therapistSlug) }}" class="text-sm font-semibold text-neutral-800 hover:text-brand-700">{{ $therapistName }}</a>
                        @else
                            <p class="text-sm font-semibold text-neutral-800">{{ $therapistName }}</p>
                        @endif
                        @if($service->professional_city)
                            <p class="text-xs text-neutral-500">📍 {{ $service->professional_city }}{{ $service->professional_state ? ', ' . $service->professional_state : '' }}</p>
                        @endif
                    </div>
                    @if($professional && $professional->is_verified)
                        <span class="chip bg-brand-50 text-brand-700">Terapeuta verificado</span>
                    @endif
                </div>
            </div>
        </div>

        {{-- About the service --}}
        <div class="mt-10 grid gap-8 lg:grid-cols-12">
            <div class="lg:col-span-7">
                <h2 class="mb-3 text-lg font-bold text-neutral-900">Sobre o serviço</h2>
                <p class="whitespace-pre-line leading-relaxed text-neutral-600">{{ $service->description }}</p>
            </div>
        </div>
    </div>

    {{-- Related --}}
    @include('partials.service-carousel', [
        'title' => 'Você também pode gostar',
        'services' => $related,
        'products' => $related,
        'seeAllUrl' => $service->category->url,
    ])
@endsection
