@extends('layouts.dashboard')

@section('title', 'Destaques')

@section('content')
    <h1 class="mb-1 text-2xl font-extrabold text-neutral-900">Produtos em destaque</h1>
    <p class="mb-6 text-sm text-neutral-500">Produtos marcados aqui aparecem na seção de destaques da home.</p>

    {{-- Currently featured --}}
    <div class="card mb-8 p-5">
        <h2 class="mb-4 font-bold text-neutral-900">Em destaque agora ({{ $featured->count() }})</h2>
        @if($featured->isEmpty())
            <p class="py-4 text-sm text-neutral-400">Nenhum produto em destaque. Escolha alguns abaixo.</p>
        @else
            <div class="flex flex-wrap gap-3">
                @foreach($featured as $product)
                    <div class="flex items-center gap-2 rounded-xl border border-neutral-200 py-1.5 pl-1.5 pr-3">
                        <img src="{{ $product->primary_image_url }}" alt="" class="h-9 w-9 rounded-lg object-cover">
                        <span class="max-w-40 truncate text-sm font-medium text-neutral-700">{{ $product->title }}</span>
                        <form method="POST" action="{{ route('admin.featured.toggle', $product) }}">
                            @csrf
                            <button class="text-neutral-400 hover:text-red-500" title="Remover">✕</button>
                        </form>
                    </div>
                @endforeach
            </div>
        @endif
    </div>

    {{-- Picker --}}
    <form method="GET" class="mb-4">
        <input name="q" value="{{ request('q') }}" placeholder="Buscar produtos para destacar…"
            class="w-full max-w-sm rounded-xl border border-neutral-300 px-4 py-2.5 text-sm focus:border-brand-400 focus:outline-none focus:ring-2 focus:ring-brand-100">
    </form>

    <div class="grid grid-cols-2 gap-4 sm:grid-cols-3 lg:grid-cols-4">
        @foreach($products as $product)
            <div class="card overflow-hidden">
                <img src="{{ $product->primary_image_url }}" alt="" class="aspect-square w-full object-cover">
                <div class="p-3">
                    <p class="line-clamp-1 text-sm font-medium text-neutral-800">{{ $product->title }}</p>
                    <p class="text-xs text-neutral-500">{{ money($product->price) }}</p>
                    <form method="POST" action="{{ route('admin.featured.toggle', $product) }}" class="mt-2">
                        @csrf
                        @if($product->is_featured)
                            <button class="btn-outline w-full !py-1.5 text-xs">★ Remover destaque</button>
                        @else
                            <button class="btn-brand w-full !py-1.5 text-xs">☆ Destacar</button>
                        @endif
                    </form>
                </div>
            </div>
        @endforeach
    </div>

    <div class="mt-6">{{ $products->onEachSide(1)->links() }}</div>
@endsection
