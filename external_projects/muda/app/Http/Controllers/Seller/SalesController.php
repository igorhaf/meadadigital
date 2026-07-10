<?php

namespace App\Http\Controllers\Seller;

use App\Http\Controllers\Controller;
use App\Models\Order;
use Illuminate\Contracts\View\View;

class SalesController extends Controller
{
    public function index(): View
    {
        $user = auth()->user();

        // Orders containing this seller's items, showing only their lines.
        $orders = Order::whereHas('items', fn ($q) => $q->where('seller_id', $user->id))
            ->with(['items' => fn ($q) => $q->where('seller_id', $user->id)])
            ->latest()
            ->paginate(15);

        $totals = [
            'orders' => $orders->total(),
            'units' => (int) $user->sales()->sum('qty'),
            'revenue' => (float) $user->sales()->sum('line_total'),
        ];

        return view('seller.sales', compact('orders', 'totals'));
    }
}
