@extends('layouts.app')

@section('title', 'Terapeutas')
@section('description', 'Encontre terapeutas de práticas integrativas — acupuntura, reiki, ayurveda, medicina tradicional chinesa e mais.')

@php
    use Illuminate\Support\Facades\Schema;

    // Colunas de perfil do terapeuta ainda estão sendo migradas (fase posterior):
    // renderiza sempre, guardando cada campo opcional.
    $hasSlug = Schema::hasColumn('users', 'professional_slug');
    $hasHeadline = Schema::hasColumn('users', 'headline');
    $hasVerified = Schema::hasColumn('users', 'is_verified');
@endphp

@section('content')
    {{-- Cabeçalho --}}
    <div class="bg-gradient-to-br from-brand-600 to-brand-800 text-white">
        <div class="container-site py-10">
            <h1 class="text-3xl font-extrabold">Terapeutas</h1>
            <p class="mt-2 max-w-2xl text-sm text-white/90">
                Encontre terapeutas de práticas integrativas — acupuntura, reiki, ayurveda,
                medicina tradicional chinesa e muito mais.
            </p>
            <p class="mt-3 text-sm text-white/80">
                {{ $professionals->total() }} {{ $professionals->total() === 1 ? 'terapeuta' : 'terapeutas' }}@if($city) em {{ $city }}@endif
            </p>
        </div>
    </div>

    {{-- Filtro por cidade (GET via links, sem JS) --}}
    @if($cities->isNotEmpty())
        <div class="border-b border-neutral-200 bg-white">
            <div class="container-site flex items-center gap-2 overflow-x-auto no-scrollbar py-3">
                <a href="{{ route('professionals.index') }}"
                   class="chip whitespace-nowrap {{ ! $city ? 'bg-brand-600 text-white' : 'bg-neutral-100 text-neutral-600 hover:bg-brand-50' }}">
                    Todas as cidades
                </a>
                @foreach($cities as $c)
                    <a href="{{ route('professionals.index', ['city' => $c]) }}"
                       class="chip whitespace-nowrap {{ $city === $c ? 'bg-brand-600 text-white' : 'bg-neutral-100 text-neutral-600 hover:bg-brand-50' }}">
                        📍 {{ $c }}
                    </a>
                @endforeach
            </div>
        </div>
    @endif

    <div class="container-site py-8">
        @if($professionals->isEmpty())
            <div class="card flex flex-col items-center justify-center py-16 text-center">
                <div class="text-5xl">🧘</div>
                <p class="mt-4 font-semibold text-neutral-700">
                    @if($city)
                        Nenhum terapeuta encontrado em {{ $city }}.
                    @else
                        Ainda não há terapeutas cadastrados.
                    @endif
                </p>
                @if($city)
                    <a href="{{ route('professionals.index') }}" class="btn-brand mt-6">Ver todos os terapeutas</a>
                @endif
            </div>
        @else
            <div class="grid grid-cols-2 gap-4 sm:grid-cols-3 lg:grid-cols-4">
                @foreach($professionals as $pro)
                    @php
                        $svc = $pro->services->first();
                        $displayName = $svc?->professional_name ?: $pro->name;
                        $initial = mb_strtoupper(mb_substr($displayName ?: 'T', 0, 1));
                        $proCity = $svc?->professional_city;
                        $count = $pro->services->count();
                        $headline = $hasHeadline ? $pro->headline : null;
                        $specialty = $svc?->category?->name;
                        $slug = $hasSlug ? $pro->professional_slug : null;
                        $link = $slug ? route('professionals.show', $slug) : '#';
                        $verified = $hasVerified && $pro->is_verified;
                    @endphp

                    <a href="{{ $link }}" class="card group flex h-full flex-col overflow-hidden">
                        {{-- Cabeçalho colorido com avatar --}}
                        <div class="flex items-center gap-3 bg-gradient-to-br from-brand-600 to-brand-800 p-4 text-white">
                            <div class="flex h-14 w-14 shrink-0 items-center justify-center rounded-2xl bg-white/15 text-2xl font-extrabold backdrop-blur">
                                {{ $initial }}
                            </div>
                            <div class="min-w-0 flex-1">
                                <h3 class="truncate text-base font-extrabold transition group-hover:text-white/90">{{ $displayName }}</h3>
                                @if($proCity)
                                    <p class="truncate text-xs text-white/80">📍 {{ $proCity }}</p>
                                @endif
                            </div>
                        </div>

                        {{-- Corpo --}}
                        <div class="flex flex-1 flex-col gap-2 p-4">
                            @if($headline)
                                <p class="line-clamp-2 text-sm text-neutral-600">{{ $headline }}</p>
                            @elseif($specialty)
                                <span class="chip w-fit bg-neutral-100 text-neutral-600">{{ $specialty }}</span>
                            @endif

                            <div class="mt-auto flex items-center justify-between gap-2 pt-2">
                                <span class="chip bg-brand-50 text-brand-700">
                                    {{ $count }} {{ $count === 1 ? 'serviço' : 'serviços' }}
                                </span>
                                @if($verified)
                                    <span class="chip bg-gold-600 text-white">✓ Verificado</span>
                                @endif
                            </div>
                        </div>
                    </a>
                @endforeach
            </div>

            <div class="mt-8">{{ $professionals->onEachSide(1)->links() }}</div>
        @endif
    </div>
@endsection
