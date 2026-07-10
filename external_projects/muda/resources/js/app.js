import { createApp } from 'vue';

import HeroCarousel from './components/HeroCarousel.vue';
import SearchBar from './components/SearchBar.vue';
import AddToCart from './components/AddToCart.vue';
import CartButton from './components/CartButton.vue';
import CartDrawer from './components/CartDrawer.vue';
import ProductGallery from './components/ProductGallery.vue';
import CartPage from './components/CartPage.vue';
import ShippingCalculator from './components/ShippingCalculator.vue';

// Registry of interactive "islands" mounted into server-rendered Blade markup.
const registry = {
    HeroCarousel,
    SearchBar,
    AddToCart,
    CartButton,
    CartDrawer,
    ProductGallery,
    CartPage,
    ShippingCalculator,
};

document.querySelectorAll('[data-island]').forEach((el) => {
    const Component = registry[el.dataset.island];
    if (!Component) return;

    let props = {};
    if (el.dataset.props) {
        try {
            props = JSON.parse(el.dataset.props);
        } catch (e) {
            console.warn('Island props parse error for', el.dataset.island, e);
        }
    }

    createApp(Component, props).mount(el);
});
