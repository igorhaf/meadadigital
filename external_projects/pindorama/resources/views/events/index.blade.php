@extends('layouts.app')

@section('title', 'Eventos')

@section('content')
<div class="container-site py-10">
    <h1 class="mb-2 text-3xl font-extrabold text-neutral-900">Eventos & experiências</h1>
    <p class="mb-8 text-neutral-500">Rodas de terapia, cursos presenciais e certificações.</p>

    @if($events->isEmpty())
        <div class="card p-10 text-center text-neutral-500">Nenhum evento no momento.</div>
    @else
        <div class="grid gap-5 sm:grid-cols-2 lg:grid-cols-3">
            @foreach($events as $event)
                <a href="{{ $event->url }}" class="card overflow-hidden transition hover:shadow-md">
                    <img src="{{ $event->cover_url }}" alt="" class="aspect-video w-full object-cover">
                    <div class="p-4">
                        <span class="chip bg-brand-50 text-brand-700">{{ $event->type_label }}</span>
                        <h2 class="mt-2 font-bold text-neutral-900">{{ $event->title }}</h2>
                        <p class="mt-1 text-sm text-neutral-500">🗓️ {{ $event->starts_at->setTimezone($event->timezone)->format('d/m/Y H:i') }}</p>
                        <p class="text-sm text-neutral-500">{{ $event->professional?->display_name }}</p>
                        <div class="mt-3 flex items-center justify-between">
                            <span class="font-extrabold text-neutral-900">{{ $event->is_free ? 'Gratuito' : money($event->price) }}</span>
                            @if($event->capacity > 0)<span class="text-xs text-neutral-400">{{ max(0, $event->capacity - $event->taken) }} vagas</span>@endif
                        </div>
                    </div>
                </a>
            @endforeach
        </div>
        <div class="mt-8">{{ $events->links() }}</div>
    @endif
</div>
@endsection
