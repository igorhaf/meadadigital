@extends('layouts.app')

@section('title', 'Contato')

@section('content')
    @php($settings = \App\Models\SiteSetting::current())
    <div class="container-muda py-10">
        <nav class="mb-4 text-sm text-neutral-500">
            <a href="{{ route('home') }}" class="hover:text-brand-700">Início</a>
            <span>/</span><span class="font-medium text-neutral-700">Contato</span>
        </nav>

        <div class="mx-auto grid max-w-4xl gap-8 lg:grid-cols-2">
            <div>
                <h1 class="text-3xl font-extrabold text-neutral-900">Fale com a Muda</h1>
                <p class="mt-2 text-neutral-600">Tem dúvida, sugestão ou quer falar sobre parceria? A gente adora um papo.</p>

                <div class="mt-6 space-y-4 text-sm">
                    <div class="flex items-center gap-3">
                        <span class="flex h-10 w-10 items-center justify-center rounded-xl bg-brand-100 text-lg">✉️</span>
                        <div>
                            <p class="text-neutral-500">E-mail</p>
                            <a href="mailto:{{ $settings->contact_email ?? 'contato@muda.com.br' }}" class="font-semibold text-brand-700 hover:underline">{{ $settings->contact_email ?? 'contato@muda.com.br' }}</a>
                        </div>
                    </div>
                    @if($settings->contact_phone)
                        <div class="flex items-center gap-3">
                            <span class="flex h-10 w-10 items-center justify-center rounded-xl bg-brand-100 text-lg">📞</span>
                            <div><p class="text-neutral-500">Telefone</p><p class="font-semibold text-neutral-800">{{ $settings->contact_phone }}</p></div>
                        </div>
                    @endif
                </div>
            </div>

            <div class="card p-6">
                @if(session('status'))
                    <div class="mb-4 rounded-xl bg-brand-100 px-4 py-3 text-sm font-medium text-brand-800">{{ session('status') }}</div>
                @endif
                @if ($errors->any())
                    <div class="mb-4 rounded-xl bg-red-50 px-4 py-3 text-sm text-red-700">{{ $errors->first() }}</div>
                @endif

                <form method="POST" action="{{ route('contact.store') }}" class="space-y-4">
                    @csrf
                    <div class="grid gap-4 sm:grid-cols-2">
                        <div>
                            <label class="mb-1 block text-sm font-medium text-neutral-700">Nome</label>
                            <input name="name" value="{{ old('name') }}" required class="w-full rounded-xl border border-neutral-300 px-4 py-2.5 text-sm focus:border-brand-400 focus:outline-none">
                        </div>
                        <div>
                            <label class="mb-1 block text-sm font-medium text-neutral-700">E-mail</label>
                            <input name="email" type="email" value="{{ old('email') }}" required class="w-full rounded-xl border border-neutral-300 px-4 py-2.5 text-sm focus:border-brand-400 focus:outline-none">
                        </div>
                    </div>
                    <div>
                        <label class="mb-1 block text-sm font-medium text-neutral-700">Assunto</label>
                        <input name="subject" value="{{ old('subject') }}" class="w-full rounded-xl border border-neutral-300 px-4 py-2.5 text-sm focus:border-brand-400 focus:outline-none">
                    </div>
                    <div>
                        <label class="mb-1 block text-sm font-medium text-neutral-700">Mensagem</label>
                        <textarea name="message" rows="5" required class="w-full rounded-xl border border-neutral-300 px-4 py-2.5 text-sm focus:border-brand-400 focus:outline-none">{{ old('message') }}</textarea>
                    </div>
                    <button class="btn-brand w-full">Enviar mensagem</button>
                </form>
            </div>
        </div>
    </div>
@endsection
