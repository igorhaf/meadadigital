@extends('layouts.app')

@section('title', $event->title)

@section('content')
@php($start = $event->starts_at->setTimezone($event->timezone))
<div class="container-site max-w-3xl py-10">
    <a href="{{ route('events.index') }}" class="text-sm text-neutral-500 hover:underline">← Eventos</a>

    @if(session('error'))<div class="mt-4 rounded-xl bg-red-50 px-4 py-3 text-sm text-red-700">{{ session('error') }}</div>@endif

    <div class="card mt-4 overflow-hidden">
        <img src="{{ $event->cover_url }}" alt="" class="aspect-[3/1] w-full object-cover">
        <div class="p-6">
            <span class="chip bg-brand-50 text-brand-700">{{ $event->type_label }}</span>
            @if($event->status !== 'published')<span class="chip ml-1 bg-amber-100 text-amber-800">{{ \App\Models\Event::STATUSES[$event->status] }}</span>@endif
            <h1 class="mt-2 text-2xl font-extrabold text-neutral-900">{{ $event->title }}</h1>
            <p class="mt-1 text-neutral-500">com {{ $event->professional?->display_name }}</p>

            <dl class="mt-4 grid gap-2 text-sm sm:grid-cols-2">
                <div><dt class="text-neutral-400">Quando</dt><dd class="font-medium">{{ $start->format('d/m/Y \à\s H:i') }}</dd></div>
                <div><dt class="text-neutral-400">Onde</dt><dd class="font-medium">{{ $event->modality === 'online' ? '💻 Online' : ($event->location_label ?: 'Presencial') }}</dd></div>
                <div><dt class="text-neutral-400">Investimento</dt><dd class="font-medium">{{ $event->is_free ? 'Gratuito' : money($event->price) }}@if($event->allow_discount && $event->discount_percent > 0)<span class="text-xs text-brand-700"> ({{ rtrim(rtrim(number_format($event->discount_percent,2,',','.'),'0'),',') }}% off)</span>@endif</dd></div>
                @if($event->capacity > 0)<div><dt class="text-neutral-400">Vagas</dt><dd class="font-medium">{{ $event->spots_left }} restantes</dd></div>@endif
            </dl>

            @if($event->description)<p class="mt-5 whitespace-pre-line leading-relaxed text-neutral-600">{{ $event->description }}</p>@endif

            <div class="mt-6 border-t border-neutral-100 pt-6">
                @if($event->isFull())
                    <p class="rounded-xl bg-neutral-100 px-4 py-3 text-center text-sm font-medium text-neutral-600">Vagas esgotadas</p>
                @elseif($event->status !== 'published')
                    <p class="text-sm text-neutral-500">Inscrições ainda não abertas.</p>
                @else
                    <form method="POST" action="{{ route('events.register', $event) }}" class="space-y-3">
                        @csrf
                        @auth
                            <input name="participant_name" value="{{ auth()->user()->name }}" placeholder="Seu nome" required class="w-full rounded-xl border border-neutral-300 px-4 py-2.5 text-sm">
                            <input name="participant_phone" placeholder="Telefone / WhatsApp" class="w-full rounded-xl border border-neutral-300 px-4 py-2.5 text-sm">
                            <button class="btn-brand w-full">{{ $event->is_free ? 'Inscrever-se gratuitamente' : 'Inscrever-se e pagar' }}</button>
                        @else
                            <a href="{{ route('login') }}" class="btn-brand block w-full text-center">Entrar para se inscrever</a>
                        @endauth
                    </form>
                @endif
            </div>
        </div>
    </div>
</div>
@endsection
