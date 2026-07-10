@extends('layouts.dashboard')

@section('title', 'Editar banner')

@section('content')
    <div class="mx-auto max-w-2xl">
        <a href="{{ route('admin.banners.index') }}" class="text-sm text-neutral-500 hover:text-brand-700">← Voltar aos banners</a>
        <h1 class="mb-6 mt-1 text-2xl font-extrabold text-neutral-900">Editar banner</h1>
    </div>
    @include('admin.banners._form', ['action' => route('admin.banners.update', $banner), 'method' => 'PUT', 'submitLabel' => 'Salvar alterações'])
@endsection
