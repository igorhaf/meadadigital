<?php

namespace App\Http\Controllers\Admin;

use App\Http\Controllers\Controller;
use App\Models\Order;
use App\Models\Product;
use App\Models\User;
use Illuminate\Contracts\View\View;

class DashboardController extends Controller
{
    public function index(): View
    {
        $stats = [
            'products' => Product::count(),
            'active' => Product::where('is_active', true)->count(),
            'sellers' => User::where('is_seller', true)->count(),
            'customers' => User::where('role', 'customer')->count(),
            'orders' => Order::count(),
            'revenue' => (float) Order::sum('total'),
        ];

        $recentOrders = Order::with('items')->latest()->take(8)->get();

        $topSellers = User::where('is_seller', true)
            ->withCount('products')
            ->withSum('sales as revenue', 'line_total')
            ->orderByDesc('revenue')
            ->take(5)
            ->get();

        return view('admin.dashboard', compact('stats', 'recentOrders', 'topSellers'));
    }
}
