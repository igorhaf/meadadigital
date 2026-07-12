@php($settings = $settings ?? \App\Models\SiteSetting::current())
@php($navCategories = $navCategories ?? collect())

<header class="sticky top-0 z-40">
    {{-- Announcement bar --}}
    @if($settings->announcement)
        <div class="bg-forest-950 text-center text-xs font-medium text-forest-100 sm:text-sm">
            <div class="container-site py-2">{{ $settings->announcement }}</div>
        </div>
    @endif

    {{-- Main bar --}}
    <div class="border-b border-neutral-200/80 bg-white/95 shadow-sm backdrop-blur">
        <div class="container-site flex items-center gap-3 py-3 sm:gap-6">
            <a href="{{ route('home') }}" class="flex shrink-0 items-center gap-2.5 text-neutral-900">
                @include('partials.logo', ['logoSize' => 'h-10 w-10'])
                <span class="font-display text-2xl font-bold tracking-tight">{{ $settings->site_name }}</span>
            </a>

            @php($searchProps = ['action' => route('search'), 'suggestUrl' => route('search.suggest'), 'initial' => request('q', '')])
            <div class="flex-1">
                <div
                    data-island="SearchBar"
                    data-props='@json($searchProps)'
                >
                    {{-- Progressive-enhancement fallback (works without JS) --}}
                    <form action="{{ route('search') }}" method="GET" class="flex overflow-hidden rounded-full border border-neutral-200 bg-sand-50 focus-within:border-brand-400 focus-within:ring-2 focus-within:ring-brand-100">
                        <input name="q" value="{{ request('q') }}" placeholder="Busque por terapias, terapeutas ou práticas…" class="w-full border-0 bg-transparent px-5 py-3 text-sm text-neutral-800 placeholder:text-neutral-400 focus:outline-none">
                        <button class="m-1 rounded-full bg-brand-600 px-5 text-sm font-semibold text-white transition hover:bg-brand-700">Buscar</button>
                    </form>
                </div>
            </div>

            @auth
                @php($u = auth()->user())
                <details data-close-outside class="account-menu group relative hidden shrink-0 lg:block">
                    <summary class="flex cursor-pointer list-none items-center gap-2 rounded-full px-3 py-2 text-sm font-medium text-neutral-700 transition hover:bg-sand-100 [&::-webkit-details-marker]:hidden">
                        <svg class="h-6 w-6 text-brand-700" fill="none" viewBox="0 0 24 24" stroke-width="1.8" stroke="currentColor"><path stroke-linecap="round" stroke-linejoin="round" d="M15.75 6a3.75 3.75 0 1 1-7.5 0 3.75 3.75 0 0 1 7.5 0ZM4.501 20.118a7.5 7.5 0 0 1 14.998 0A17.933 17.933 0 0 1 12 21.75c-2.676 0-5.216-.584-7.499-1.632Z" /></svg>
                        <span class="text-left leading-tight"><span class="block text-xs text-neutral-500">Olá,</span><span class="block font-semibold text-neutral-900">{{ \Illuminate\Support\Str::of($u->name)->before(' ') }}</span></span>
                    </summary>
                    <div class="absolute right-0 top-full z-50 mt-1 w-56 overflow-hidden rounded-2xl border border-neutral-100 bg-white py-2 shadow-xl">
                        <div class="border-b border-neutral-100 px-4 py-2">
                            <p class="text-sm font-semibold text-neutral-900">{{ $u->name }}</p>
                            <p class="truncate text-xs text-neutral-500">{{ $u->email }}</p>
                        </div>
                        @php($selling = config('pindorama.professionals_enabled'))
                        <a href="{{ route('appointments.index') }}" class="menu-item">🗓️ Meus agendamentos</a>
                        @if($u->isProfessional() && ($selling || $u->isRoot()))
                            <a href="{{ route('professional.dashboard') }}" class="menu-item">🩺 Meu painel</a>
                        @elseif(! $u->isProfessional() && $selling)
                            <a href="{{ route('onboarding.create') }}" class="menu-item">✨ Seja um terapeuta</a>
                        @endif
                        @if($u->isRoot())
                            <a href="{{ route('admin.dashboard') }}" class="menu-item font-semibold text-gold-700">⚙️ Administração</a>
                        @endif
                        <form method="POST" action="{{ route('logout') }}" class="border-t border-neutral-100">
                            @csrf
                            <button type="submit" class="menu-item w-full text-left text-red-600">↩ Sair</button>
                        </form>
                    </div>
                </details>
            @else
                <div class="hidden shrink-0 items-center gap-2 lg:flex">
                    <a href="{{ route('login') }}" class="rounded-full px-4 py-2 text-sm font-medium text-neutral-700 transition hover:bg-sand-100 hover:text-brand-700">Entrar</a>
                    <a href="{{ route('register') }}" class="rounded-full bg-brand-600 px-5 py-2 text-sm font-semibold text-white shadow-sm transition hover:bg-brand-700">Cadastrar</a>
                </div>
            @endauth
        </div>
    </div>

    {{-- Category nav — chips com submenu no hover (sem hambúrguer) --}}
    <nav class="border-b border-neutral-200/80 bg-white/95 backdrop-blur">
        <div class="container-site flex items-center gap-1 overflow-x-auto no-scrollbar xl:justify-center xl:overflow-visible">
            @foreach($navCategories as $cat)
                <div class="group relative shrink-0">
                    <a href="{{ $cat->url }}" class="flex items-center gap-1.5 whitespace-nowrap px-3 py-3 text-sm font-medium text-neutral-600 transition hover:text-brand-700 group-hover:text-brand-700">
                        <span>{{ $cat->icon }}</span>{{ $cat->name }}
                        @if($cat->children->isNotEmpty())
                            <svg class="h-3.5 w-3.5 text-neutral-400 transition group-hover:rotate-180" fill="none" viewBox="0 0 24 24" stroke-width="2.5" stroke="currentColor"><path stroke-linecap="round" stroke-linejoin="round" d="m19.5 8.25-7.5 7.5-7.5-7.5" /></svg>
                        @endif
                    </a>
                    @if($cat->children->isNotEmpty())
                        <div class="invisible absolute left-0 top-full z-30 w-56 -translate-y-1 rounded-2xl border border-neutral-100 bg-white p-2 opacity-0 shadow-xl transition-all group-hover:visible group-hover:translate-y-0 group-hover:opacity-100">
                            @foreach($cat->children as $child)
                                <a href="{{ $child->url }}" class="block rounded-lg px-3 py-2 text-sm text-neutral-600 hover:bg-brand-50 hover:text-brand-700">{{ $child->name }}</a>
                            @endforeach
                        </div>
                    @endif
                </div>
            @endforeach
        </div>
    </nav>
</header>

<script>
    // Close any open dropdown (account menu, categories) when clicking outside of it.
    document.addEventListener('click', (e) => {
        document.querySelectorAll('details[data-close-outside][open]').forEach((d) => {
            if (!d.contains(e.target)) d.removeAttribute('open');
        });
    });
</script>
