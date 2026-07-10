@extends('layouts.dashboard')

@section('title', 'Frete')

@section('content')
    <h1 class="mb-1 text-2xl font-extrabold text-neutral-900">Frete</h1>
    <p class="mb-6 text-sm text-neutral-500">Integração com o Melhor Envio (agrega Correios, Jadlog, Loggi e outras).</p>

    <div class="card max-w-2xl p-6">
        <div class="flex items-center justify-between">
            <div class="flex items-center gap-3">
                <span class="flex h-11 w-11 items-center justify-center rounded-xl bg-brand-100 text-2xl">🚚</span>
                <div>
                    <p class="font-bold text-neutral-900">Melhor Envio {{ $sandbox ? '· sandbox' : '· produção' }}</p>
                    <p class="text-sm text-neutral-500">CEP de origem: {{ $origin }}</p>
                </div>
            </div>
            @if($connected)
                <span class="chip bg-brand-50 text-brand-700">● Conectado</span>
            @elseif($configured)
                <span class="chip bg-amber-50 text-amber-700">● Não conectado</span>
            @else
                <span class="chip bg-red-50 text-red-600">● Sem credenciais</span>
            @endif
        </div>

        @if(! $configured)
            <div class="mt-4 rounded-xl bg-red-50 p-4 text-sm text-red-700">
                Defina <code>MELHORENVIO_CLIENT_ID</code>, <code>MELHORENVIO_CLIENT_SECRET</code> e
                <code>MELHORENVIO_REDIRECT_URI</code> no <code>.env</code> e rode
                <code>php artisan config:clear</code>.
            </div>
        @elseif($connected)
            <div class="mt-4 rounded-xl bg-brand-50 p-4 text-sm text-brand-800">
                Tudo certo! As cotações no carrinho e na página do produto já usam as transportadoras reais.
            </div>
            <form method="POST" action="{{ route('admin.shipping.disconnect') }}" class="mt-4" onsubmit="return confirm('Desconectar o Melhor Envio?')">
                @csrf
                <button class="btn-outline">Desconectar</button>
            </form>
        @else
            <div class="mt-4 rounded-xl bg-amber-50 p-4 text-sm text-amber-800">
                <p>Clique abaixo para autorizar a Muda no Melhor Envio (OAuth).</p>
                <p class="mt-1 text-xs">Redirect cadastrado: <code>{{ $redirect }}</code> — precisa bater com a URL de callback do app no painel do Melhor Envio.</p>
            </div>
            <a href="{{ route('admin.shipping.connect') }}" class="btn-brand mt-4">Conectar Melhor Envio</a>
        @endif

        <p class="mt-4 text-xs text-neutral-400">Enquanto não conectado, a loja mostra uma estimativa (ViaCEP + peso) para não travar o checkout.</p>
    </div>
@endsection
