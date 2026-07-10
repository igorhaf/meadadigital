{{-- Reusable product card. Expects $product (with images loaded). --}}
<div class="card group flex h-full flex-col overflow-hidden">
    <a href="{{ $product->url }}" class="relative block aspect-square overflow-hidden bg-neutral-100">
        <img
            src="{{ $product->primary_image_url }}"
            alt="{{ $product->title }}"
            loading="lazy"
            class="h-full w-full object-cover transition duration-300 group-hover:scale-105"
        >

        <div class="absolute left-2 top-2 flex flex-col gap-1">
            @if($product->discount_percent)
                <span class="chip bg-grape-600 text-white shadow-sm">-{{ $product->discount_percent }}%</span>
            @endif
            @if($product->condition === 'novo')
                <span class="chip bg-brand-600 text-white shadow-sm">Novo c/ etiqueta</span>
            @endif
        </div>

        @if($product->free_shipping)
            <span class="absolute bottom-2 left-2 chip bg-white/95 text-brand-700 shadow-sm">🚚 Frete grátis</span>
        @endif
    </a>

    <div class="flex flex-1 flex-col p-3">
        <div class="mb-1 flex items-center gap-2 text-xs text-neutral-400">
            <span class="chip bg-neutral-100 text-neutral-600">{{ $product->condition_label }}</span>
            @if($product->size)<span>Tam. {{ $product->size }}</span>@endif
        </div>

        <a href="{{ $product->url }}" class="line-clamp-2 text-sm font-semibold text-neutral-800 transition hover:text-brand-700">{{ $product->title }}</a>

        @if($product->brand)
            <p class="mt-0.5 text-xs text-neutral-500">{{ $product->brand }}</p>
        @endif

        <div class="mt-1 flex items-center gap-1 text-xs text-amber-500">
            <svg class="h-3.5 w-3.5" fill="currentColor" viewBox="0 0 20 20"><path d="M10 15l-5.878 3.09 1.123-6.545L.489 6.91l6.572-.955L10 0l2.939 5.955 6.572.955-4.756 4.635 1.123 6.545z"/></svg>
            <span class="font-semibold text-neutral-600">{{ number_format($product->rating, 1, ',', '') }}</span>
            <span class="text-neutral-400">({{ $product->reviews_count }})</span>
        </div>

        <div class="mt-auto pt-3">
            @if($product->compare_at_price && $product->compare_at_price > $product->price)
                <p class="text-xs text-neutral-400 line-through">{{ money($product->compare_at_price) }}</p>
            @endif
            <p class="text-lg font-extrabold text-neutral-900">{{ money($product->price) }}</p>
            <p class="text-xs text-neutral-500">em até {{ $product->max_installments }}x de {{ money($product->installment_value) }}</p>

            @php($cardProps = ['product' => $product->toCartPayload(), 'variant' => 'card'])
            <div
                class="mt-3"
                data-island="AddToCart"
                data-props='@json($cardProps)'
            >
                {{-- fallback link (no-JS) --}}
                <a href="{{ $product->url }}" class="btn-outline w-full !py-2 text-sm">Ver produto</a>
            </div>
        </div>
    </div>
</div>
