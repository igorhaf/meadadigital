@extends('layouts.app')

@section('title', 'Seja um terapeuta')

@section('content')
<div class="container-site max-w-lg py-12">
    <div class="card p-8">
        <div class="mb-6 text-center">
            <div class="mx-auto mb-3 flex h-14 w-14 items-center justify-center rounded-2xl bg-brand-100 text-3xl">🌿</div>
            <h1 class="text-2xl font-extrabold text-neutral-900">Ofereça suas terapias na Pindorama</h1>
            <p class="mt-2 text-sm text-neutral-500">Crie sua página, cadastre seus serviços e receba agendamentos com pagamento online.</p>
        </div>

        <form method="POST" action="{{ route('onboarding.store') }}" class="space-y-4">
            @csrf
            <div>
                <label for="professional_name" class="mb-1 block text-sm font-medium text-neutral-700">Nome público / do consultório</label>
                <input type="text" name="professional_name" id="professional_name" value="{{ old('professional_name', auth()->user()->name) }}" required
                       class="w-full rounded-xl border border-neutral-300 px-4 py-2.5 text-sm focus:border-brand-500 focus:outline-none focus:ring-1 focus:ring-brand-500">
                @error('professional_name')<p class="mt-1 text-xs text-red-600">{{ $message }}</p>@enderror
            </div>
            <button type="submit" class="btn-brand w-full">Criar meu perfil de terapeuta</button>
        </form>
    </div>
</div>
@endsection
