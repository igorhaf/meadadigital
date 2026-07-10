@extends('layouts.app')

@section('title', $page->title)
@section('description', \Illuminate\Support\Str::limit(strip_tags($page->body), 150))

@section('content')
    <div class="container-muda py-10">
        <nav class="mb-4 text-sm text-neutral-500">
            <a href="{{ route('home') }}" class="hover:text-brand-700">Início</a>
            <span>/</span>
            <span class="font-medium text-neutral-700">{{ $page->title }}</span>
        </nav>

        <div class="mx-auto max-w-3xl">
            <h1 class="text-3xl font-extrabold text-neutral-900">{{ $page->title }}</h1>
            <div class="mt-6 whitespace-pre-line leading-relaxed text-neutral-700">{{ $page->body }}</div>

            <div class="mt-10 rounded-2xl bg-brand-50 p-6 text-sm text-brand-800">
                Ainda com dúvidas? <a href="{{ route('contact.show') }}" class="font-semibold underline">Fale com a gente</a>.
            </div>
        </div>
    </div>
@endsection
