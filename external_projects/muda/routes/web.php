<?php

use App\Http\Controllers\Admin;
use App\Http\Controllers\Auth\LoginController;
use App\Http\Controllers\Auth\RegisterController;
use App\Http\Controllers\Auth\SocialController;
use App\Http\Controllers\CartController;
use App\Http\Controllers\CategoryController;
use App\Http\Controllers\CheckoutController;
use App\Http\Controllers\ContactController;
use App\Http\Controllers\HomeController;
use App\Http\Controllers\OrderController;
use App\Http\Controllers\PageController;
use App\Http\Controllers\PaymentController;
use App\Http\Controllers\PlaceholderController;
use App\Http\Controllers\ProductController;
use App\Http\Controllers\SearchController;
use App\Http\Controllers\Seller;
use App\Http\Controllers\ShippingController;
use App\Http\Controllers\StoreController;
use Illuminate\Support\Facades\Route;

/* ------------------------------------------------------------- Storefront */
Route::get('/', [HomeController::class, 'index'])->name('home');

Route::get('/busca', [SearchController::class, 'index'])->name('search');
Route::get('/api/busca/sugestoes', [SearchController::class, 'suggest'])->name('search.suggest');
Route::get('/api/frete/cotar', [ShippingController::class, 'quote'])->name('shipping.quote');

Route::get('/carrinho', [CartController::class, 'index'])->name('cart');

Route::get('/produto/{product:slug}', [ProductController::class, 'show'])->name('products.show');
Route::get('/categoria/{category:slug}', [CategoryController::class, 'show'])->name('categories.show');
Route::get('/loja/{user:store_slug}', [StoreController::class, 'show'])->name('stores.show');

// Institutional pages (content editable by root in the admin).
Route::get('/central-de-ajuda', [PageController::class, 'show'])->defaults('slug', 'central-de-ajuda')->name('pages.help');
Route::get('/trocas-e-devolucoes', [PageController::class, 'show'])->defaults('slug', 'trocas-e-devolucoes')->name('pages.returns');
Route::get('/privacidade', [PageController::class, 'show'])->defaults('slug', 'privacidade')->name('pages.privacy');
Route::get('/contato', [ContactController::class, 'show'])->name('contact.show');
Route::post('/contato', [ContactController::class, 'store'])->name('contact.store');

// Self-contained SVG placeholder image generator (demo assets).
Route::get('/ph', [PlaceholderController::class, 'show'])->name('placeholder');

// Mercado Pago server-to-server webhook (público, isento de CSRF em bootstrap/app.php).
Route::post('/webhooks/mercadopago', [PaymentController::class, 'webhook'])->name('mp.webhook');

/* ------------------------------------------------------------------ Auth */
Route::middleware('guest')->group(function () {
    Route::get('/entrar', [LoginController::class, 'create'])->name('login');
    Route::post('/entrar', [LoginController::class, 'store']);
    Route::get('/cadastrar', [RegisterController::class, 'create'])->name('register');
    Route::post('/cadastrar', [RegisterController::class, 'store']);

    Route::get('/auth/google/redirect', [SocialController::class, 'redirect'])->name('auth.google.redirect');
    Route::get('/auth/google/callback', [SocialController::class, 'callback'])->name('auth.google.callback');
});
Route::post('/sair', [LoginController::class, 'destroy'])->middleware('auth')->name('logout');

/* -------------------------------------------------- Authenticated (any) */
Route::middleware('auth')->group(function () {
    Route::post('/checkout', [CheckoutController::class, 'store'])->name('checkout.store');

    // Checkout Transparente (Payment Brick) — pagamento dentro da loja.
    Route::get('/pagamento/{order}', [PaymentController::class, 'show'])->name('payment.show');
    Route::post('/pagamento/{order}/processar', [PaymentController::class, 'process'])->name('payment.process');

    Route::get('/meus-pedidos', [OrderController::class, 'index'])->name('orders.index');
    Route::get('/meus-pedidos/{order}', [OrderController::class, 'show'])->name('orders.show');
    Route::post('/meus-pedidos/{order}/pagar', [PaymentController::class, 'retry'])->name('orders.retry');

    Route::middleware('selling')->group(function () {
        Route::get('/vender', [Seller\OnboardingController::class, 'create'])->name('sell.create');
        Route::post('/vender', [Seller\OnboardingController::class, 'store'])->name('sell.store');
    });
});

