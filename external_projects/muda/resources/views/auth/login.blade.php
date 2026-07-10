@extends('layouts.auth')

@section('title', 'Entrar')

@section('content')
    <div class="rounded-3xl border border-neutral-200 bg-white p-8 shadow-sm">
        <h1 class="text-2xl font-extrabold text-neutral-900">Entrar</h1>
        <p class="mt-1 text-sm text-neutral-500">Bem-vindo de volta ao seu garimpo.</p>

        @if ($errors->any())
            <div class="mt-4 rounded-xl bg-red-50 px-4 py-3 text-sm text-red-700">
                {{ $errors->first() }}
            </div>
        @endif

        @include('partials.google-button')

        <form method="POST" action="{{ route('login') }}" class="mt-6 space-y-4">
            @csrf
            <div>
                <label class="mb-1 block text-sm font-medium text-neutral-700">E-mail</label>
                <input type="email" name="email" value="{{ old('email') }}" required autofocus
                    class="w-full rounded-xl border border-neutral-300 px-4 py-2.5 text-sm focus:border-brand-400 focus:outline-none focus:ring-2 focus:ring-brand-100">
            </div>
            <div>
                <label class="mb-1 block text-sm font-medium text-neutral-700">Senha</label>
                <input type="password" name="password" required
                    class="w-full rounded-xl border border-neutral-300 px-4 py-2.5 text-sm focus:border-brand-400 focus:outline-none focus:ring-2 focus:ring-brand-100">
            </div>
            <label class="flex items-center gap-2 text-sm text-neutral-600">
                <input type="checkbox" name="remember" class="rounded border-neutral-300 text-brand-600 focus:ring-brand-500">
                Manter conectado
            </label>

            <button type="submit" class="btn-brand w-full">Entrar</button>
        </form>

        <p class="mt-6 text-center text-sm text-neutral-500">
            Ainda não tem conta?
            <a href="{{ route('register') }}" class="font-semibold text-brand-700 hover:underline">Cadastre-se</a>
        </p>
    </div>
@endsection
