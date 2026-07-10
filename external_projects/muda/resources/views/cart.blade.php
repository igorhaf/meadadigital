@extends('layouts.app')

@section('title', 'Meu carrinho')

@section('content')
    @php($cartPageProps = [
        'checkoutUrl' => route('checkout.store'),
        'loginUrl' => route('login'),
        'quoteUrl' => route('shipping.quote'),
        'csrf' => csrf_token(),
        'authenticated' => auth()->check(),
        'buyer' => auth()->check() ? ['name' => auth()->user()->name, 'email' => auth()->user()->email] : ['name' => '', 'email' => ''],
        'checkoutLabel' => app(\App\Services\MercadoPagoService::class)->enabled() ? 'Ir para o pagamento' : 'Finalizar compra',
    ])
    <div class="container-muda py-8">
        <div data-island="CartPage" data-props='@json($cartPageProps)'>
            {{-- SSR fallback while the island mounts --}}
            <div class="flex items-center justify-center py-16 text-neutral-400">
                <svg class="h-6 w-6 animate-spin" fill="none" viewBox="0 0 24 24"><circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"/><path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 0 1 8-8v4a4 4 0 0 0-4 4H4z"/></svg>
                <span class="ml-3">Carregando seu carrinho…</span>
            </div>
        </div>
    </div>

    @include('partials.product-carousel', [
        'title' => 'Aproveite e leve também',
        'products' => $suggestions,
    ])
@endsection
