<script setup>
import { ref } from 'vue';

const props = defineProps({
    images: { type: Array, default: () => [] },
});

const active = ref(0);
const zoom = ref(false);
const origin = ref('50% 50%');

function move(e) {
    const rect = e.currentTarget.getBoundingClientRect();
    const x = ((e.clientX - rect.left) / rect.width) * 100;
    const y = ((e.clientY - rect.top) / rect.height) * 100;
    origin.value = `${x}% ${y}%`;
}
</script>

<template>
    <div class="flex flex-col-reverse gap-4 sm:flex-row">
        <div class="flex gap-3 overflow-x-auto sm:flex-col no-scrollbar">
            <button
                v-for="(img, i) in images"
                :key="i"
                type="button"
                class="h-16 w-16 shrink-0 overflow-hidden rounded-xl border-2 transition sm:h-20 sm:w-20"
                :class="i === active ? 'border-brand-600 ring-2 ring-brand-200' : 'border-neutral-200 hover:border-neutral-400'"
                @mouseenter="active = i"
                @click="active = i"
            >
                <img :src="img.path" :alt="img.alt" class="h-full w-full object-cover" />
            </button>
        </div>

        <div
            class="relative aspect-square flex-1 cursor-zoom-in overflow-hidden rounded-2xl border border-neutral-200 bg-white"
            @mouseenter="zoom = true"
            @mouseleave="zoom = false"
            @mousemove="move"
        >
            <img
                :src="images[active]?.path"
                :alt="images[active]?.alt"
                class="h-full w-full object-cover transition-transform duration-200"
                :style="{ transform: zoom ? 'scale(1.8)' : 'scale(1)', transformOrigin: origin }"
            />
            <span class="pointer-events-none absolute bottom-3 right-3 rounded-full bg-neutral-900/60 px-2.5 py-1 text-xs text-white backdrop-blur">
                {{ active + 1 }} / {{ images.length }}
            </span>
        </div>
    </div>
</template>
