<script setup>
import { cart, cartSubtotal, removeFromCart, setQty, closeDrawer, brl } from '../stores/cart';

const cartUrl = '/carrinho';
</script>

<template>
    <Teleport to="body">
        <Transition
            enter-active-class="transition-opacity duration-200"
            enter-from-class="opacity-0"
            leave-active-class="transition-opacity duration-200"
            leave-to-class="opacity-0"
        >
            <div v-if="cart.drawerOpen" class="fixed inset-0 z-50 bg-neutral-900/50" @click="closeDrawer"></div>
        </Transition>

        <Transition
            enter-active-class="transition-transform duration-300 ease-out"
            enter-from-class="translate-x-full"
            leave-active-class="transition-transform duration-300 ease-in"
            leave-to-class="translate-x-full"
        >
            <aside
                v-if="cart.drawerOpen"
                class="fixed inset-y-0 right-0 z-50 flex w-full max-w-md flex-col bg-white shadow-2xl"
            >
                <header class="flex items-center justify-between border-b border-neutral-200 px-5 py-4">
                    <h2 class="text-lg font-bold text-neutral-900">Seu carrinho</h2>
                    <button type="button" class="rounded-lg p-1.5 text-neutral-500 hover:bg-neutral-100" @click="closeDrawer" aria-label="Fechar">
                        <svg xmlns="http://www.w3.org/2000/svg" class="h-6 w-6" fill="none" viewBox="0 0 24 24" stroke-width="2" stroke="currentColor">
                            <path stroke-linecap="round" stroke-linejoin="round" d="M6 18 18 6M6 6l12 12" />
                        </svg>
                    </button>
                </header>

                <div v-if="cart.items.length === 0" class="flex flex-1 flex-col items-center justify-center gap-3 px-6 text-center">
                    <div class="text-5xl">🛒</div>
                    <p class="text-neutral-500">Seu carrinho está vazio.<br />Que tal garimpar alguns achados?</p>
                    <button class="btn-outline mt-2" @click="closeDrawer">Continuar comprando</button>
                </div>

                <div v-else class="flex-1 space-y-4 overflow-y-auto px-5 py-4">
                    <div v-for="item in cart.items" :key="item.id" class="flex gap-3">
                        <a :href="item.url" class="h-20 w-20 shrink-0 overflow-hidden rounded-xl border border-neutral-200">
                            <img :src="item.image" :alt="item.title" class="h-full w-full object-cover" />
                        </a>
                        <div class="min-w-0 flex-1">
                            <a :href="item.url" class="line-clamp-2 text-sm font-semibold text-neutral-800 hover:text-brand-700">{{ item.title }}</a>
                            <p class="text-xs text-neutral-500">{{ item.seller }}</p>
                            <div class="mt-1.5 flex items-center justify-between">
                                <div class="inline-flex items-center rounded-lg border border-neutral-300 text-sm">
                                    <button class="px-2 py-1 text-neutral-600 hover:text-brand-700" @click="setQty(item.id, item.qty - 1)">−</button>
                                    <span class="w-8 text-center font-semibold">{{ item.qty }}</span>
                                    <button class="px-2 py-1 text-neutral-600 hover:text-brand-700" @click="setQty(item.id, item.qty + 1)">+</button>
                                </div>
                                <span class="font-bold text-brand-700">{{ brl(item.price * item.qty) }}</span>
                            </div>
                        </div>
                        <button class="self-start text-neutral-400 hover:text-red-500" @click="removeFromCart(item.id)" aria-label="Remover">
                            <svg xmlns="http://www.w3.org/2000/svg" class="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke-width="1.8" stroke="currentColor">
                                <path stroke-linecap="round" stroke-linejoin="round" d="m14.74 9-.346 9m-4.788 0L9.26 9m9.968-3.21c.342.052.682.107 1.022.166m-1.022-.165L18.16 19.673a2.25 2.25 0 0 1-2.244 2.077H8.084a2.25 2.25 0 0 1-2.244-2.077L4.772 5.79m14.456 0a48.108 48.108 0 0 0-3.478-.397m-12 .562c.34-.059.68-.114 1.022-.165m0 0a48.11 48.11 0 0 1 3.478-.397m7.5 0v-.916c0-1.18-.91-2.164-2.09-2.201a51.964 51.964 0 0 0-3.32 0c-1.18.037-2.09 1.022-2.09 2.201v.916m7.5 0a48.667 48.667 0 0 0-7.5 0" />
                            </svg>
                        </button>
                    </div>
                </div>

                <footer v-if="cart.items.length > 0" class="border-t border-neutral-200 px-5 py-4">
                    <div class="mb-3 flex items-center justify-between text-base">
                        <span class="font-medium text-neutral-600">Subtotal</span>
                        <span class="text-xl font-extrabold text-neutral-900">{{ brl(cartSubtotal) }}</span>
                    </div>
                    <a :href="cartUrl" class="btn-brand w-full">Finalizar compra</a>
                    <button class="mt-2 w-full text-sm text-neutral-500 hover:text-neutral-700" @click="closeDrawer">Continuar comprando</button>
                </footer>
            </aside>
        </Transition>
    </Teleport>
</template>
