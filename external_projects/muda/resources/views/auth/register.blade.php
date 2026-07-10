@extends('layouts.auth')

@section('title', 'Criar conta')

@section('content')
    <div class="rounded-3xl border border-neutral-200 bg-white p-8 shadow-sm">
        <h1 class="text-2xl font-extrabold text-neutral-900">Criar conta</h1>
        <p class="mt-1 text-sm text-neutral-500">Junte-se à moda circular.</p>

        @if ($errors->any())
            <div class="mt-4 rounded-xl bg-red-50 px-4 py-3 text-sm text-red-700">
                <ul class="list-inside list-disc space-y-0.5">
                    @foreach ($errors->all() as $error)<li>{{ $error }}</li>@endforeach
                </ul>
            </div>
        @endif

        @include('partials.google-button')

        <form method="POST" action="{{ route('register') }}" class="mt-6 space-y-4">
            @csrf
            <div>
                <label class="mb-1 block text-sm font-medium text-neutral-700">Nome completo</label>
                <input type="text" name="name" value="{{ old('name') }}" required autofocus
                    class="w-full rounded-xl border border-neutral-300 px-4 py-2.5 text-sm focus:border-brand-400 focus:outline-none focus:ring-2 focus:ring-brand-100">
            </div>
            <div>
                <label class="mb-1 block text-sm font-medium text-neutral-700">E-mail</label>
                <input type="email" name="email" value="{{ old('email') }}" required
                    class="w-full rounded-xl border border-neutral-300 px-4 py-2.5 text-sm focus:border-brand-400 focus:outline-none focus:ring-2 focus:ring-brand-100">
            </div>
            <div class="grid grid-cols-2 gap-3">
                <div>
                    <label class="mb-1 block text-sm font-medium text-neutral-700">Senha</label>
                    <input type="password" name="password" required
                        class="w-full rounded-xl border border-neutral-300 px-4 py-2.5 text-sm focus:border-brand-400 focus:outline-none focus:ring-2 focus:ring-brand-100">
                </div>
                <div>
                    <label class="mb-1 block text-sm font-medium text-neutral-700">Confirmar</label>
                    <input type="password" name="password_confirmation" required
                        class="w-full rounded-xl border border-neutral-300 px-4 py-2.5 text-sm focus:border-brand-400 focus:outline-none focus:ring-2 focus:ring-brand-100">
                </div>
            </div>

            @if(config('muda.selling_enabled'))
                <div class="rounded-xl bg-brand-50 p-4">
                    <label class="flex items-start gap-2 text-sm text-neutral-700">
                        <input type="checkbox" name="become_seller" value="1" id="become_seller" {{ old('become_seller') ? 'checked' : '' }}
                            class="mt-0.5 rounded border-neutral-300 text-brand-600 focus:ring-brand-500">
                        <span><strong>Quero vender no Muda</strong> — crie sua loja e comece a desapegar.</span>
                    </label>
                    <div id="store_field" class="mt-3 {{ old('become_seller') ? '' : 'hidden' }}">
                        <label class="mb-1 block text-sm font-medium text-neutral-700">Nome da sua loja</label>
                        <input type="text" name="store_name" value="{{ old('store_name') }}"
                            class="w-full rounded-xl border border-neutral-300 px-4 py-2.5 text-sm focus:border-brand-400 focus:outline-none focus:ring-2 focus:ring-brand-100"
                            placeholder="Ex.: Brechó da Ana">
                    </div>
                </div>
            @endif

            <button type="submit" class="btn-brand w-full">Criar conta</button>
        </form>

        <p class="mt-6 text-center text-sm text-neutral-500">
            Já tem conta?
            <a href="{{ route('login') }}" class="font-semibold text-brand-700 hover:underline">Entrar</a>
        </p>
    </div>

    <script>
        document.getElementById('become_seller')?.addEventListener('change', function () {
            document.getElementById('store_field').classList.toggle('hidden', !this.checked);
        });
    </script>
@endsection
