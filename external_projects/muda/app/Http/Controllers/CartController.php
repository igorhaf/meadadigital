<?php

namespace App\Http\Controllers;

use App\Models\Product;
use Illuminate\Contracts\View\View;

class CartController extends Controller
{
    /**
     * The cart itself lives client-side (Vue island + localStorage). This page
     * just renders the shell plus a few "you might also like" suggestions.
     */
    public function index(): View
    {
        $suggestions = Product::active()
            ->with('images')
            ->orderByDesc('sold_count')
            ->take(6)
            ->get();

        return view('cart', compact('suggestions'));
    }
}
