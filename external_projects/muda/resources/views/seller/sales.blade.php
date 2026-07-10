@extends('layouts.dashboard')

@section('title', 'Minhas vendas')

@section('content')
    <h1 class="mb-6 text-2xl font-extrabold text-neutral-900">Minhas vendas</h1>

    <div class="mb-6 grid grid-cols-3 gap-4">
        @foreach([
            ['Pedidos', $totals['orders'], '🧾'],
            ['Unidades', $totals['units'], '📦'],
            ['Faturamento', money($totals['revenue']), '💰'],
        ] as [$label, $value, $icon])
            <div class="card p-5">
                <div class="flex items-center justify-between"><span class="text-sm text-neutral-500">{{ $label }}</span><span class="text-xl">{{ $icon }}</span></div>
                <p class="mt-2 text-2xl font-extrabold text-neutral-900">{{ $value }}</p>
            </div>
        @endforeach
    </div>

    @forelse($orders as $order)
        <div class="card mb-4 overflow-hidden">
            <div class="flex flex-wrap items-center justify-between gap-2 border-b border-neutral-100 bg-neutral-50 px-5 py-3 text-sm">
                <div class="flex items-center gap-3">
                    <span class="font-bold text-neutral-800">{{ $order->reference }}</span>
                    <span class="chip {{ $order->status === 'delivered' ? 'bg-brand-50 text-brand-700' : ($order->status === 'cancelled' ? 'bg-red-50 text-red-600' : 'bg-amber-50 text-amber-700') }}">{{ $order->status_label }}</span>
                </div>
                <span class="text-neutral-500">{{ $order->created_at->format('d/m/Y') }} · {{ $order->buyer_name }}</span>
            </div>
            <div class="divide-y divide-neutral-100">
                @foreach($order->items as $item)
                    <div class="flex items-center gap-3 px-5 py-3">
                        <img src="{{ $item->image_path }}" alt="" class="h-12 w-12 rounded-lg object-cover">
                        <div class="min-w-0 flex-1">
                            <p class="truncate text-sm font-medium text-neutral-800">{{ $item->title }}</p>
                            <p class="text-xs text-neutral-500">{{ $item->qty }}x · {{ money($item->price) }}</p>
                        </div>
                        <span class="font-bold text-brand-700">{{ money($item->line_total) }}</span>
                    </div>
                @endforeach
            </div>
        </div>
    @empty
        <div class="card flex flex-col items-center justify-center py-16 text-center">
            <div class="text-5xl">🛍️</div>
            <p class="mt-4 font-semibold text-neutral-700">Nenhuma venda ainda</p>
            <p class="mt-1 text-sm text-neutral-500">Quando alguém comprar seus produtos, os pedidos aparecem aqui.</p>
        </div>
    @endforelse

    <div class="mt-6">{{ $orders->onEachSide(1)->links() }}</div>
@endsection
