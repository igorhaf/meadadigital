<!DOCTYPE html>
<html lang="pt-BR" class="h-full">
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <meta name="csrf-token" content="{{ csrf_token() }}">
    <title>@yield('title', 'Painel') · {{ settings()->site_name }}</title>
    <link rel="icon" type="image/svg+xml" href="{{ asset('favicon.svg') }}">
    <meta name="theme-color" content="#b0552c">

    <link rel="preconnect" href="https://fonts.bunny.net">
    <link href="https://fonts.bunny.net/css?family=albert-sans:400,500,600,700,800|fraunces:600,700&display=swap" rel="stylesheet">

    @vite(['resources/css/app.css', 'resources/js/app.js'])
</head>
<body class="flex min-h-full flex-col bg-sand-100">
    @php($user = auth()->user())

    {{-- Top bar --}}
    <header class="bg-forest-900 text-white">
        <div class="container-site flex items-center justify-between py-3">
            <a href="{{ route('home') }}" class="flex items-center gap-2.5">
                @include('partials.logo', ['logoSize' => 'h-8 w-8'])
                <span class="font-display text-xl font-bold tracking-tight">{{ \App\Models\SiteSetting::current()->site_name }}</span> <span class="font-light text-white/60">· Painel</span>
            </a>
            <div class="flex items-center gap-4 text-sm">
                <a href="{{ route('home') }}" class="hidden text-white/80 hover:text-white sm:inline">Ver site ↗</a>
                <span class="hidden text-white/60 sm:inline">|</span>
                <span class="text-white/90">{{ $user->name }}</span>
                <form method="POST" action="{{ route('logout') }}">
                    @csrf
                    <button class="rounded-lg bg-white/10 px-3 py-1.5 font-medium transition hover:bg-white/20">Sair</button>
                </form>
            </div>
        </div>

        {{-- Tabs --}}
        <nav class="border-t border-white/10">
            <div class="container-site flex items-center gap-1 overflow-x-auto no-scrollbar">
                @php($tab = fn ($pattern) => request()->routeIs($pattern) ? 'border-white text-white' : 'border-transparent text-white/70 hover:text-white')
                <a href="{{ route('professional.dashboard') }}" class="whitespace-nowrap border-b-2 px-3 py-3 text-sm font-medium {{ $tab('professional.dashboard') }}">Visão geral</a>
                <a href="{{ route('professional.agenda') }}" class="whitespace-nowrap border-b-2 px-3 py-3 text-sm font-medium {{ $tab('professional.agenda*') }}">Agenda</a>
                <a href="{{ route('professional.services.index') }}" class="whitespace-nowrap border-b-2 px-3 py-3 text-sm font-medium {{ $tab('professional.services.*') }}">Meus serviços</a>
                <a href="{{ route('professional.locations.index') }}" class="whitespace-nowrap border-b-2 px-3 py-3 text-sm font-medium {{ $tab('professional.locations.*') }}">Locais</a>
                <a href="{{ route('professional.availability.edit') }}" class="whitespace-nowrap border-b-2 px-3 py-3 text-sm font-medium {{ $tab('professional.availability.*') }}">Disponibilidade</a>
                <a href="{{ route('professional.profile.edit') }}" class="whitespace-nowrap border-b-2 px-3 py-3 text-sm font-medium {{ $tab('professional.profile.*') }}">Meu perfil</a>
                <a href="{{ route('professional.events.index') }}" class="whitespace-nowrap border-b-2 px-3 py-3 text-sm font-medium {{ $tab('professional.events.*') }}">Eventos</a>
                <a href="{{ route('professional.charges.index') }}" class="whitespace-nowrap border-b-2 px-3 py-3 text-sm font-medium {{ $tab('professional.charges.*') }}">Cobranças</a>

                @if($user->isRoot())
                    <span class="mx-2 h-5 w-px bg-white/20"></span>
                    <span class="whitespace-nowrap py-3 text-xs font-bold uppercase tracking-wide text-gold-300">Admin</span>
                    <a href="{{ route('admin.dashboard') }}" class="whitespace-nowrap border-b-2 px-3 py-3 text-sm font-medium {{ $tab('admin.dashboard') }}">Site</a>
                    <a href="{{ route('admin.settings.edit') }}" class="whitespace-nowrap border-b-2 px-3 py-3 text-sm font-medium {{ $tab('admin.settings.*') }}">Configurações</a>
                    <a href="{{ route('admin.banners.index') }}" class="whitespace-nowrap border-b-2 px-3 py-3 text-sm font-medium {{ $tab('admin.banners.*') }}">Banners</a>
                    <a href="{{ route('admin.featured') }}" class="whitespace-nowrap border-b-2 px-3 py-3 text-sm font-medium {{ $tab('admin.featured') }}">Destaques</a>
                    <a href="{{ route('admin.practices.index') }}" class="whitespace-nowrap border-b-2 px-3 py-3 text-sm font-medium {{ $tab('admin.practices.*') }}">Práticas</a>
                    <a href="{{ route('admin.professionals.index') }}" class="whitespace-nowrap border-b-2 px-3 py-3 text-sm font-medium {{ $tab('admin.professionals.*') }}">Terapeutas</a>
                    <a href="{{ route('admin.rooms.index') }}" class="whitespace-nowrap border-b-2 px-3 py-3 text-sm font-medium {{ $tab('admin.rooms.*') }}">Salas</a>
                    <a href="{{ route('admin.commission.index') }}" class="whitespace-nowrap border-b-2 px-3 py-3 text-sm font-medium {{ $tab('admin.commission.*') }}">Comissão</a>
                    <a href="{{ route('admin.pages.index') }}" class="whitespace-nowrap border-b-2 px-3 py-3 text-sm font-medium {{ $tab('admin.pages.*') }}">Páginas</a>
                    <a href="{{ route('admin.messages.index') }}" class="whitespace-nowrap border-b-2 px-3 py-3 text-sm font-medium {{ $tab('admin.messages.*') }}">Mensagens</a>
                    <a href="{{ route('admin.payments') }}" class="whitespace-nowrap border-b-2 px-3 py-3 text-sm font-medium {{ $tab('admin.payments') }}">Pagamentos</a>
                @endif
            </div>
        </nav>
    </header>

    @if(session('status'))
        <div class="container-site pt-4">
            <div class="rounded-xl bg-brand-100 px-4 py-3 text-sm font-medium text-brand-800">{{ session('status') }}</div>
        </div>
    @endif

    <main class="container-site flex-1 py-8">
        @yield('content')
    </main>
</body>
</html>
