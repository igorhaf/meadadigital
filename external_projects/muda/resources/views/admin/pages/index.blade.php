@extends('layouts.dashboard')

@section('title', 'Páginas')

@section('content')
    <h1 class="mb-1 text-2xl font-extrabold text-neutral-900">Páginas institucionais</h1>
    <p class="mb-6 text-sm text-neutral-500">Edite o conteúdo exibido no rodapé da loja.</p>

    <div class="card divide-y divide-neutral-100">
        @foreach($pages as $page)
            <div class="flex items-center justify-between p-4">
                <div>
                    <p class="font-semibold text-neutral-800">{{ $page->title }}</p>
                    <p class="text-xs text-neutral-500">/{{ $page->slug }} · atualizada {{ $page->updated_at?->diffForHumans() }}</p>
                </div>
                <div class="flex gap-2">
                    <a href="{{ url($page->slug) }}" target="_blank" class="rounded-lg px-3 py-1.5 text-xs font-medium text-neutral-500 hover:bg-neutral-100">Ver ↗</a>
                    <a href="{{ route('admin.pages.edit', $page) }}" class="rounded-lg px-3 py-1.5 text-xs font-semibold text-brand-700 hover:bg-brand-50">Editar</a>
                </div>
            </div>
        @endforeach
    </div>
@endsection
