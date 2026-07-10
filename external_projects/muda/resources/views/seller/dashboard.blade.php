@extends('layouts.dashboard')

@section('title', 'Visão geral')

@section('content')
    @php($user = auth()->user())
    <div class="mb-6 flex flex-wrap items-center justify-between gap-3">
        <div>
            <h1 class="text-2xl font-extrabold text-neutral-900">Olá, {{ \Illuminate\Support\Str::of($user->name)->before(' ') }}! 👋</h1>
            <p class="text-sm text-neutral-500">Painel da loja <strong>{{ $user->store_name }}</strong>
                @if($user->store_url) · <a href="{{ $user->store_url }}" class="text-brand-700 hover:underline">ver loja pública ↗</a>@endif
            </p>
        </div>
        <a href="{{ route('seller.products.create') }}" class="btn-brand">+ Cadastrar produto</a>
    </div>

    {{-- Stats --}}
    <div class="grid grid-cols-2 gap-4 lg:grid-cols-4">
        @foreach([
            ['Produtos', $stats['products'], '📦', 'text-neutral-900'],
            ['Ativos', $stats['active'], '✅', 'text-brand-700'],
            ['Unidades vendidas', $stats['sold'], '🛍️', 'text-neutral-900'],
            ['Faturamento', money($stats['revenue']), '💰', 'text-brand-700'],
        ] as [$label, $value, $icon, $color])
            <div class="card p-5">
                <div class="flex items-center justify-between">
                    <span class="text-sm text-neutral-500">{{ $label }}</span>
                    <span class="text-xl">{{ $icon }}</span>
                </div>
                <p class="mt-2 text-2xl font-extrabold {{ $color }}">{{ $value }}</p>
            </div>
        @endforeach
    </div>

    <div class="mt-6 grid gap-6 lg:grid-cols-2">
        {{-- Recent sales --}}
        <div class="card p-5">
            <div class="mb-4 flex items-center justify-between">
                <h2 class="font-bold text-neutral-900">Vendas recentes</h2>
                <a href="{{ route('seller.sales') }}" class="text-sm font-semibold text-brand-700 hover:underline">Ver todas</a>
            </div>
            @forelse($recentSales as $sale)
                <div class="flex items-center gap-3 border-t border-neutral-100 py-3 first:border-0">
                    <img src="{{ $sale->image_path }}" alt="" class="h-12 w-12 rounded-lg object-cover">
                    <div class="min-w-0 flex-1">
                        <p class="truncate text-sm font-medium text-neutral-800">{{ $sale->title }}</p>
                        <p class="text-xs text-neutral-500">Pedido {{ $sale->order?->reference }} · {{ $sale->qty }}x</p>
                    </div>
                    <span class="font-bold text-brand-700">{{ money($sale->line_total) }}</span>
                </div>
            @empty
                <p class="py-6 text-center text-sm text-neutral-400">Nenhuma venda ainda. Divulgue sua loja! 🚀</p>
            @endforelse
        </div>

        {{-- Low stock --}}
        <div class="card p-5">
            <h2 class="mb-4 font-bold text-neutral-900">Estoque baixo</h2>
            @forelse($lowStock as $product)
                <div class="flex items-center gap-3 border-t border-neutral-100 py-3 first:border-0">
                    <img src="{{ $product->primary_image_url }}" alt="" class="h-12 w-12 rounded-lg object-cover">
                    <div class="min-w-0 flex-1">
                        <a href="{{ route('seller.products.edit', $product) }}" class="truncate text-sm font-medium text-neutral-800 hover:text-brand-700">{{ $product->title }}</a>
                        <p class="text-xs text-neutral-500">{{ money($product->price) }}</p>
                    </div>
                    <span class="chip {{ $product->stock <= 1 ? 'bg-amber-100 text-amber-700' : 'bg-neutral-100 text-neutral-600' }}">{{ $product->stock }} un.</span>
                </div>
            @empty
                <p class="py-6 text-center text-sm text-neutral-400">Cadastre produtos para acompanhar o estoque.</p>
            @endforelse
        </div>
    </div>
@endsection
