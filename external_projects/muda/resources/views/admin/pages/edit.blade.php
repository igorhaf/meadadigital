@extends('layouts.dashboard')

@section('title', 'Editar página')

@section('content')
    <div class="mx-auto max-w-3xl">
        <a href="{{ route('admin.pages.index') }}" class="text-sm text-neutral-500 hover:text-brand-700">← Voltar às páginas</a>
        <h1 class="mb-6 mt-1 text-2xl font-extrabold text-neutral-900">Editar: {{ $page->title }}</h1>

        @if ($errors->any())
            <div class="mb-4 rounded-xl bg-red-50 px-4 py-3 text-sm text-red-700">{{ $errors->first() }}</div>
        @endif

        <form method="POST" action="{{ route('admin.pages.update', $page) }}" class="card space-y-4 p-6">
            @csrf @method('PUT')
            <div>
                <label class="mb-1 block text-sm font-medium text-neutral-700">Título</label>
                <input name="title" value="{{ old('title', $page->title) }}" required class="w-full rounded-xl border border-neutral-300 px-4 py-2.5 text-sm focus:border-brand-400 focus:outline-none">
            </div>
            <div>
                <label class="mb-1 block text-sm font-medium text-neutral-700">Conteúdo</label>
                <textarea name="body" rows="16" class="w-full rounded-xl border border-neutral-300 px-4 py-2.5 text-sm leading-relaxed focus:border-brand-400 focus:outline-none">{{ old('body', $page->body) }}</textarea>
                <p class="mt-1 text-xs text-neutral-400">As quebras de linha são preservadas na página pública.</p>
            </div>
            <div class="flex gap-2">
                <button class="btn-brand">Salvar página</button>
                <a href="{{ route('admin.pages.index') }}" class="btn-outline">Cancelar</a>
            </div>
        </form>
    </div>
@endsection
