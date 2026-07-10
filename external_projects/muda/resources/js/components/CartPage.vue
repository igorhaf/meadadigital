<script setup>
import { computed, ref } from 'vue';
import { cart, cartSubtotal, cartCount, removeFromCart, setQty, clearCart, brl } from '../stores/cart';

const props = defineProps({
    checkoutUrl: { type: String, required: true },
    loginUrl: { type: String, required: true },
    quoteUrl: { type: String, default: '/api/frete/cotar' },
    csrf: { type: String, default: '' },
    authenticated: { type: Boolean, default: false },
    buyer: { type: Object, default: () => ({ name: '', email: '' }) },
    checkoutLabel: { type: String, default: 'Finalizar compra' },
});

// Só id + qty são enviados; o servidor recalcula preços e frete.
const linesPayload = computed(() => cart.items.map((i) => ({ id: i.id, qty: i.qty })));
const itemsJson = computed(() => JSON.stringify(linesPayload.value));

/* ------------------------------------------------------------------ Frete */
const cep = ref('');
const shippingLoading = ref(false);
const shippingError = ref('');
const shippingOptions = ref([]);
const selectedId = ref('');

const selectedShipping = computed(() => shippingOptions.value.find((o) => o.id === selectedId.value) || null);
const total = computed(() => cartSubtotal.value + (selectedShipping.value?.price ?? 0));

function maskCep(v) {
    const d = v.replace(/\D/g, '').slice(0, 8);
    return d.length > 5 ? `${d.slice(0, 5)}-${d.slice(5)}` : d;
}

async function calcShipping() {
    const digits = cep.value.replace(/\D/g, '');
    shippingError.value = '';
    shippingOptions.value = [];
    selectedId.value = '';
    if (digits.length !== 8) {
        shippingError.value = 'Digite um CEP válido.';
        return;
    }
    shippingLoading.value = true;
    try {
        const res = await fetch(`${props.quoteUrl}?cep=${digits}&items=${encodeURIComponent(itemsJson.value)}`, {
            headers: { Accept: 'application/json' },
        });
        const data = await res.json();
        if (!res.ok) throw new Error(data.error || 'Falha ao calcular o frete.');
        shippingOptions.value = data.options || [];
        if (shippingOptions.value.length) selectedId.value = shippingOptions.value[0].id;
        else shippingError.value = 'Nenhuma opção de frete para este CEP.';
    } catch (e) {
        shippingError.value = e.message || 'Não foi possível calcular o frete.';
    } finally {
        shippingLoading.value = false;
    }
}
</script>

