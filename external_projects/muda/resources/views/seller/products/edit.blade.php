@extends('layouts.dashboard')

@section('title', 'Editar produto')

@section('content')
    <div class="mb-6 flex items-center justify-between">
        <div>
            <a href="{{ route('seller.products.index') }}" class="text-sm text-neutral-500 hover:text-brand-700">← Voltar aos produtos</a>
            <h1 class="mt-1 text-2xl font-extrabold text-neutral-900">Editar produto</h1>
        </div>
        <a href="{{ $product->url }}" class="btn-outline">Ver na loja ↗</a>
    </div>

    @include('seller.products._form', [
        'action' => route('seller.products.update', $product),
        'method' => 'PUT',
        'submitLabel' => 'Salvar alterações',
    ])
@endsection
