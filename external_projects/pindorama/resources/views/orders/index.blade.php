@extends('layouts.app')

@section('title', 'Meus pedidos')

@section('content')
    <div class="container-site py-8">
        <h1 class="mb-6 text-2xl font-extrabold text-neutral-900">Meus pedidos</h1>

        @forelse($orders as $order)
            <a href="{{ route('orders.show', $order) }}" class="card mb-4 block overflow-hidden transition hover:border-brand-300">
                <div class="flex flex-wrap items-center justify-between gap-2 border-b border-neutral-100 bg-neutral-50 px-5 py-3 text-sm">
                    <div class="flex items-center gap-2">
                        <span class="font-bold text-neutral-800">{{ $order->reference }}</span>
                        @include('partials.payment-badge')
                        <span class="chip {{ $order->status === 'delivered' ? 'bg-brand-50 text-brand-700' : ($order->status === 'cancelled' ? 'bg-red-50 text-red-600' : ($order->status === 'paid' ? 'bg-sky-50 text-sky-700' : 'bg-amber-50 text-amber-700')) }}">{{ $order->status_label }}</span>
                    </div>
                    <span class="text-neutral-500">{{ $order->created_at->format('d/m/Y') }}</span>
                </div>
                <div class="flex items-center gap-4 p-5">
                    <div class="flex -space-x-3">
                        @foreach($order->items->take(4) as $item)
                            <img src="{{ $item->image_path }}" alt="" class="h-12 w-12 rounded-lg border-2 border-white object-cover">
                        @endforeach
                    </div>
                    <div class="flex-1">
                        <p class="text-sm text-neutral-600">{{ $order->items->count() }} item(s)</p>
                    </div>
                    <div class="text-right">
                        <p class="text-lg font-extrabold text-neutral-900">{{ money($order->total) }}</p>
                        <span class="text-sm text-brand-700">Ver detalhes →</span>
                    </div>
                </div>
            </a>
        @empty
            <div class="card flex flex-col items-center justify-center py-16 text-center">
                <div class="text-5xl">🧾</div>
                <p class="mt-4 font-semibold text-neutral-700">Você ainda não fez pedidos</p>
                <a href="{{ route('home') }}" class="btn-brand mt-6">Explorar terapias</a>
            </div>
        @endforelse

        <div class="mt-6">{{ $orders->onEachSide(1)->links() }}</div>
    </div>
@endsection
