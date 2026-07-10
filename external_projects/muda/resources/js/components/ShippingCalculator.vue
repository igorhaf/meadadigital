<script setup>
import { ref } from 'vue';
import { brl } from '../stores/cart';

const props = defineProps({
    productId: { type: [Number, String], required: true },
    quoteUrl: { type: String, default: '/api/frete/cotar' },
});

const cep = ref('');
const loading = ref(false);
const error = ref('');
const options = ref([]);

function maskCep(v) {
    const d = v.replace(/\D/g, '').slice(0, 8);
    return d.length > 5 ? `${d.slice(0, 5)}-${d.slice(5)}` : d;
}

async function calc() {
    const digits = cep.value.replace(/\D/g, '');
    error.value = '';
    options.value = [];
    if (digits.length !== 8) {
        error.value = 'Digite um CEP válido (8 dígitos).';
        return;
    }
    loading.value = true;
    try {
        const items = JSON.stringify([{ id: Number(props.productId), qty: 1 }]);
        const res = await fetch(`${props.quoteUrl}?cep=${digits}&items=${encodeURIComponent(items)}`, {
            headers: { Accept: 'application/json' },
        });
        const data = await res.json();
        if (!res.ok) throw new Error(data.error || 'Falha ao calcular');
        options.value = data.options || [];
        if (!options.value.length) error.value = 'Nenhuma opção de frete para este CEP.';
    } catch (e) {
        error.value = e.message || 'Não foi possível calcular o frete.';
    } finally {
        loading.value = false;
    }
}
</script>

<template>
    <div class="rounded-2xl border border-neutral-200 bg-white p-4">
        <div class="flex items-center gap-2 text-sm font-semibold text-neutral-800">
            <svg class="h-5 w-5 text-brand-600" fill="none" viewBox="0 0 24 24" stroke-width="1.8" stroke="currentColor"><path stroke-linecap="round" stroke-linejoin="round" d="M8.25 18.75a1.5 1.5 0 0 1-3 0m3 0a1.5 1.5 0 0 0-3 0m3 0h6m-9 0H3.375a1.125 1.125 0 0 1-1.125-1.125V14.25m17.25 4.5a1.5 1.5 0 0 1-3 0m3 0a1.5 1.5 0 0 0-3 0m3 0h1.125c.621 0 1.129-.504 1.09-1.124a17.902 17.902 0 0 0-3.213-9.193 2.056 2.056 0 0 0-1.58-.86H14.25M16.5 18.75h-2.25m0-11.177v-.958c0-.568-.422-1.048-.987-1.106a48.554 48.554 0 0 0-10.026 0 1.106 1.106 0 0 0-.987 1.106v7.635m12-6.677v6.677m0 4.5v-4.5m0 0h-12" /></svg>
            Calcular frete e prazo
        </div>

        <form class="mt-3 flex gap-2" @submit.prevent="calc">
            <input :value="cep" @input="cep = maskCep($event.target.value)" inputmode="numeric" placeholder="Seu CEP"
                class="w-36 rounded-xl border border-neutral-300 px-3 py-2 text-sm focus:border-brand-400 focus:outline-none" />
            <button type="submit" class="btn-outline !py-2 text-sm" :disabled="loading">{{ loading ? '...' : 'Calcular' }}</button>
            <a href="https://buscacepinter.correios.com.br/app/endereco/index.php" target="_blank" rel="noopener" class="self-center text-xs text-neutral-400 hover:text-brand-700">Não sei</a>
        </form>

        <p v-if="error" class="mt-2 text-xs text-red-500">{{ error }}</p>

        <ul v-if="options.length" class="mt-3 divide-y divide-neutral-100">
            <li v-for="o in options" :key="o.id" class="flex items-center justify-between py-2 text-sm">
                <div>
                    <span class="font-medium text-neutral-800">{{ o.service }}</span>
                    <span class="text-neutral-400"> · {{ o.carrier }}</span>
                    <span class="block text-xs text-neutral-500">até {{ o.days }} dia(s) útil(eis)</span>
                </div>
                <span class="font-bold text-brand-700">{{ o.price === 0 ? 'Grátis' : brl(o.price) }}</span>
            </li>
        </ul>
    </div>
</template>
