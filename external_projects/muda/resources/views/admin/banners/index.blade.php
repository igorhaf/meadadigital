@extends('layouts.dashboard')

@section('title', 'Banners')

@section('content')
    <div class="mb-6 flex items-center justify-between">
        <h1 class="text-2xl font-extrabold text-neutral-900">Banners</h1>
        <a href="{{ route('admin.banners.create') }}" class="btn-brand">+ Novo banner</a>
    </div>

    @foreach(['hero' => 'Destaques (hero)', 'strip' => 'Tiles promocionais'] as $placement => $label)
        <h2 class="mb-3 mt-6 text-sm font-bold uppercase tracking-wide text-neutral-500">{{ $label }}</h2>
        <div class="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
            @forelse($banners[$placement] ?? [] as $banner)
                <div class="card overflow-hidden">
                    <div class="flex min-h-28 flex-col justify-center p-5 text-white" style="background-image: linear-gradient(120deg, {{ $banner->bg_from }}, {{ $banner->bg_to }})">
                        <p class="font-extrabold">{{ $banner->title }}</p>
                        @if($banner->subtitle)<p class="text-sm text-white/85">{{ $banner->subtitle }}</p>@endif
                    </div>
                    <div class="flex items-center justify-between p-3 text-sm">
                        <span class="chip {{ $banner->is_active ? 'bg-brand-50 text-brand-700' : 'bg-neutral-100 text-neutral-500' }}">{{ $banner->is_active ? 'Ativo' : 'Inativo' }}</span>
                        <div class="flex gap-1">
                            <a href="{{ route('admin.banners.edit', $banner) }}" class="rounded-lg px-2.5 py-1.5 text-xs font-semibold text-brand-700 hover:bg-brand-50">Editar</a>
                            <form method="POST" action="{{ route('admin.banners.destroy', $banner) }}" onsubmit="return confirm('Remover este banner?')">
                                @csrf @method('DELETE')
                                <button class="rounded-lg px-2.5 py-1.5 text-xs font-medium text-red-500 hover:bg-red-50">Excluir</button>
                            </form>
                        </div>
                    </div>
                </div>
            @empty
                <p class="text-sm text-neutral-400">Nenhum banner {{ $placement }}.</p>
            @endforelse
        </div>
    @endforeach
@endsection
