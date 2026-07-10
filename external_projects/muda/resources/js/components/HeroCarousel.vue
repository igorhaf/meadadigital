<script setup>
import { ref, onMounted, onBeforeUnmount, computed } from 'vue';

const props = defineProps({
    slides: { type: Array, default: () => [] },
    interval: { type: Number, default: 5500 },
});

const current = ref(0);
let timer = null;

const active = computed(() => props.slides[current.value] || {});

function go(i) {
    current.value = (i + props.slides.length) % props.slides.length;
    restart();
}
const next = () => go(current.value + 1);
const prev = () => go(current.value - 1);

function restart() {
    if (timer) clearInterval(timer);
    if (props.slides.length > 1) timer = setInterval(next, props.interval);
}

onMounted(restart);
onBeforeUnmount(() => timer && clearInterval(timer));
</script>

<template>
    <div class="relative overflow-hidden rounded-3xl">
        <div
            class="relative flex min-h-[240px] flex-col justify-center px-8 py-12 text-white sm:min-h-[320px] sm:px-14"
            :style="{ backgroundImage: `linear-gradient(120deg, ${active.from}, ${active.to})` }"
        >
            <Transition mode="out-in" enter-active-class="transition duration-500" enter-from-class="opacity-0 translate-y-3" leave-active-class="transition duration-200" leave-to-class="opacity-0">
                <div :key="current" class="max-w-xl">
                    <span class="mb-3 inline-block rounded-full bg-white/20 px-3 py-1 text-xs font-semibold uppercase tracking-wide backdrop-blur">Brechó em destaque</span>
                    <h2 class="text-3xl font-extrabold leading-tight drop-shadow-sm sm:text-4xl">{{ active.title }}</h2>
                    <p class="mt-3 max-w-md text-sm text-white/90 sm:text-base">{{ active.subtitle }}</p>
                    <a v-if="active.link" :href="active.link" class="mt-6 inline-flex items-center gap-2 rounded-xl bg-white px-6 py-3 text-sm font-bold text-neutral-900 shadow-lg transition hover:bg-neutral-100">
                        {{ active.ctaLabel || 'Ver mais' }}
                        <svg xmlns="http://www.w3.org/2000/svg" class="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke-width="2.5" stroke="currentColor"><path stroke-linecap="round" stroke-linejoin="round" d="M13.5 4.5 21 12m0 0-7.5 7.5M21 12H3" /></svg>
                    </a>
                </div>
            </Transition>
        </div>

        <button v-if="slides.length > 1" type="button" class="absolute left-3 top-1/2 -translate-y-1/2 rounded-full bg-white/25 p-2 text-white backdrop-blur transition hover:bg-white/40" @click="prev" aria-label="Anterior">
            <svg xmlns="http://www.w3.org/2000/svg" class="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke-width="2.5" stroke="currentColor"><path stroke-linecap="round" stroke-linejoin="round" d="M15.75 19.5 8.25 12l7.5-7.5" /></svg>
        </button>
        <button v-if="slides.length > 1" type="button" class="absolute right-3 top-1/2 -translate-y-1/2 rounded-full bg-white/25 p-2 text-white backdrop-blur transition hover:bg-white/40" @click="next" aria-label="Próximo">
            <svg xmlns="http://www.w3.org/2000/svg" class="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke-width="2.5" stroke="currentColor"><path stroke-linecap="round" stroke-linejoin="round" d="m8.25 4.5 7.5 7.5-7.5 7.5" /></svg>
        </button>

        <div v-if="slides.length > 1" class="absolute bottom-4 left-1/2 flex -translate-x-1/2 gap-2">
            <button v-for="(s, i) in slides" :key="i" type="button" class="h-2 rounded-full transition-all" :class="i === current ? 'w-6 bg-white' : 'w-2 bg-white/50 hover:bg-white/80'" @click="go(i)" :aria-label="`Slide ${i + 1}`"></button>
        </div>
    </div>
</template>
