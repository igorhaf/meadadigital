@extends('layouts.app')

@section('title', $user->display_name)
@section('description', $user->headline ?: ('Agende uma sessão com ' . $user->display_name . ' na Pindorama.'))

@php
    $primary = $user->brand_primary ?: '#3b7a57';
    $secondary = $user->brand_secondary ?: '#1f513a';
@endphp

@section('content')
<div style="--brand: {{ $primary }}; --brand-2: {{ $secondary }}">
    {{-- Banner --}}
    <div class="relative h-48 w-full sm:h-60 md:h-72"
         style="background: @if($user->banner_url) center/cover no-repeat url('{{ $user->banner_url }}') @else linear-gradient(120deg, var(--brand), var(--brand-2)) @endif;">
        <div class="absolute inset-0 bg-gradient-to-t from-black/45 to-transparent"></div>
    </div>

    <div class="container-site -mt-16 sm:-mt-20">
        {{-- Header card --}}
        <div class="card flex flex-col gap-5 p-6 sm:flex-row sm:items-center">
            <div class="flex h-28 w-28 shrink-0 items-center justify-center overflow-hidden rounded-2xl text-4xl font-extrabold text-white shadow-lg ring-4 ring-white"
                 style="background: linear-gradient(135deg, var(--brand), var(--brand-2));">
                @if($user->avatar_url)
                    <img src="{{ $user->avatar_url }}" alt="{{ $user->display_name }}" class="h-full w-full object-cover">
                @else
                    {{ $user->initial }}
                @endif
            </div>

            <div class="min-w-0 flex-1">
                <div class="flex flex-wrap items-center gap-2">
                    <h1 class="text-2xl font-extrabold text-neutral-900">{{ $user->display_name }}</h1>
                    @if($user->is_verified)
                        <span class="chip bg-brand-100 text-brand-800">✓ Terapeuta verificado</span>
                    @endif
                </div>
                @if($user->headline)
                    <p class="mt-1 text-neutral-600">{{ $user->headline }}</p>
                @endif
                <div class="mt-2 flex flex-wrap items-center gap-x-4 gap-y-1 text-sm text-neutral-500">
                    @if($user->city)<span>📍 {{ $user->city }}@if($user->state), {{ $user->state }}@endif</span>@endif
                    @if($services->isNotEmpty())<span>🌿 {{ $services->count() }} {{ \Illuminate\Support\Str::plural('serviço', $services->count()) }}</span>@endif
                </div>

                @if($user->specialties->isNotEmpty())
                    <div class="mt-3 flex flex-wrap gap-1.5">
                        @foreach($user->specialties as $spec)
                            <a href="{{ $spec->url }}" class="chip bg-neutral-100 text-neutral-700 hover:bg-brand-50 hover:text-brand-700">{{ $spec->icon }} {{ $spec->name }}</a>
                        @endforeach
                    </div>
                @endif
            </div>

            @if($user->whatsapp)
                <a href="https://wa.me/{{ preg_replace('/\D/', '', $user->whatsapp) }}" target="_blank" rel="noopener"
                   class="btn-brand shrink-0 self-start sm:self-center">💬 WhatsApp</a>
            @endif
        </div>

        <div class="grid gap-8 py-8 lg:grid-cols-[1fr_320px]">
            <div class="space-y-8">
                {{-- Bio --}}
                @if($user->bio)
                    <section>
                        <h2 class="mb-3 text-lg font-bold text-neutral-900">Sobre</h2>
                        <p class="whitespace-pre-line leading-relaxed text-neutral-600">{{ $user->bio }}</p>
                    </section>
                @endif

                {{-- Serviços --}}
                <section>
                    <h2 class="mb-4 text-lg font-bold text-neutral-900">Serviços</h2>
                    @if($services->isEmpty())
                        <div class="card p-8 text-center text-neutral-500">Nenhum serviço disponível no momento.</div>
                    @else
                        <div class="grid grid-cols-2 gap-4 sm:grid-cols-3">
                            @foreach($services as $service)
                                @include('partials.service-card', ['service' => $service])
                            @endforeach
                        </div>
                    @endif
                </section>
            </div>

            {{-- Sidebar: agenda + locais --}}
            <aside class="space-y-6">
                <div class="card p-5">
                    <h3 class="mb-3 text-sm font-bold uppercase tracking-wide text-neutral-500">Agendar</h3>
                    {{-- BookingCalendar island é ligado na P7; por ora, CTA para os serviços --}}
                    <div data-island="BookingCalendar" data-props='@json(['professionalId' => $user->id, 'slotsUrl' => route('booking.slots')])'>
                        <p class="text-sm text-neutral-600">Escolha um serviço acima para ver os horários disponíveis e agendar.</p>
                    </div>
                </div>

                @if($locations->isNotEmpty())
                    <div class="card p-5">
                        <h3 class="mb-3 text-sm font-bold uppercase tracking-wide text-neutral-500">Locais de atendimento</h3>
                        <ul class="space-y-3 text-sm">
                            @foreach($locations as $loc)
                                <li>
                                    <p class="font-semibold text-neutral-800">{{ $loc->is_online ? '💻 ' : '📍 ' }}{{ $loc->name }}</p>
                                    @unless($loc->is_online)
                                        <p class="text-neutral-500">{{ collect([$loc->address, $loc->neighborhood, $loc->city])->filter()->implode(', ') }}</p>
                                    @endunless
                                </li>
                            @endforeach
                        </ul>
                    </div>
                @endif
            </aside>
        </div>
    </div>
</div>
@endsection
