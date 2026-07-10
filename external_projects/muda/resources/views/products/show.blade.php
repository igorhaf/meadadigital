@extends('layouts.app')

@section('title', $product->title)
@section('description', \Illuminate\Support\Str::limit(strip_tags($product->description), 150))

@section('content')
    <div class="container-muda py-6">
        {{-- Breadcrumb --}}
        <nav class="mb-5 flex flex-wrap items-center gap-1 text-sm text-neutral-500">
            <a href="{{ route('home') }}" class="hover:text-brand-700">Início</a>
            <span>/</span>
            <a href="{{ $product->category->url }}" class="hover:text-brand-700">{{ $product->category->name }}</a>
            <span>/</span>
            <span class="line-clamp-1 font-medium text-neutral-700">{{ $product->title }}</span>
        </nav>

        <div class="grid gap-8 lg:grid-cols-12">
            {{-- Gallery --}}
            @php($galleryProps = ['images' => $product->images->map(fn ($i) => ['path' => $i->path, 'alt' => $i->alt])->values()])
            <div class="lg:col-span-7">
                <div data-island="ProductGallery" data-props='@json($galleryProps)'>
                    <div class="aspect-square overflow-hidden rounded-2xl border border-neutral-200 bg-white">
                        <img src="{{ $product->primary_image_url }}" alt="{{ $product->title }}" class="h-full w-full object-cover">
                    </div>
                </div>
            </div>

            {{-- Buy box --}}
            <div class="lg:col-span-5">
                <div class="flex items-center gap-2">
                    <span class="chip bg-neutral-100 text-neutral-600">{{ $product->condition_label }}</span>
                    @if($product->brand)<span class="chip bg-brand-50 text-brand-700">{{ $product->brand }}</span>@endif
                    @if($product->stock <= 1)<span class="chip bg-amber-100 text-amber-700">Última peça!</span>@endif
                </div>

                <h1 class="mt-3 text-2xl font-extrabold leading-tight text-neutral-900 sm:text-3xl">{{ $product->title }}</h1>

                <div class="mt-2 flex flex-wrap items-center gap-3 text-sm text-neutral-500">
                    <span class="flex items-center gap-1 text-amber-500">
                        @for($i = 1; $i <= 5; $i++)
                            <svg class="h-4 w-4 {{ $i <= round($product->rating) ? '' : 'text-neutral-300' }}" fill="currentColor" viewBox="0 0 20 20"><path d="M10 15l-5.878 3.09 1.123-6.545L.489 6.91l6.572-.955L10 0l2.939 5.955 6.572.955-4.756 4.635 1.123 6.545z"/></svg>
                        @endfor
                        <span class="ml-1 font-semibold text-neutral-600">{{ number_format($product->rating, 1, ',', '') }}</span>
                    </span>
                    <span>·</span>
                    <span>{{ $product->reviews_count }} avaliações</span>
                    <span>·</span>
                    <span>{{ $product->sold_count }} vendidos</span>
                </div>

                {{-- Price --}}
                <div class="mt-5 rounded-2xl border border-neutral-200 bg-white p-5">
                    @if($product->compare_at_price && $product->compare_at_price > $product->price)
                        <div class="flex items-center gap-2">
                            <span class="text-sm text-neutral-400 line-through">{{ money($product->compare_at_price) }}</span>
                            <span class="chip bg-grape-600 text-white">-{{ $product->discount_percent }}%</span>
                        </div>
                    @endif
                    <p class="mt-1 text-4xl font-extrabold text-neutral-900">{{ money($product->price) }}</p>
                    <p class="mt-1 text-sm text-neutral-500">em até <strong class="text-neutral-700">{{ $product->max_installments }}x de {{ money($product->installment_value) }}</strong> sem juros</p>

                    @if($product->free_shipping)
                        <p class="mt-3 flex items-center gap-2 text-sm font-semibold text-brand-700">
                            <svg class="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke-width="1.8" stroke="currentColor"><path stroke-linecap="round" stroke-linejoin="round" d="M8.25 18.75a1.5 1.5 0 0 1-3 0m3 0a1.5 1.5 0 0 0-3 0m3 0h6m-9 0H3.375a1.125 1.125 0 0 1-1.125-1.125V14.25m17.25 4.5a1.5 1.5 0 0 1-3 0m3 0a1.5 1.5 0 0 0-3 0m3 0h1.125c.621 0 1.129-.504 1.09-1.124a17.902 17.902 0 0 0-3.213-9.193 2.056 2.056 0 0 0-1.58-.86H14.25M16.5 18.75h-2.25m0-11.177v-.958c0-.568-.422-1.048-.987-1.106a48.554 48.554 0 0 0-10.026 0 1.106 1.106 0 0 0-.987 1.106v7.635m12-6.677v6.677m0 4.5v-4.5m0 0h-12" /></svg>
                        Frete grátis para todo o Brasil
                        </p>
                    @endif

                    {{-- Add to cart island (full) --}}
                    @php($buyProps = ['product' => $product->toCartPayload(), 'variant' => 'full'])
                    <div class="mt-5" data-island="AddToCart" data-props='@json($buyProps)'>
                        <a href="{{ route('cart') }}" class="btn-brand w-full">Adicionar ao carrinho</a>
                    </div>
                </div>

                {{-- Frete --}}
                @php($shipProps = ['productId' => $product->id, 'quoteUrl' => route('shipping.quote')])
                <div class="mt-4" data-island="ShippingCalculator" data-props='@json($shipProps)'></div>

                {{-- Seller card --}}
                <div class="mt-4 flex items-center gap-3 rounded-2xl border border-neutral-200 bg-white p-4">
                    <div class="flex h-12 w-12 items-center justify-center rounded-full bg-brand-100 text-lg font-bold text-brand-700">
                        {{ \Illuminate\Support\Str::of($product->seller_name)->substr(0, 1) }}
                    </div>
                    <div class="min-w-0 flex-1">
                        <p class="text-sm font-semibold text-neutral-800">{{ $product->seller_name }}</p>
                        <p class="text-xs text-neutral-500">📍 {{ $product->seller_location }}</p>
                    </div>
                    <span class="chip bg-brand-50 text-brand-700">Vendedor verificado</span>
                </div>
            </div>
        </div>

        {{-- Details + attributes --}}
        <div class="mt-10 grid gap-8 lg:grid-cols-12">
            <div class="lg:col-span-7">
                <h2 class="mb-3 text-lg font-bold text-neutral-900">Descrição</h2>
                <p class="leading-relaxed text-neutral-600">{{ $product->description }}</p>
                @if($product->condition_note)
                    <div class="mt-4 rounded-xl bg-amber-50 p-4 text-sm text-amber-800">
                        <strong>Estado da peça:</strong> {{ $product->condition_note }}
                    </div>
                @endif
            </div>
            <div class="lg:col-span-5">
                <h2 class="mb-3 text-lg font-bold text-neutral-900">Detalhes</h2>
                <dl class="divide-y divide-neutral-100 overflow-hidden rounded-2xl border border-neutral-200 bg-white text-sm">
                    @foreach(array_filter([
                        'Marca' => $product->brand,
                        'Condição' => $product->condition_label,
                        'Tamanho' => $product->size,
                        'Cor' => $product->color,
                        'Código' => $product->sku,
                    ]) as $label => $value)
                        <div class="flex justify-between px-4 py-3">
                            <dt class="text-neutral-500">{{ $label }}</dt>
                            <dd class="font-medium text-neutral-800">{{ $value }}</dd>
                        </div>
                    @endforeach
                </dl>
            </div>
        </div>
    </div>

    {{-- Related --}}
    @include('partials.product-carousel', [
        'title' => 'Você também pode gostar',
        'products' => $related,
        'seeAllUrl' => $product->category->url,
    ])
@endsection
