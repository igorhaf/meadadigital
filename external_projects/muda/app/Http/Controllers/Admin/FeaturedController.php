<?php

namespace App\Http\Controllers\Admin;

use App\Http\Controllers\Controller;
use App\Models\Product;
use Illuminate\Contracts\View\View;
use Illuminate\Http\RedirectResponse;
use Illuminate\Http\Request;

class FeaturedController extends Controller
{
    public function index(Request $request): View
    {
        $featured = Product::where('is_featured', true)->with('images')->latest('updated_at')->get();

        $products = Product::with('images')
            ->when($request->query('q'), fn ($q, $term) => $q->where('title', 'ilike', "%{$term}%"))
            ->orderByDesc('sold_count')
            ->paginate(12)
            ->withQueryString();

        return view('admin.featured', compact('featured', 'products'));
    }

    public function toggle(Product $product): RedirectResponse
    {
        $product->update(['is_featured' => ! $product->is_featured]);

        return back()->with('status', $product->is_featured
            ? "\"{$product->title}\" agora está em destaque."
            : "\"{$product->title}\" saiu dos destaques.");
    }
}
