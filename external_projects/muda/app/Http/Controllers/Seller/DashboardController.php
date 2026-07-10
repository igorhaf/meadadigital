<?php

namespace App\Http\Controllers\Seller;

use App\Http\Controllers\Controller;
use App\Models\Product;
use Illuminate\Contracts\View\View;

class DashboardController extends Controller
{
    public function index(): View
    {
        $user = auth()->user();
        $products = fn () => Product::forSeller($user);

        $stats = [
            'products' => $products()->count(),
            'active' => $products()->where('is_active', true)->count(),
            'sold' => (int) $user->sales()->sum('qty'),
            'revenue' => (float) $user->sales()->sum('line_total'),
        ];

        $recentSales = $user->sales()->with('order')->latest()->take(6)->get();
        $lowStock = $products()->where('is_active', true)->orderBy('stock')->take(5)->get();

        return view('seller.dashboard', compact('stats', 'recentSales', 'lowStock'));
    }
}
