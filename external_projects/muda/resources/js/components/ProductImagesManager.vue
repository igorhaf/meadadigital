<script setup>
import { ref, computed, onBeforeUnmount } from 'vue';

/**
 * Galeria do formulário de produto (create/edit):
 *  - preview imediato dos uploads (object URLs);
 *  - ordenação por arrastar-e-soltar (+ setas como fallback p/ touch);
 *  - estrela define a foto de destaque (thumb do produto);
 *  - X remove (existentes viram remove_images[], novas saem do envio).
 *
 * O que o backend recebe:
 *  - images[]         arquivos novos, já na ordem exibida (DataTransfer);
 *  - gallery[]        tokens "existing:ID" / "new:IDX" na ordem final;
 *  - gallery_primary  token da foto de destaque;
 *  - remove_images[]  ids das existentes removidas.
 */
const props = defineProps({
    existing: { type: Array, default: () => [] }, // [{id, url, primary}]
    maxFiles: { type: Number, default: 8 },
});

let uid = 0;
const files = []; // File objects das fotos novas (índice estável = fileIdx)

const items = ref(props.existing.map((img) => ({ key: `e${img.id}`, kind: 'existing', id: img.id, url: img.url })));
const removed = ref([]);
const initialPrimary = props.existing.find((i) => i.primary) || props.existing[0];
const primaryKey = ref(initialPrimary ? `e${initialPrimary.id}` : null);

const pickerInput = ref(null);
const submitInput = ref(null);
const dragKey = ref(null);

const newCount = computed(() => items.value.filter((i) => i.kind === 'new').length);
const canAdd = computed(() => items.value.length < props.maxFiles);

/** Tokens na ordem exibida — "new" usa o índice dentro do FileList reconstruído. */
const tokens = computed(() => {
    let n = 0;

    return items.value.map((i) => (i.kind === 'existing' ? `existing:${i.id}` : `new:${n++}`));
});

const primaryToken = computed(() => {
    const idx = items.value.findIndex((i) => i.key === primaryKey.value);

    return idx >= 0 ? tokens.value[idx] : (tokens.value[0] ?? '');
});

/** Reconstrói o FileList do input real na ordem atual das fotos novas. */
function syncInput() {
    if (!submitInput.value) return;
    const dt = new DataTransfer();
    items.value.filter((i) => i.kind === 'new').forEach((i) => dt.items.add(files[i.fileIdx]));
    submitInput.value.files = dt.files;
}

function addFiles(e) {
    for (const f of Array.from(e.target.files || [])) {
        if (!canAdd.value) break;
        if (!/^image\/(jpeg|png|webp)$/.test(f.type)) continue;
        const fileIdx = files.push(f) - 1;
        items.value.push({ key: `n${uid++}`, kind: 'new', fileIdx, url: URL.createObjectURL(f) });
    }
    e.target.value = ''; // permite re-selecionar o mesmo arquivo depois
    if (!primaryKey.value && items.value.length) primaryKey.value = items.value[0].key;
    syncInput();
}

function remove(item) {
    if (item.kind === 'existing') {
        removed.value.push(item.id);
    } else {
        URL.revokeObjectURL(item.url);
    }
    items.value = items.value.filter((i) => i.key !== item.key);
    if (primaryKey.value === item.key) primaryKey.value = items.value[0]?.key ?? null;
    syncInput();
}

function move(item, delta) {
    const from = items.value.findIndex((i) => i.key === item.key);
    const to = from + delta;
    if (from < 0 || to < 0 || to >= items.value.length) return;
    const list = [...items.value];
    list.splice(to, 0, ...list.splice(from, 1));
    items.value = list;
    syncInput();
}

/* Drag-and-drop: reordena ao vivo enquanto arrasta por cima dos vizinhos. */
function onDragStart(item, e) {
    dragKey.value = item.key;
    e.dataTransfer.effectAllowed = 'move';
    e.dataTransfer.setData('text/plain', item.key); // exigido pelo Firefox
}

function onDragOver(target) {
    if (!dragKey.value || dragKey.value === target.key) return;
    const from = items.value.findIndex((i) => i.key === dragKey.value);
    const to = items.value.findIndex((i) => i.key === target.key);
    if (from < 0 || to < 0) return;
    const list = [...items.value];
    list.splice(to, 0, ...list.splice(from, 1));
    items.value = list;
}

