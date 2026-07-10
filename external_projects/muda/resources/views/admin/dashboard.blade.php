@extends('layouts.dashboard')

@section('title', 'Administração')

@section('content')
    <div class="mb-6">
        <span class="chip bg-amber-100 text-amber-700">Administração do site</span>
        <h1 class="mt-2 text-2xl font-extrabold text-neutral-900">Visão geral do marketplace</h1>
    </div>

    <div class="grid grid-cols-2 gap-4 lg:grid-cols-3 xl:grid-cols-6">
        @foreach([
            ['Produtos', $stats['products'], '📦'],
            ['Ativos', $stats['active'], '✅'],
            ['Lojistas', $stats['sellers'], '🏪'],
            ['Clientes', $stats['customers'], '👥'],
            ['Pedidos', $stats['orders'], '🧾'],
            ['Faturamento', money($stats['revenue']), '💰'],
        ] as [$label, $value, $icon])
            <div class="card p-4">
                <div class="flex items-center justify-between"><span class="text-xs text-neutral-500">{{ $label }}</span><span>{{ $icon }}</span></div>
                <p class="mt-1 text-xl font-extrabold text-neutral-900">{{ $value }}</p>
            </div>
        @endforeach
    </div>

    <div class="mt-6 grid gap-6 lg:grid-cols-2">
        <div class="card p-5">
            <h2 class="mb-4 font-bold text-neutral-900">Pedidos recentes</h2>
            @forelse($recentOrders as $order)
                <div class="flex items-center justify-between border-t border-neutral-100 py-3 text-sm first:border-0">
                    <div>
                        <div class="flex items-center gap-2">
                            <a href="{{ route('orders.show', $order) }}" class="font-medium text-neutral-800 hover:text-brand-700">{{ $order->reference }}</a>
                            @include('partials.payment-badge')
                        </div>
                        <p class="text-xs text-neutral-500">{{ $order->buyer_name }} · {{ $order->items->count() }} item(s) · {{ $order->created_at->format('d/m/Y') }}</p>
                    </div>
                    <span class="font-bold text-brand-700">{{ money($order->total) }}</span>
                </div>
            @empty
                <p class="py-6 text-center text-sm text-neutral-400">Nenhum pedido ainda.</p>
            @endforelse
        </div>

        <div class="card p-5">
            <h2 class="mb-4 font-bold text-neutral-900">Top lojistas</h2>
            @foreach($topSellers as $seller)
                <div class="flex items-center gap-3 border-t border-neutral-100 py-3 first:border-0">
                    <div class="flex h-9 w-9 items-center justify-center rounded-full bg-brand-100 text-sm font-bold text-brand-700">{{ $seller->initial }}</div>
                    <div class="min-w-0 flex-1">
                        <p class="truncate text-sm font-medium text-neutral-800">{{ $seller->store_name }}</p>
                        <p class="text-xs text-neutral-500">{{ $seller->products_count }} produtos</p>
                    </div>
                    <span class="text-sm font-bold text-neutral-700">{{ money($seller->revenue ?? 0) }}</span>
                </div>
            @endforeach
        </div>
    </div>
@endsection
