<script setup>
import { ref, watch, onMounted, onBeforeUnmount } from 'vue';

const props = defineProps({
    action: { type: String, default: '/busca' },
    suggestUrl: { type: String, default: '/api/busca/sugestoes' },
    initial: { type: String, default: '' },
});

const term = ref(props.initial);
const open = ref(false);
const loading = ref(false);
const results = ref({ products: [], categories: [] });
const root = ref(null);
let debounce = null;
let controller = null;

watch(term, (value) => {
    if (debounce) clearTimeout(debounce);
    if (value.trim().length < 2) {
        results.value = { products: [], categories: [] };
        open.value = false;
        return;
    }
    debounce = setTimeout(fetchSuggestions, 250);
});

async function fetchSuggestions() {
    loading.value = true;
    open.value = true;
    try {
        if (controller) controller.abort();
        controller = new AbortController();
        const res = await fetch(`${props.suggestUrl}?q=${encodeURIComponent(term.value)}`, {
            headers: { Accept: 'application/json' },
            signal: controller.signal,
        });
        results.value = await res.json();
    } catch (e) {
        if (e.name !== 'AbortError') console.warn(e);
    } finally {
        loading.value = false;
    }
}

const hasResults = () => results.value.products.length || results.value.categories.length;

function onClickOutside(e) {
    if (root.value && !root.value.contains(e.target)) open.value = false;
}
onMounted(() => document.addEventListener('click', onClickOutside));
onBeforeUnmount(() => document.removeEventListener('click', onClickOutside));
</script>

<template>
    <div ref="root" class="relative w-full">
        <form :action="action" method="GET" @submit="open = false">
            <div class="flex items-center overflow-hidden rounded-full bg-white shadow-sm ring-1 ring-neutral-200 focus-within:ring-2 focus-within:ring-brand-400">
                <input
                    v-model="term"
                    name="q"
                    type="text"
                    autocomplete="off"
                    placeholder="Busque por peças, marcas, categorias…"
                    class="w-full border-0 px-4 py-3 text-sm text-neutral-800 placeholder:text-neutral-400 focus:outline-none focus:ring-0"
                    @focus="term.trim().length >= 2 && (open = true)"
                />
                <button type="submit" class="flex items-center gap-1 bg-brand-600 px-4 py-3 text-white transition hover:bg-brand-700" aria-label="Buscar">
                    <svg xmlns="http://www.w3.org/2000/svg" class="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke-width="2" stroke="currentColor"><path stroke-linecap="round" stroke-linejoin="round" d="m21 21-5.197-5.197m0 0A7.5 7.5 0 1 0 5.196 5.196a7.5 7.5 0 0 0 10.607 10.607Z" /></svg>
                </button>
            </div>
        </form>

        <Transition enter-active-class="transition duration-150" enter-from-class="opacity-0 -translate-y-1" leave-active-class="transition duration-100" leave-to-class="opacity-0">
            <div v-if="open && (hasResults() || loading)" class="absolute z-40 mt-2 w-full overflow-hidden rounded-2xl bg-white shadow-xl ring-1 ring-black/5">
                <div v-if="loading && !hasResults()" class="px-4 py-6 text-center text-sm text-neutral-400">Buscando…</div>

                <div v-if="results.categories.length" class="border-b border-neutral-100 p-2">
                    <p class="px-3 pb-1 pt-2 text-xs font-semibold uppercase tracking-wide text-neutral-400">Categorias</p>
                    <a v-for="c in results.categories" :key="c.url" :href="c.url" class="flex items-center gap-2 rounded-lg px-3 py-2 text-sm text-neutral-700 hover:bg-brand-50">
                        <svg xmlns="http://www.w3.org/2000/svg" class="h-4 w-4 text-neutral-400" fill="none" viewBox="0 0 24 24" stroke-width="2" stroke="currentColor"><path stroke-linecap="round" stroke-linejoin="round" d="M6 6.878V6a2.25 2.25 0 0 1 2.25-2.25h7.5A2.25 2.25 0 0 1 18 6v.878m-12 0c.235-.083.487-.128.75-.128h10.5c.263 0 .515.045.75.128m-12 0A2.25 2.25 0 0 0 4.5 9v.878m13.5-3A2.25 2.25 0 0 1 19.5 9v.878m0 0a2.246 2.246 0 0 0-.75-.128H5.25c-.263 0-.515.045-.75.128m15 0A2.25 2.25 0 0 1 21 12v6a2.25 2.25 0 0 1-2.25 2.25H5.25A2.25 2.25 0 0 1 3 18v-6c0-.98.626-1.813 1.5-2.122" /></svg>
                        {{ c.name }}
                    </a>
                </div>

                <div v-if="results.products.length" class="p-2">
                    <p class="px-3 pb-1 pt-2 text-xs font-semibold uppercase tracking-wide text-neutral-400">Produtos</p>
                    <a v-for="p in results.products" :key="p.url" :href="p.url" class="flex items-center gap-3 rounded-lg px-3 py-2 hover:bg-brand-50">
                        <img :src="p.image" :alt="p.title" class="h-10 w-10 shrink-0 rounded-lg object-cover" />
                        <span class="line-clamp-1 flex-1 text-sm text-neutral-700">{{ p.title }}</span>
                        <span class="text-sm font-bold text-brand-700">{{ p.price }}</span>
                    </a>
                </div>

                <a :href="`${action}?q=${encodeURIComponent(term)}`" class="block border-t border-neutral-100 bg-neutral-50 px-4 py-2.5 text-center text-sm font-semibold text-brand-700 hover:bg-brand-50">
                    Ver todos os resultados para "{{ term }}"
                </a>
            </div>
        </Transition>
    </div>
</template>