function onDragEnd() {
    dragKey.value = null;
    syncInput();
}

onBeforeUnmount(() => items.value.forEach((i) => i.kind === 'new' && URL.revokeObjectURL(i.url)));
</script>

<template>
    <div class="space-y-2">
        <!-- inputs reais do form -->
        <input ref="pickerInput" type="file" accept="image/png,image/jpeg,image/webp" multiple class="hidden" @change="addFiles">
        <input ref="submitInput" type="file" name="images[]" multiple class="hidden" tabindex="-1" aria-hidden="true" :disabled="newCount === 0">
        <input v-for="(t, i) in tokens" :key="`t${i}`" type="hidden" name="gallery[]" :value="t">
        <input type="hidden" name="gallery_primary" :value="primaryToken">
        <input v-for="id in removed" :key="`r${id}`" type="hidden" name="remove_images[]" :value="id">

        <div class="flex gap-3 overflow-x-auto pb-2 no-scrollbar">
            <div
                v-for="item in items"
                :key="item.key"
                class="group relative h-28 w-28 shrink-0 cursor-grab overflow-hidden rounded-xl border bg-neutral-100 transition"
                :class="[
                    dragKey === item.key ? 'opacity-40 ring-2 ring-brand-400' : '',
                    primaryKey === item.key ? 'border-brand-500 ring-1 ring-brand-300' : 'border-neutral-200',
                ]"
                draggable="true"
                @dragstart="onDragStart(item, $event)"
                @dragover.prevent="onDragOver(item)"
                @dragend="onDragEnd"
                @drop.prevent="onDragEnd"
            >
                <img :src="item.url" alt="" class="pointer-events-none h-full w-full object-cover">

                <!-- estrela: foto de destaque -->
                <button
                    type="button"
                    class="absolute left-1 top-1 flex h-7 w-7 items-center justify-center rounded-full text-sm shadow transition"
                    :class="primaryKey === item.key ? 'bg-amber-400 text-white' : 'bg-black/45 text-white/85 hover:bg-amber-400 hover:text-white'"
                    :title="primaryKey === item.key ? 'Foto de destaque' : 'Definir como destaque'"
                    @click="primaryKey = item.key"
                >★</button>

                <!-- remover -->
                <button
                    type="button"
                    class="absolute right-1 top-1 flex h-7 w-7 items-center justify-center rounded-full bg-black/45 text-sm text-white/85 shadow transition hover:bg-red-600 hover:text-white"
                    title="Remover foto"
                    @click="remove(item)"
                >✕</button>

                <!-- setas (fallback p/ touch) + badge -->
                <div class="absolute inset-x-0 bottom-0 flex items-center justify-between bg-gradient-to-t from-black/60 to-transparent px-1 pb-1 pt-4">
                    <button type="button" class="flex h-6 w-6 items-center justify-center rounded-full text-xs text-white/80 hover:bg-white/20" title="Mover para a esquerda" @click="move(item, -1)">◀</button>
                    <span v-if="primaryKey === item.key" class="rounded-full bg-amber-400 px-2 py-0.5 text-[10px] font-bold uppercase tracking-wide text-white">Destaque</span>
                    <span v-else-if="item.kind === 'new'" class="rounded-full bg-white/25 px-2 py-0.5 text-[10px] font-semibold text-white">Nova</span>
                    <button type="button" class="flex h-6 w-6 items-center justify-center rounded-full text-xs text-white/80 hover:bg-white/20" title="Mover para a direita" @click="move(item, 1)">▶</button>
                </div>
            </div>

            <!-- adicionar -->
            <button
                v-if="canAdd"
                type="button"
                class="flex h-28 w-28 shrink-0 flex-col items-center justify-center gap-1 rounded-xl border-2 border-dashed border-neutral-300 text-neutral-400 transition hover:border-brand-400 hover:text-brand-600"
                @click="pickerInput?.click()"
            >
                <span class="text-2xl leading-none">+</span>
                <span class="text-xs font-medium">Adicionar</span>
            </button>
        </div>

        <p class="text-xs text-neutral-400">
            Arraste as fotos para definir a ordem da galeria — a <span class="font-semibold text-amber-500">★</span> marca a foto de destaque do produto.
            {{ items.length }}/{{ maxFiles }} fotos · JPG, PNG ou WEBP até 8&nbsp;MB (redimensionamos automaticamente).
        </p>
    </div>
</template>
