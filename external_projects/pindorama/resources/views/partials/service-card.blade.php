{{-- Reusable service card. Expects $service (with images loaded). --}}
<div class="card group flex h-full flex-col overflow-hidden">
    <a href="{{ $service->url }}" class="relative block aspect-square overflow-hidden bg-neutral-100">
        <img
            src="{{ $service->cover_url }}"
            alt="{{ $service->title }}"
            loading="lazy"
            class="h-full w-full object-cover transition duration-300 group-hover:scale-105"
        >

        @if($service->discount_percent)
            <div class="absolute left-2 top-2 flex flex-col gap-1">
                <span class="chip bg-gold-600 text-white shadow-sm">-{{ $service->discount_percent }}%</span>
            </div>
        @endif
    </a>

    <div class="flex flex-1 flex-col p-3">
        <div class="mb-1 flex items-center gap-2 text-xs text-neutral-400">
            <span class="chip bg-neutral-100 text-neutral-600">{{ $service->modality_label }}</span>
        </div>

        <a href="{{ $service->url }}" class="line-clamp-2 text-sm font-semibold text-neutral-800 transition hover:text-brand-700">{{ $service->title }}</a>

        @if($service->professional_name)
            <p class="mt-0.5 text-xs text-neutral-500">{{ $service->professional_name }}@if($service->professional_city) · 📍 {{ $service->professional_city }}@endif</p>
        @endif

        <div class="mt-1 flex items-center gap-2 text-xs text-neutral-400">
            <span class="chip bg-neutral-100 text-neutral-600">⏱ {{ $service->duration_label }}</span>
        </div>

        <div class="mt-1 flex items-center gap-1 text-xs text-amber-500">
            <svg class="h-3.5 w-3.5" fill="currentColor" viewBox="0 0 20 20"><path d="M10 15l-5.878 3.09 1.123-6.545L.489 6.91l6.572-.955L10 0l2.939 5.955 6.572.955-4.756 4.635 1.123 6.545z"/></svg>
            <span class="font-semibold text-neutral-600">{{ number_format($service->rating, 1, ',', '') }}</span>
            <span class="text-neutral-400">({{ $service->reviews_count }})</span>
        </div>

        <div class="mt-auto pt-3">
            @if($service->compare_at_price && $service->compare_at_price > $service->price)
                <p class="text-xs text-neutral-400 line-through">{{ money($service->compare_at_price) }}</p>
            @endif
            <p class="text-lg font-extrabold text-neutral-900">{{ money($service->price) }}</p>
            <p class="text-xs text-neutral-500">em até {{ $service->max_installments }}x de {{ money($service->installment_value) }}</p>

            <a href="{{ $service->url }}" class="btn-brand mt-3 w-full !py-2 text-sm">Agendar</a>
        </div>
    </div>
</div>
