@php($settings = $settings ?? \App\Models\SiteSetting::current())
@php($navCategories = $navCategories ?? collect())
@php($socials = $settings->socialLinks())

<footer class="mt-16 bg-neutral-900 text-neutral-300">
    {{-- Trust strip --}}
    <div class="border-b border-white/10">
        <div class="container-muda flex flex-wrap items-center justify-evenly gap-x-6 gap-y-6 py-8">
            @foreach([
                ['💳', 'Até 12x sem juros', 'No cartão de crédito'],
                ['🔒', 'Compra segura', 'Seus dados protegidos'],
                ['♻️', 'Moda circular', 'Consumo consciente'],
            ] as [$icon, $title, $desc])
                <div class="flex items-center gap-3">
                    <span class="text-2xl">{{ $icon }}</span>
                    <div>
                        <p class="text-sm font-semibold text-white">{{ $title }}</p>
                        <p class="text-xs text-neutral-400">{{ $desc }}</p>
                    </div>
                </div>
            @endforeach
        </div>
    </div>

    <div class="container-muda grid grid-cols-2 gap-8 py-12 md:grid-cols-4 lg:grid-cols-5">
        <div class="col-span-2">
            <a href="{{ route('home') }}" class="flex items-center gap-2 text-2xl font-extrabold text-white">
                <span class="text-3xl">🌱</span> {{ $settings->site_name }}
            </a>
            <p class="mt-4 max-w-sm text-sm leading-relaxed text-neutral-400">{{ $settings->about }}</p>

            @if(count($socials))
                <div class="mt-6 flex gap-3">
                    @foreach($socials as $network => $url)
                        <a href="{{ $url }}" target="_blank" rel="noopener" class="flex h-10 w-10 items-center justify-center rounded-full bg-white/10 text-white transition hover:bg-brand-600" aria-label="{{ ucfirst($network) }}">
                            @switch($network)
                                @case('instagram')
                                    <svg class="h-5 w-5" fill="currentColor" viewBox="0 0 24 24"><path d="M12 2.16c3.2 0 3.58.01 4.85.07 1.17.05 1.8.25 2.23.41.56.22.96.48 1.38.9.42.42.68.82.9 1.38.16.42.36 1.06.41 2.23.06 1.27.07 1.65.07 4.85s-.01 3.58-.07 4.85c-.05 1.17-.25 1.8-.41 2.23-.22.56-.48.96-.9 1.38-.42.42-.82.68-1.38.9-.42.16-1.06.36-2.23.41-1.27.06-1.65.07-4.85.07s-3.58-.01-4.85-.07c-1.17-.05-1.8-.25-2.23-.41a3.7 3.7 0 0 1-1.38-.9 3.7 3.7 0 0 1-.9-1.38c-.16-.42-.36-1.06-.41-2.23-.06-1.27-.07-1.65-.07-4.85s.01-3.58.07-4.85c.05-1.17.25-1.8.41-2.23.22-.56.48-.96.9-1.38.42-.42.82-.68 1.38-.9.42-.16 1.06-.36 2.23-.41C8.42 2.17 8.8 2.16 12 2.16Zm0 3.68a6.16 6.16 0 1 0 0 12.32 6.16 6.16 0 0 0 0-12.32Zm0 10.16a4 4 0 1 1 0-8 4 4 0 0 1 0 8Zm6.4-10.4a1.44 1.44 0 1 1-2.88 0 1.44 1.44 0 0 1 2.88 0Z"/></svg>
                                    @break
                                @case('facebook')
                                    <svg class="h-5 w-5" fill="currentColor" viewBox="0 0 24 24"><path d="M22 12a10 10 0 1 0-11.56 9.88v-6.99H7.9V12h2.54V9.8c0-2.5 1.49-3.89 3.78-3.89 1.09 0 2.24.2 2.24.2v2.46h-1.26c-1.24 0-1.63.77-1.63 1.56V12h2.78l-.44 2.89h-2.34v6.99A10 10 0 0 0 22 12Z"/></svg>
                                    @break
                                @case('twitter')
                                    <svg class="h-5 w-5" fill="currentColor" viewBox="0 0 24 24"><path d="M18.9 2H22l-7.5 8.6L23 22h-6.8l-5-6.6L5.5 22H2.4l8-9.2L1.7 2h6.9l4.5 6 5.8-6Zm-2.4 18h1.9L7.6 4H5.6l10.9 16Z"/></svg>
                                    @break
                                @case('tiktok')
                                    <svg class="h-5 w-5" fill="currentColor" viewBox="0 0 24 24"><path d="M16.6 5.8a4.28 4.28 0 0 1-1-2.8h-3v12.1a2.6 2.6 0 1 1-2.6-2.6c.27 0 .53.04.78.12V9.5a5.7 5.7 0 1 0 4.82 5.63V8.68a7.2 7.2 0 0 0 4.2 1.34V7a4.28 4.28 0 0 1-3.2-1.2Z"/></svg>
                                    @break
                                @default
                                    <svg class="h-5 w-5" fill="currentColor" viewBox="0 0 24 24"><path d="M12 2a10 10 0 1 0 0 20 10 10 0 0 0 0-20Zm0 4a2 2 0 1 1 0 4 2 2 0 0 1 0-4Z"/></svg>
                            @endswitch
                        </a>
                    @endforeach
                </div>
            @endif
        </div>

        <div>
            <h3 class="mb-4 text-sm font-bold uppercase tracking-wide text-white">Categorias</h3>
            <ul class="space-y-2 text-sm">
                @foreach($navCategories->take(6) as $cat)
                    <li><a href="{{ $cat->url }}" class="text-neutral-400 transition hover:text-brand-400">{{ $cat->name }}</a></li>
                @endforeach
            </ul>
        </div>

        <div>
            <h3 class="mb-4 text-sm font-bold uppercase tracking-wide text-white">Institucional</h3>
            <ul class="space-y-2 text-sm">
                <li><a href="#" class="text-neutral-400 hover:text-brand-400">Sobre a {{ $settings->site_name }}</a></li>
                <li><a href="#" class="text-neutral-400 hover:text-brand-400">Como funciona</a></li>
                @if(config('muda.selling_enabled'))
                    <li><a href="{{ route('sell.create') }}" class="text-neutral-400 hover:text-brand-400">Venda no {{ $settings->site_name }}</a></li>
                @endif
                <li><a href="#" class="text-neutral-400 hover:text-brand-400">Sustentabilidade</a></li>
            </ul>
        </div>

        <div>
            <h3 class="mb-4 text-sm font-bold uppercase tracking-wide text-white">Ajuda</h3>
            <ul class="space-y-2 text-sm">
                <li><a href="{{ route('pages.help') }}" class="text-neutral-400 hover:text-brand-400">Central de ajuda</a></li>
                <li><a href="{{ route('pages.returns') }}" class="text-neutral-400 hover:text-brand-400">Trocas e devoluções</a></li>
                <li><a href="{{ route('pages.privacy') }}" class="text-neutral-400 hover:text-brand-400">Privacidade</a></li>
                <li><a href="{{ route('contact.show') }}" class="text-neutral-400 hover:text-brand-400">Contato</a></li>
                @if($settings->contact_email)
                    <li><a href="mailto:{{ $settings->contact_email }}" class="text-neutral-400 hover:text-brand-400">{{ $settings->contact_email }}</a></li>
                @endif
            </ul>
        </div>
    </div>

    <div class="border-t border-white/10">
        <div class="container-muda flex flex-col items-center justify-between gap-2 py-6 text-xs text-neutral-500 sm:flex-row">
            <p>© {{ date('Y') }} {{ $settings->site_name }} · Marketplace de brechó. Todos os direitos reservados.</p>
            <p>Feito com 💚 para a <a href="https://meadadigital.com" target="_blank" rel="noopener" class="font-medium text-neutral-400 hover:text-brand-400">meada digital</a></p>
        </div>
    </div>
</footer>
