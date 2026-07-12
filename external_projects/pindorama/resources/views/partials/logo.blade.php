{{--
    Marca da Pindorama: palmeira estilizada com sol, sobre gradiente terracota.
    Uso: @include('partials.logo', ['logoSize' => 'h-9 w-9']) — herda a cor do
    texto do elemento pai para o wordmark; a marca tem cores próprias.
--}}
@php($logoSize = $logoSize ?? 'h-9 w-9')
@php($logoId = 'pin-' . uniqid())
<svg viewBox="0 0 48 48" fill="none" xmlns="http://www.w3.org/2000/svg" class="{{ $logoSize }} shrink-0" role="img" aria-label="Pindorama">
    <defs>
        <linearGradient id="{{ $logoId }}" x1="6" y1="4" x2="42" y2="44" gradientUnits="userSpaceOnUse">
            <stop stop-color="#bf6d3d"/>
            <stop offset="1" stop-color="#78381f"/>
        </linearGradient>
    </defs>
    <rect x="2" y="2" width="44" height="44" rx="14" fill="url(#{{ $logoId }})"/>
    <circle cx="33" cy="14.5" r="4.5" fill="#EFC975"/>
    {{-- tronco --}}
    <path d="M22.5 38.5c.3-7.6 1.2-12.9 3-17.5" stroke="#FCF7F0" stroke-width="2.6" stroke-linecap="round"/>
    {{-- copa da palmeira: quatro folhas --}}
    <path d="M25.5 21c-4.8-.6-8.6 1-11.5 4.6 4.9 1 8.8-.4 11.5-4.6Z" fill="#FCF7F0"/>
    <path d="M25.5 21c-1.4-4.6-4.3-7.3-8.8-8.4.5 5 3.4 7.8 8.8 8.4Z" fill="#FCF7F0" opacity=".92"/>
    <path d="M25.5 21c3-3.9 6.6-5.4 11.2-4.6-2.3 4.4-6 6-11.2 4.6Z" fill="#FCF7F0" opacity=".92"/>
    <path d="M25.5 21c4.4 1.6 6.9 4.5 7.8 9-4.8-1.6-7.4-4.6-7.8-9Z" fill="#FCF7F0" opacity=".85"/>
</svg>