<template>
    <div v-if="cartCount === 0" class="mx-auto max-w-lg rounded-3xl border border-neutral-200 bg-white p-10 text-center">
        <div class="text-6xl">🛒</div>
        <h2 class="mt-4 text-2xl font-extrabold text-neutral-900">Seu carrinho está vazio</h2>
        <p class="mt-2 text-neutral-600">Explore milhares de achados de brechó esperando por você.</p>
        <a href="/" class="btn-brand mt-6">Começar a garimpar</a>
    </div>

    <div v-else class="grid gap-8 lg:grid-cols-3">
        <div class="lg:col-span-2">
            <div class="mb-4 flex items-center justify-between">
                <h1 class="text-2xl font-extrabold text-neutral-900">Carrinho <span class="text-base font-medium text-neutral-500">({{ cartCount }} itens)</span></h1>
                <button class="text-sm text-neutral-500 hover:text-red-500" @click="clearCart">Limpar carrinho</button>
            </div>

            <div class="divide-y divide-neutral-100 overflow-hidden rounded-2xl border border-neutral-200 bg-white">
                <div v-for="item in cart.items" :key="item.id" class="flex gap-4 p-4">
                    <a :href="item.url" class="h-24 w-24 shrink-0 overflow-hidden rounded-xl border border-neutral-200">
                        <img :src="item.image" :alt="item.title" class="h-full w-full object-cover" />
                    </a>
                    <div class="flex min-w-0 flex-1 flex-col">
                        <a :href="item.url" class="font-semibold text-neutral-800 hover:text-brand-700">{{ item.title }}</a>
                        <p class="text-sm text-neutral-500">Vendido por {{ item.seller }}</p>
                        <div class="mt-auto flex items-center justify-between pt-3">
                            <div class="inline-flex items-center rounded-lg border border-neutral-300">
                                <button class="px-3 py-1.5 text-neutral-600 hover:text-brand-700" @click="setQty(item.id, item.qty - 1)">−</button>
                                <span class="w-10 text-center font-semibold">{{ item.qty }}</span>
                                <button class="px-3 py-1.5 text-neutral-600 hover:text-brand-700" @click="setQty(item.id, item.qty + 1)">+</button>
                            </div>
                            <div class="text-right">
                                <div class="font-bold text-neutral-900">{{ brl(item.price * item.qty) }}</div>
                                <button class="text-xs text-neutral-400 hover:text-red-500" @click="removeFromCart(item.id)">Remover</button>
                            </div>
                        </div>
                    </div>
                </div>
            </div>
        </div>

        <aside class="lg:col-span-1">
            <div class="sticky top-24 rounded-2xl border border-neutral-200 bg-white p-6">
                <h2 class="text-lg font-bold text-neutral-900">Resumo</h2>

                <!-- Frete -->
                <div class="mt-4">
                    <label class="mb-1 block text-sm font-medium text-neutral-700">Calcular frete</label>
                    <form class="flex gap-2" @submit.prevent="calcShipping">
                        <input :value="cep" @input="cep = maskCep($event.target.value)" inputmode="numeric" placeholder="Seu CEP"
                            class="w-full rounded-xl border border-neutral-300 px-3 py-2 text-sm focus:border-brand-400 focus:outline-none" />
                        <button type="submit" class="btn-outline !px-4 !py-2 text-sm" :disabled="shippingLoading">{{ shippingLoading ? '...' : 'OK' }}</button>
                    </form>
                    <p v-if="shippingError" class="mt-1 text-xs text-red-500">{{ shippingError }}</p>

                    <div v-if="shippingOptions.length" class="mt-3 space-y-2">
                        <label v-for="o in shippingOptions" :key="o.id"
                            class="flex cursor-pointer items-center gap-2 rounded-xl border p-2.5 text-sm transition"
                            :class="selectedId === o.id ? 'border-brand-500 bg-brand-50' : 'border-neutral-200 hover:border-neutral-300'">
                            <input type="radio" name="ship" :value="o.id" v-model="selectedId" class="text-brand-600 focus:ring-brand-500" />
                            <span class="flex-1">
                                <span class="font-medium text-neutral-800">{{ o.service }}</span>
                                <span class="block text-xs text-neutral-500">{{ o.carrier }} · até {{ o.days }} dia(s)</span>
                            </span>
                            <span class="font-bold text-brand-700">{{ o.price === 0 ? 'Grátis' : brl(o.price) }}</span>
                        </label>
                    </div>
                </div>

                <dl class="mt-4 space-y-2 border-t border-neutral-100 pt-4 text-sm">
                    <div class="flex justify-between"><dt class="text-neutral-600">Subtotal</dt><dd class="font-semibold">{{ brl(cartSubtotal) }}</dd></div>
                    <div class="flex justify-between">
                        <dt class="text-neutral-600">Frete</dt>
                        <dd class="font-semibold" :class="selectedShipping?.price === 0 ? 'text-brand-600' : ''">
                            {{ selectedShipping ? (selectedShipping.price === 0 ? 'Grátis' : brl(selectedShipping.price)) : 'Calcule acima' }}
                        </dd>
                    </div>
                </dl>

                <div class="mt-4 flex items-center justify-between border-t border-neutral-200 pt-4">
                    <span class="font-medium text-neutral-600">Total</span>
                    <span class="text-2xl font-extrabold text-neutral-900">{{ brl(total) }}</span>
                </div>
                <p class="mt-1 text-right text-xs text-neutral-500">em até 12x sem juros</p>

                <div v-if="!authenticated" class="mt-5">
                    <a :href="loginUrl" class="btn-brand w-full">Entrar para finalizar</a>
                </div>

                <form v-else :action="checkoutUrl" method="POST" class="mt-5 space-y-3">
                    <input type="hidden" name="_token" :value="csrf" />
                    <input type="hidden" name="items" :value="itemsJson" />
                    <input type="hidden" name="shipping_option_id" :value="selectedShipping?.id || ''" />
                    <input type="hidden" name="shipping_cep" :value="cep.replace(/\D/g, '')" />

                    <div>
                        <label class="mb-1 block text-xs font-medium text-neutral-600">Nome</label>
                        <input name="buyer_name" :value="buyer.name" required class="w-full rounded-lg border border-neutral-300 px-3 py-2 text-sm focus:border-brand-400 focus:outline-none" />
                    </div>
                    <div>
                        <label class="mb-1 block text-xs font-medium text-neutral-600">E-mail</label>
                        <input name="buyer_email" type="email" :value="buyer.email" required class="w-full rounded-lg border border-neutral-300 px-3 py-2 text-sm focus:border-brand-400 focus:outline-none" />
                    </div>
                    <div>
                        <label class="mb-1 block text-xs font-medium text-neutral-600">Endereço de entrega</label>
                        <input name="shipping_address" placeholder="Rua, número — cidade/UF" class="w-full rounded-lg border border-neutral-300 px-3 py-2 text-sm focus:border-brand-400 focus:outline-none" />
                    </div>

                    <button type="submit" class="btn-brand w-full text-base" :disabled="!selectedShipping">{{ checkoutLabel }}</button>
                    <p v-if="!selectedShipping" class="text-center text-xs text-neutral-500">Calcule o frete para continuar.</p>
                </form>
            </div>
        </aside>
    </div>
</template>
