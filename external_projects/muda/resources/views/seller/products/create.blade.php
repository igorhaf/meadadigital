@extends('layouts.dashboard')

@section('title', 'Novo produto')

@section('content')
    <div class="mb-6">
        <a href="{{ route('seller.products.index') }}" class="text-sm text-neutral-500 hover:text-brand-700">← Voltar aos produtos</a>
        <h1 class="mt-1 text-2xl font-extrabold text-neutral-900">Cadastrar produto</h1>
    </div>

    @include('seller.products._form', [
        'action' => route('seller.products.store'),
        'submitLabel' => 'Publicar produto',
    ])
@endsection
