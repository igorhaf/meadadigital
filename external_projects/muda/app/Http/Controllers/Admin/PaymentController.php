<?php

namespace App\Http\Controllers\Admin;

use App\Http\Controllers\Controller;
use App\Models\Order;
use App\Services\MercadoPagoService;
use Illuminate\Contracts\View\View;
use Illuminate\Support\Str;

class PaymentController extends Controller
{
    public function index(MercadoPagoService $mp): View
    {
        $publicKey = $mp->publicKey();

        $config = [
            'enabled' => $mp->enabled(),
            'environment' => $mp->environmentLabel(),
            'sandbox' => $mp->isSandbox(),
            'public_key' => $publicKey ? Str::limit($publicKey, 12, '…') : null,
        ];

        $totals = [
            'approved' => Order::whereIn('payment_status', ['approved', 'authorized'])->count(),
            'pending' => Order::where('payment_status', 'pending')->count(),
            'rejected' => Order::whereIn('payment_status', ['rejected', 'cancelled'])->count(),
            'revenue' => (float) Order::whereIn('payment_status', ['approved', 'authorized'])->sum('total'),
        ];

        $orders = Order::latest()->paginate(15);

        return view('admin.payments', compact('config', 'totals', 'orders'));
    }
}
