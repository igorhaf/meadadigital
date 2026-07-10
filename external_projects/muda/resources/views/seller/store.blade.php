@extends('layouts.dashboard')

@section('title', 'Minha loja')

@section('content')
    <div class="mx-auto max-w-2xl">
        <h1 class="mb-1 text-2xl font-extrabold text-neutral-900">Minha loja</h1>
        <p class="mb-6 text-sm text-neutral-500">
            Estas informações aparecem na sua vitrine pública
            @if($user->store_url)· <a href="{{ $user->store_url }}" class="text-brand-700 hover:underline">{{ $user->store_url }}</a>@endif
        </p>

        @if ($errors->any())
            <div class="mb-4 rounded-xl bg-red-50 px-4 py-3 text-sm text-red-700">{{ $errors->first() }}</div>
        @endif

        <form method="POST" action="{{ route('seller.store.update') }}" class="card space-y-4 p-6">
            @csrf @method('PUT')
            <div class="flex items-center gap-4">
                <div class="flex h-16 w-16 items-center justify-center rounded-2xl bg-brand-100 text-2xl font-bold text-brand-700">{{ $user->initial }}</div>
                <div class="flex-1">
                    <label class="mb-1 block text-sm font-medium text-neutral-700">Nome da loja *</label>
                    <input name="store_name" value="{{ old('store_name', $user->store_name) }}" required
                        class="w-full rounded-xl border border-neutral-300 px-4 py-2.5 text-sm focus:border-brand-400 focus:outline-none focus:ring-2 focus:ring-brand-100">
                </div>
            </div>
            <div>
                <label class="mb-1 block text-sm font-medium text-neutral-700">Localização</label>
                <input name="store_location" value="{{ old('store_location', $user->store_location) }}" placeholder="Cidade, UF"
                    class="w-full rounded-xl border border-neutral-300 px-4 py-2.5 text-sm focus:border-brand-400 focus:outline-none">
            </div>
            <div>
                <label class="mb-1 block text-sm font-medium text-neutral-700">Sobre a loja</label>
                <textarea name="store_bio" rows="3" placeholder="Conte um pouco sobre seu brechó…"
                    class="w-full rounded-xl border border-neutral-300 px-4 py-2.5 text-sm focus:border-brand-400 focus:outline-none">{{ old('store_bio', $user->store_bio) }}</textarea>
            </div>
            <button class="btn-brand">Salvar loja</button>
        </form>
    </div>
@endsection
