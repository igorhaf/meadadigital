@extends('layouts.app')

@section('title', 'Pagamento')

@section('content')
    <div class="container-muda py-8">
        <div class="mx-auto max-w-4xl">
            <a href="{{ route('cart') }}" class="text-sm text-neutral-500 hover:text-brand-700">← Voltar ao carrinho</a>
            <h1 class="mt-1 mb-6 text-2xl font-extrabold text-neutral-900">Pagamento seguro</h1>

            <div class="grid gap-6 lg:grid-cols-5">
                {{-- Payment Brick --}}
                <div class="lg:col-span-3">
                    <div id="paymentBrick_container"></div>
                    <div id="pay-loading" class="rounded-2xl border border-neutral-200 bg-white p-8 text-center text-sm text-neutral-400">Carregando formas de pagamento…</div>
                    <p id="pay-error" class="mt-3 hidden rounded-xl bg-red-50 px-4 py-3 text-sm text-red-700"></p>

                    {{-- Resultado PIX (QR code) --}}
                    <div id="pix-result" class="hidden rounded-2xl border border-neutral-200 bg-white p-6 text-center">
                        <h2 class="text-lg font-extrabold text-neutral-900">Pague com PIX</h2>
                        <p class="mt-1 text-sm text-neutral-500">Escaneie o QR code ou copie o código. Assim que o pagamento cair, seu pedido é liberado.</p>
                        <img id="pix-qr" src="" alt="QR code PIX" class="mx-auto mt-4 h-56 w-56 rounded-xl border border-neutral-100">
                        <div class="mt-4">
                            <label class="mb-1 block text-xs font-medium text-neutral-500">PIX copia e cola</label>
                            <div class="flex gap-2">
                                <input id="pix-code" readonly class="w-full rounded-lg border border-neutral-300 px-3 py-2 text-xs" value="">
                                <button type="button" id="pix-copy" class="btn-outline !px-4 !py-2 text-sm">Copiar</button>
                            </div>
                        </div>
                        <a id="pix-order-link" href="#" class="btn-brand mt-5 inline-flex">Ver meu pedido</a>
                    </div>
                </div>

                {{-- Order summary --}}
                <aside class="lg:col-span-2">
                    <div class="rounded-2xl border border-neutral-200 bg-white p-6">
                        <h2 class="font-bold text-neutral-900">Pedido {{ $order->reference }}</h2>
                        <div class="mt-4 space-y-3">
                            @foreach($order->items as $item)
                                <div class="flex items-center gap-3">
                                    <img src="{{ $item->image_path }}" alt="" class="h-12 w-12 rounded-lg object-cover">
                                    <div class="min-w-0 flex-1">
                                        <p class="truncate text-sm font-medium text-neutral-800">{{ $item->title }}</p>
                                        <p class="text-xs text-neutral-500">{{ $item->qty }}x</p>
                                    </div>
                                    <span class="text-sm font-semibold text-neutral-700">{{ money($item->line_total) }}</span>
                                </div>
                            @endforeach
                        </div>
                        <dl class="mt-4 space-y-1.5 border-t border-neutral-100 pt-4 text-sm">
                            <div class="flex justify-between"><dt class="text-neutral-500">Subtotal</dt><dd>{{ money($order->subtotal) }}</dd></div>
                            <div class="flex justify-between"><dt class="text-neutral-500">Frete</dt><dd>{{ (float) $order->shipping === 0.0 ? 'Grátis' : money($order->shipping) }}</dd></div>
                            <div class="flex justify-between border-t border-neutral-100 pt-2 text-base"><dt class="font-medium text-neutral-600">Total</dt><dd class="font-extrabold text-neutral-900">{{ money($order->total) }}</dd></div>
                        </dl>
                    </div>
                    <p class="mt-3 text-center text-xs text-neutral-400">🔒 Pagamento processado com Mercado Pago</p>
                </aside>
            </div>
        </div>
    </div>

    <script src="https://sdk.mercadopago.com/js/v2"></script>
    <script>
        const mp = new MercadoPago(@json($publicKey), { locale: 'pt-BR' });
        const errorEl = document.getElementById('pay-error');

        function showError(msg) {
            errorEl.textContent = msg;
            errorEl.classList.remove('hidden');
        }

        function showPix(data) {
            document.getElementById('paymentBrick_container').classList.add('hidden');
            document.getElementById('pix-qr').src = 'data:image/png;base64,' + data.qr_code_base64;
            document.getElementById('pix-code').value = data.qr_code || '';
            document.getElementById('pix-order-link').href = @json(route('orders.show', $order));
            document.getElementById('pix-result').classList.remove('hidden');
        }

        document.getElementById('pix-copy').addEventListener('click', () => {
            const el = document.getElementById('pix-code');
            el.select();
            navigator.clipboard?.writeText(el.value);
            document.getElementById('pix-copy').textContent = 'Copiado!';
        });

        mp.bricks().create('payment', 'paymentBrick_container', {
            initialization: {
                amount: {{ (float) $order->total }},
                payer: { email: @json($order->buyer_email) },
            },
            customization: {
                paymentMethods: {
                    creditCard: 'all',
                    debitCard: 'all',
                    bankTransfer: 'all', // PIX
                    maxInstallments: 12,
                },
            },
            callbacks: {
                onReady: () => {
                    document.getElementById('pay-loading')?.remove();
                },
                onError: (error) => {
                    console.error(error);
                    showError('Não foi possível carregar o formulário de pagamento.');
                },
                onSubmit: ({ formData }) => {
                    errorEl.classList.add('hidden');
                    return fetch(@json(route('payment.process', $order)), {
                        method: 'POST',
                        headers: {
                            'Content-Type': 'application/json',
                            'X-CSRF-TOKEN': @json(csrf_token()),
                            'Accept': 'application/json',
                        },
                        body: JSON.stringify(formData),
                    })
                        .then((r) => r.json().then((data) => ({ ok: r.ok, data })))
                        .then(({ ok, data }) => {
                            if (!ok) throw new Error(data.error || 'Pagamento não aprovado.');
                            try { localStorage.removeItem('muda.cart.v1'); } catch (e) {}
                            if (data.status === 'approved' || data.status === 'authorized') {
                                window.location.href = data.redirect;
                            } else if (data.qr_code_base64) {
                                showPix(data);          // PIX: mostra o QR na tela
                            } else if (data.ticket_url) {
                                window.location.href = data.ticket_url;   // boleto
                            } else {
                                window.location.href = data.redirect;     // demais pendentes
                            }
                        })
                        .catch((err) => {
                            showError(err.message || 'Falha ao processar o pagamento.');
                            throw err;
                        });
                },
            },
        });
    </script>
@endsection
