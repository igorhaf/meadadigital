@extends('layouts.app')

@section('title', 'Venda no Muda')

@section('content')
    <div class="container-muda py-12">
        <div class="mx-auto max-w-xl">
            <div class="mb-6 text-center">
                <span class="text-5xl">🏪</span>
                <h1 class="mt-3 text-3xl font-extrabold text-neutral-900">Abra sua loja no Muda</h1>
                <p class="mt-2 text-neutral-500">Desapegue com propósito. Cadastre seus produtos e alcance quem ama garimpar.</p>
            </div>

            @if ($errors->any())
                <div class="mb-4 rounded-xl bg-red-50 px-4 py-3 text-sm text-red-700">{{ $errors->first() }}</div>
            @endif

            <form method="POST" action="{{ route('sell.store') }}" class="card space-y-4 p-6">
                @csrf
                <div>
                    <label class="mb-1 block text-sm font-medium text-neutral-700">Nome da sua loja *</label>
                    <input name="store_name" value="{{ old('store_name') }}" required autofocus placeholder="Ex.: Brechó da Ana"
                        class="w-full rounded-xl border border-neutral-300 px-4 py-2.5 text-sm focus:border-brand-400 focus:outline-none focus:ring-2 focus:ring-brand-100">
                </div>
                <div>
                    <label class="mb-1 block text-sm font-medium text-neutral-700">Localização</label>
                    <input name="store_location" value="{{ old('store_location') }}" placeholder="Cidade, UF"
                        class="w-full rounded-xl border border-neutral-300 px-4 py-2.5 text-sm focus:border-brand-400 focus:outline-none">
                </div>
                <div>
                    <label class="mb-1 block text-sm font-medium text-neutral-700">Sobre a loja</label>
                    <textarea name="store_bio" rows="3" placeholder="Conte sua história de moda circular…"
                        class="w-full rounded-xl border border-neutral-300 px-4 py-2.5 text-sm focus:border-brand-400 focus:outline-none">{{ old('store_bio') }}</textarea>
                </div>
                <button class="btn-brand w-full">Criar minha loja</button>
            </form>
        </div>
    </div>
@endsection