/* ----------------------------------------------- Seller dashboard (tenant) */
Route::middleware(['auth', 'role:seller', 'selling'])->prefix('painel')->group(function () {
    Route::get('/', [Seller\DashboardController::class, 'index'])->name('dashboard');

    Route::get('/produtos', [Seller\ProductController::class, 'index'])->name('seller.products.index');
    Route::get('/produtos/novo', [Seller\ProductController::class, 'create'])->name('seller.products.create');
    Route::post('/produtos', [Seller\ProductController::class, 'store'])->name('seller.products.store');
    Route::get('/produtos/{product}/editar', [Seller\ProductController::class, 'edit'])->name('seller.products.edit');
    Route::put('/produtos/{product}', [Seller\ProductController::class, 'update'])->name('seller.products.update');
    Route::delete('/produtos/{product}', [Seller\ProductController::class, 'destroy'])->name('seller.products.destroy');
    Route::post('/produtos/{product}/toggle', [Seller\ProductController::class, 'toggle'])->name('seller.products.toggle');

    Route::get('/vendas', [Seller\SalesController::class, 'index'])->name('seller.sales');

    Route::get('/loja', [Seller\StoreController::class, 'edit'])->name('seller.store.edit');
    Route::put('/loja', [Seller\StoreController::class, 'update'])->name('seller.store.update');
});

/* --------------------------------------------------- Root admin (site-wide) */
Route::middleware(['auth', 'role:root'])->prefix('admin')->name('admin.')->group(function () {
    Route::get('/', [Admin\DashboardController::class, 'index'])->name('dashboard');

    Route::get('/config', [Admin\SettingsController::class, 'edit'])->name('settings.edit');
    Route::put('/config', [Admin\SettingsController::class, 'update'])->name('settings.update');

    Route::get('/banners', [Admin\BannerController::class, 'index'])->name('banners.index');
    Route::get('/banners/novo', [Admin\BannerController::class, 'create'])->name('banners.create');
    Route::post('/banners', [Admin\BannerController::class, 'store'])->name('banners.store');
    Route::get('/banners/{banner}/editar', [Admin\BannerController::class, 'edit'])->name('banners.edit');
    Route::put('/banners/{banner}', [Admin\BannerController::class, 'update'])->name('banners.update');
    Route::delete('/banners/{banner}', [Admin\BannerController::class, 'destroy'])->name('banners.destroy');

    Route::get('/destaques', [Admin\FeaturedController::class, 'index'])->name('featured');
    Route::post('/destaques/{product}/toggle', [Admin\FeaturedController::class, 'toggle'])->name('featured.toggle');

    Route::get('/paginas', [Admin\PageController::class, 'index'])->name('pages.index');
    Route::get('/paginas/{page}/editar', [Admin\PageController::class, 'edit'])->name('pages.edit');
    Route::put('/paginas/{page}', [Admin\PageController::class, 'update'])->name('pages.update');

    Route::get('/mensagens', [Admin\MessageController::class, 'index'])->name('messages.index');
    Route::post('/mensagens/{message}/lida', [Admin\MessageController::class, 'toggleRead'])->name('messages.toggle');
    Route::delete('/mensagens/{message}', [Admin\MessageController::class, 'destroy'])->name('messages.destroy');

    Route::get('/pagamentos', [Admin\PaymentController::class, 'index'])->name('payments');

    Route::get('/frete', [Admin\ShippingController::class, 'index'])->name('shipping');
    Route::get('/frete/conectar', [Admin\ShippingController::class, 'connect'])->name('shipping.connect');
    Route::post('/frete/desconectar', [Admin\ShippingController::class, 'disconnect'])->name('shipping.disconnect');
});

// Callback OAuth do Melhor Envio (redirect_uri cadastrado = /frete/callback).
Route::get('/frete/callback', [Admin\ShippingController::class, 'callback'])
    ->middleware(['auth', 'role:root'])->name('shipping.callback');
