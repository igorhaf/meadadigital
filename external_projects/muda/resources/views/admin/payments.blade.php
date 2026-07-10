@extends('layouts.dashboard')

@section('title', 'Pagamentos')

@section('content')
    <h1 class="mb-1 text-2xl font-extrabold text-neutral-900">Pagamentos</h1>
    <p class="mb-6 text-sm text-neutral-500">Integração com Mercado Pago (Checkout Pro) e status dos pedidos.</p>

    {{-- Config status --}}
    <div class="card mb-6 p-6">
        <div class="flex flex-wrap items-center justify-between gap-3">
            <div class="flex items-center gap-3">
                <span class="flex h-11 w-11 items-center justify-center rounded-xl bg-sky-100 text-2xl">💳</span>
                <div>
                    <p class="font-bold text-neutral-900">Mercado Pago · Checkout Pro</p>
                    @if($config['enabled'])
                        <p class="text-sm text-neutral-500">Ambiente: <strong>{{ $config['environment'] }}</strong> · Public key: <code class="text-xs">{{ $config['public_key'] }}</code></p>
                    @else
                        <p class="text-sm text-neutral-500">Ainda não configurado.</p>
                    @endif
                </div>
            </div>
            @if($config['enabled'])
                <span class="chip bg-brand-50 text-brand-700">● Ativo{{ $config['sandbox'] ? ' (sandbox)' : '' }}</span>
            @else
                <span class="chip bg-amber-50 text-amber-700">● Inativo</span>
            @endif
        </div>

        @unless($config['enabled'])
            <div class="mt-4 rounded-xl bg-amber-50 p-4 text-sm text-amber-800">
                <p class="font-semibold">Como ativar</p>
                <ol class="mt-1 list-decimal space-y-0.5 pl-5">
                    <li>Crie uma aplicação em <a class="underline" href="https://www.mercadopago.com.br/developers/panel/app" target="_blank">mercadopago.com.br/developers</a>.</li>
                    <li>Copie as credenciais de <strong>Teste</strong> (Access Token e Public Key).</li>
                    <li>No <code>.env</code>: defina <code>MP_ENABLED=true</code>, <code>MP_ACCESS_TOKEN</code>, <code>MP_PUBLIC_KEY</code>.</li>
                    <li>Para webhook/retorno em dev, aponte <code>MP_BACK_URL_BASE</code> para um túnel público (ngrok).</li>
                </ol>
                <p class="mt-2">Enquanto desativado, o checkout usa um <strong>pagamento simulado</strong> para a demo funcionar.</p>
            </div>
        @endunless
    </div>

    {{-- Stats --}}
    <div class="mb-6 grid grid-cols-2 gap-4 lg:grid-cols-4">
        @foreach([
            ['Aprovados', $totals['approved'], '✅'],
            ['Pendentes', $totals['pending'], '⏳'],
            ['Recusados', $totals['rejected'], '❌'],
            ['Receita aprovada', money($totals['revenue']), '💰'],
        ] as [$label, $value, $icon])
            <div class="card p-4">
                <div class="flex items-center justify-between"><span class="text-xs text-neutral-500">{{ $label }}</span><span>{{ $icon }}</span></div>
                <p class="mt-1 text-xl font-extrabold text-neutral-900">{{ $value }}</p>
            </div>
        @endforeach
    </div>

    {{-- Orders --}}
    <div class="card overflow-hidden">
        <div class="overflow-x-auto">
            <table class="w-full text-sm">
                <thead class="border-b border-neutral-200 bg-neutral-50 text-left text-xs uppercase tracking-wide text-neutral-500">
                    <tr>
                        <th class="px-4 py-3">Pedido</th>
                        <th class="px-4 py-3">Cliente</th>
                        <th class="px-4 py-3">Total</th>
                        <th class="px-4 py-3">Método</th>
                        <th class="px-4 py-3">Pagamento</th>
                        <th class="px-4 py-3">Data</th>
                    </tr>
                </thead>
                <tbody class="divide-y divide-neutral-100">
                    @foreach($orders as $order)
                        <tr class="hover:bg-neutral-50">
                            <td class="px-4 py-3"><a href="{{ route('orders.show', $order) }}" class="font-medium text-brand-700 hover:underline">{{ $order->reference }}</a></td>
                            <td class="px-4 py-3 text-neutral-700">{{ $order->buyer_name }}</td>
                            <td class="px-4 py-3 font-semibold text-neutral-900">{{ money($order->total) }}</td>
                            <td class="px-4 py-3 text-neutral-500">{{ $order->payment_method === 'mercadopago' ? 'Mercado Pago' : 'Simulado' }}</td>
                            <td class="px-4 py-3">@include('partials.payment-badge')</td>
                            <td class="px-4 py-3 text-neutral-500">{{ $order->created_at->format('d/m/Y') }}</td>
                        </tr>
                    @endforeach
                </tbody>
            </table>
        </div>
    </div>

    <div class="mt-6">{{ $orders->onEachSide(1)->links() }}</div>
@endsection
