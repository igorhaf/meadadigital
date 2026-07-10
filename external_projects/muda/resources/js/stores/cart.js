import { reactive, computed, watch } from 'vue';

const STORAGE_KEY = 'muda.cart.v1';

function load() {
    try {
        const raw = localStorage.getItem(STORAGE_KEY);
        return raw ? JSON.parse(raw) : [];
    } catch {
        return [];
    }
}

// Single shared reactive state — every island imports this same module instance.
export const cart = reactive({
    items: load(),
    drawerOpen: false,
});

export const cartCount = computed(() => cart.items.reduce((n, i) => n + i.qty, 0));
export const cartSubtotal = computed(() => cart.items.reduce((s, i) => s + i.price * i.qty, 0));

export function addToCart(product, qty = 1) {
    const existing = cart.items.find((i) => i.id === product.id);
    if (existing) {
        existing.qty += qty;
    } else {
        cart.items.push({ ...product, qty });
    }
    cart.drawerOpen = true;
}

export function removeFromCart(id) {
    cart.items = cart.items.filter((i) => i.id !== id);
}

export function setQty(id, qty) {
    const item = cart.items.find((i) => i.id === id);
    if (item) item.qty = Math.max(1, Math.min(99, qty));
}

export function clearCart() {
    cart.items = [];
}

export const openDrawer = () => (cart.drawerOpen = true);
export const closeDrawer = () => (cart.drawerOpen = false);

// Persist + keep tabs in sync.
watch(
    () => cart.items,
    (value) => localStorage.setItem(STORAGE_KEY, JSON.stringify(value)),
    { deep: true },
);

window.addEventListener('storage', (e) => {
    if (e.key === STORAGE_KEY) cart.items = load();
});

export const brl = (value) =>
    Number(value || 0).toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' });
