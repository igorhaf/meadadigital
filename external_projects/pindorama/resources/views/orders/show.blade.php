@extends('layouts.app')

@section('title', 'Pedido ' . $order->reference)

@section('content')
    <div class="container-site py-8">
        @if(session('placed') || request('pago'))
            @if($order->isPaid())
                <div class="mb-6 flex items-center gap-4 rounded-2xl border border-brand-200 bg-brand-50 p-6">
                    <span class="text-4xl">🌱</span>
                    <div>
                        <h2 class="text-lg font-extrabold text-neutral-900">Pagamento confirmado!</h2>
                        <p class="text-sm text-neutral-600">Obrigado por dar uma nova vida a essas peças. Seu pedido já está sendo preparado.</p>
                    </div>
                </div>
            @else
                <div class="mb-6 flex items-center gap-4 rounded-2xl border border-amber-200 bg-amber-50 p-6">
                    <span class="text-4xl">⏳</span>
                    <div>
                        <h2 class="text-lg font-extrabold text-neutral-900">Pedido criado — aguardando pagamento</h2>
                        <p class="text-sm text-neutral-600">Assim que o pagamento for aprovado, seu pedido é liberado.</p>
                    </div>
                </div>
            @endif
            {{-- Clear the client-side cart after a successful checkout --}}
            <script>try { localStorage.removeItem('pindorama.cart.v1'); } catch (e) {}</script>
        @endif

        @if(session('status'))
            <div class="mb-6 rounded-xl bg-amber-50 px-4 py-3 text-sm text-amber-800">{{ session('status') }}</div>
        @endif

        <a href="{{ route('orders.index') }}" class="text-sm text-neutral-500 hover:text-brand-700">← Meus pedidos</a>

        <div class="mt-2 mb-6 flex flex-wrap items-center justify-between gap-2">
            <h1 class="text-2xl font-extrabold text-neutral-900">Pedido {{ $order->reference }}</h1>
            <div class="flex items-center gap-2">
                <span class="text-xs text-neutral-400">Pagamento:</span>
                @include('partials.payment-badge')
                <span class="chip {{ $order->status === 'delivered' ? 'bg-brand-50 text-brand-700' : ($order->status === 'cancelled' ? 'bg-red-50 text-red-600' : ($order->status === 'paid' ? 'bg-sky-50 text-sky-700' : 'bg-amber-50 text-amber-700')) }}">{{ $order->status_label }}</span>
            </div>
        </div>

        @unless($order->isPaid() || $order->status === 'cancelled')
            <div class="mb-6 flex flex-wrap items-center justify-between gap-3 rounded-2xl border border-amber-200 bg-amber-50 p-4">
                <p class="text-sm text-amber-800">Este pedido ainda não foi pago.</p>
                <form method="POST" action="{{ route('orders.retry', $order) }}">
                    @csrf
                    <button class="btn-brand !py-2 text-sm">Pagar agora</button>
                </form>
            </div>
        @endunless

        <div class="grid gap-6 lg:grid-cols-3">
            <div class="card divide-y divide-neutral-100 lg:col-span-2">
                @foreach($order->items as $item)
                    <div class="flex items-center gap-4 p-4">
                        <img src="{{ $item->image_path }}" alt="" class="h-16 w-16 rounded-xl object-cover">
                        <div class="min-w-0 flex-1">
                            @if($item->product)
                                <a href="{{ $item->product->url }}" class="font-medium text-neutral-800 hover:text-brand-700">{{ $item->title }}</a>
                            @else
                                <p class="font-medium text-neutral-800">{{ $item->title }}</p>
                            @endif
                            <p class="text-sm text-neutral-500">{{ $item->qty }}x · {{ money($item->price) }}</p>
                        </div>
                        <span class="font-bold text-neutral-900">{{ money($item->line_total) }}</span>
                    </div>
                @endforeach
            </div>

            <div class="space-y-4">
                <div class="card p-5">
                    <h2 class="mb-3 font-bold text-neutral-900">Resumo</h2>
                    <dl class="space-y-2 text-sm">
                        <div class="flex justify-between"><dt class="text-neutral-500">Subtotal</dt><dd class="font-semibold">{{ money($order->subtotal) }}</dd></div>
                        @php
                            $freteInfo = null;
                            if ($order->shipping_service) {
                                $freteInfo = trim(($order->shipping_carrier ?? '') . ' ' . $order->shipping_service);
                                if ($order->shipping_days) {
                                    $freteInfo .= ' · até ' . $order->shipping_days . ' dia(s)';
                                }
                            }
                        @endphp
                        <div class="flex justify-between">
                            <dt class="text-neutral-500">
                                Frete
                                @if($freteInfo)<span class="block text-xs text-neutral-400">{{ $freteInfo }}</span>@endif
                            </dt>
                            <dd class="font-semibold {{ (float) $order->shipping === 0.0 ? 'text-brand-600' : '' }}">{{ (float) $order->shipping === 0.0 ? 'Grátis' : money($order->shipping) }}</dd>
                        </div>
                        <div class="flex justify-between border-t border-neutral-100 pt-2 text-base"><dt class="font-medium text-neutral-600">Total</dt><dd class="font-extrabold text-neutral-900">{{ money($order->total) }}</dd></div>
                    </dl>
                </div>
                <div class="card p-5 text-sm">
                    <h2 class="mb-3 font-bold text-neutral-900">Entrega</h2>
                    <p class="text-neutral-700">{{ $order->buyer_name }}</p>
                    <p class="text-neutral-500">{{ $order->buyer_email }}</p>
                    @if($order->buyer_phone)<p class="text-neutral-500">{{ $order->buyer_phone }}</p>@endif
                    @if($order->shipping_address)<p class="mt-2 text-neutral-500">{{ $order->shipping_address }}</p>@endif
                </div>
            </div>
        </div>
    </div>
@endsection
