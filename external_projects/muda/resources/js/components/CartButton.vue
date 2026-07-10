<script setup>
import { cartCount, openDrawer } from '../stores/cart';
</script>

<template>
    <button
        type="button"
        class="relative inline-flex items-center gap-2 rounded-xl px-3 py-2 text-sm font-medium text-white/90 transition hover:bg-white/10"
        @click="openDrawer"
        aria-label="Abrir carrinho"
    >
        <svg xmlns="http://www.w3.org/2000/svg" class="h-6 w-6" fill="none" viewBox="0 0 24 24" stroke-width="1.8" stroke="currentColor">
            <path stroke-linecap="round" stroke-linejoin="round" d="M2.25 3h1.386c.51 0 .955.343 1.087.836l.383 1.437M7.5 14.25a3 3 0 0 0-3 3h15.75m-12.75-3h11.218c1.121-.001 2.09-.775 2.337-1.87l1.35-6a1.125 1.125 0 0 0-1.1-1.38H5.106M7.5 14.25 5.106 5.272M6 20.25a.75.75 0 1 1-1.5 0 .75.75 0 0 1 1.5 0Zm12.75 0a.75.75 0 1 1-1.5 0 .75.75 0 0 1 1.5 0Z" />
        </svg>
        <span class="hidden sm:inline">Carrinho</span>
        <span
            v-if="cartCount > 0"
            class="absolute -right-1 -top-1 flex h-5 min-w-5 items-center justify-center rounded-full bg-grape-500 px-1.5 text-xs font-bold text-white ring-2 ring-brand-700"
        >
            {{ cartCount }}
        </span>
    </button>
</template>
