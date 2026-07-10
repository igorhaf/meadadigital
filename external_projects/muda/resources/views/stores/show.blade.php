@extends('layouts.app')

@section('title', $user->store_name)
@section('description', $user->store_bio ?? "Produtos de brechó da loja {$user->store_name} no Muda.")

@section('content')
    {{-- Store header --}}
    <div class="bg-gradient-to-br from-brand-600 to-brand-800 text-white">
        <div class="container-muda flex flex-col items-center gap-4 py-10 text-center sm:flex-row sm:text-left">
            <div class="flex h-20 w-20 items-center justify-center rounded-2xl bg-white/15 text-3xl font-extrabold backdrop-blur">{{ $user->initial }}</div>
            <div class="flex-1">
                <h1 class="text-3xl font-extrabold">{{ $user->store_name }}</h1>
                @if($user->store_location)<p class="text-white/80">📍 {{ $user->store_location }}</p>@endif
                @if($user->store_bio)<p class="mt-2 max-w-2xl text-sm text-white/90">{{ $user->store_bio }}</p>@endif
            </div>
            <span class="chip bg-white/15 text-white backdrop-blur">✓ Vendedor verificado</span>
        </div>
    </div>

    <div class="container-muda py-8">
        <h2 class="mb-5 text-xl font-extrabold text-neutral-900">Produtos ({{ $products->total() }})</h2>

        @if($products->isEmpty())
            <div class="card flex flex-col items-center justify-center py-16 text-center">
                <div class="text-5xl">🏪</div>
                <p class="mt-4 font-semibold text-neutral-700">Esta loja ainda não tem produtos ativos.</p>
            </div>
        @else
            <div class="grid grid-cols-2 gap-4 sm:grid-cols-3 lg:grid-cols-4">
                @foreach($products as $product)
                    @include('partials.product-card', ['product' => $product])
                @endforeach
            </div>
            <div class="mt-8">{{ $products->onEachSide(1)->links() }}</div>
        @endif
    </div>
@endsection
