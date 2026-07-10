@extends('layouts.dashboard')

@section('title', 'Configurações do site')

@section('content')
    <div class="mx-auto max-w-2xl">
        <h1 class="mb-1 text-2xl font-extrabold text-neutral-900">Configurações do site</h1>
        <p class="mb-6 text-sm text-neutral-500">Identidade, aviso do topo, redes sociais e contato.</p>

        @if ($errors->any())
            <div class="mb-4 rounded-xl bg-red-50 px-4 py-3 text-sm text-red-700">
                <ul class="list-inside list-disc">@foreach ($errors->all() as $e)<li>{{ $e }}</li>@endforeach</ul>
            </div>
        @endif

        <form method="POST" action="{{ route('admin.settings.update') }}" class="space-y-6">
            @csrf @method('PUT')

            <div class="card space-y-4 p-6">
                <h2 class="font-bold text-neutral-900">Identidade</h2>
                <div>
                    <label class="mb-1 block text-sm font-medium text-neutral-700">Nome do site *</label>
                    <input name="site_name" value="{{ old('site_name', $settings->site_name) }}" required class="w-full rounded-xl border border-neutral-300 px-4 py-2.5 text-sm focus:border-brand-400 focus:outline-none">
                </div>
                <div>
                    <label class="mb-1 block text-sm font-medium text-neutral-700">Slogan</label>
                    <input name="tagline" value="{{ old('tagline', $settings->tagline) }}" class="w-full rounded-xl border border-neutral-300 px-4 py-2.5 text-sm focus:border-brand-400 focus:outline-none">
                </div>
                <div>
                    <label class="mb-1 block text-sm font-medium text-neutral-700">Aviso do topo (barra de anúncio)</label>
                    <input name="announcement" value="{{ old('announcement', $settings->announcement) }}" class="w-full rounded-xl border border-neutral-300 px-4 py-2.5 text-sm focus:border-brand-400 focus:outline-none">
                </div>
                <div>
                    <label class="mb-1 block text-sm font-medium text-neutral-700">Sobre</label>
                    <textarea name="about" rows="3" class="w-full rounded-xl border border-neutral-300 px-4 py-2.5 text-sm focus:border-brand-400 focus:outline-none">{{ old('about', $settings->about) }}</textarea>
                </div>
            </div>

            <div class="card grid gap-4 p-6 sm:grid-cols-2">
                <h2 class="font-bold text-neutral-900 sm:col-span-2">Redes sociais</h2>
                @foreach(['instagram_url' => 'Instagram', 'facebook_url' => 'Facebook', 'tiktok_url' => 'TikTok', 'twitter_url' => 'Twitter / X'] as $field => $label)
                    <div>
                        <label class="mb-1 block text-sm font-medium text-neutral-700">{{ $label }}</label>
                        <input name="{{ $field }}" value="{{ old($field, $settings->$field) }}" placeholder="https://…" class="w-full rounded-xl border border-neutral-300 px-4 py-2.5 text-sm focus:border-brand-400 focus:outline-none">
                    </div>
                @endforeach
                <div>
                    <label class="mb-1 block text-sm font-medium text-neutral-700">WhatsApp</label>
                    <input name="whatsapp" value="{{ old('whatsapp', $settings->whatsapp) }}" placeholder="+55 11 90000-0000" class="w-full rounded-xl border border-neutral-300 px-4 py-2.5 text-sm focus:border-brand-400 focus:outline-none">
                </div>
            </div>

            <div class="card grid gap-4 p-6 sm:grid-cols-2">
                <h2 class="font-bold text-neutral-900 sm:col-span-2">Contato</h2>
                <div>
                    <label class="mb-1 block text-sm font-medium text-neutral-700">E-mail</label>
                    <input name="contact_email" value="{{ old('contact_email', $settings->contact_email) }}" class="w-full rounded-xl border border-neutral-300 px-4 py-2.5 text-sm focus:border-brand-400 focus:outline-none">
                </div>
                <div>
                    <label class="mb-1 block text-sm font-medium text-neutral-700">Telefone</label>
                    <input name="contact_phone" value="{{ old('contact_phone', $settings->contact_phone) }}" class="w-full rounded-xl border border-neutral-300 px-4 py-2.5 text-sm focus:border-brand-400 focus:outline-none">
                </div>
            </div>

            <button class="btn-brand">Salvar configurações</button>
        </form>
    </div>
@endsection
