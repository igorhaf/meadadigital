<?php

namespace App\Http\Controllers;

use App\Models\Product;
use Illuminate\Contracts\View\View;

class ProductController extends Controller
{
    public function show(Product $product): View
    {
        abort_unless($product->isVisible(), 404);

        $product->load(['images', 'category']);
        $product->incrementQuietly('views');

        $related = Product::active()
            ->where('category_id', $product->category_id)
            ->whereKeyNot($product->id)
            ->with('images')
            ->orderByDesc('sold_count')
            ->take(8)
            ->get();

        return view('products.show', [
            'product' => $product,
            'related' => $related,
        ]);
    }
}
