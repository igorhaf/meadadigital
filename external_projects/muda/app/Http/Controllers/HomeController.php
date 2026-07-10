<?php

namespace App\Http\Controllers;

use App\Models\Banner;
use App\Models\Category;
use App\Models\Product;
use Illuminate\Contracts\View\View;

class HomeController extends Controller
{
    public function index(): View
    {
        $heroBanners = Banner::active()->placement('hero')->get();
        $stripBanners = Banner::active()->placement('strip')->get();

        $categories = Category::active()->roots()->orderBy('position')->get();

        $base = Product::query()->active()->with('images');

        $deals = (clone $base)
            ->whereNotNull('compare_at_price')
            ->whereColumn('compare_at_price', '>', 'price')
            ->orderByRaw('(compare_at_price - price) / compare_at_price DESC')
            ->take(10)->get();

        $newest = (clone $base)->latest()->take(10)->get();
        $bestSellers = (clone $base)->orderByDesc('sold_count')->take(10)->get();
        $featured = (clone $base)->featured()->take(4)->get();

        // A couple of category showcases for the "shop by category" rows.
        $showcases = $categories->take(3)->map(function (Category $category) {
            return [
                'category' => $category,
                'products' => Product::active()
                    ->where('category_id', $category->id)
                    ->with('images')
                    ->orderByDesc('sold_count')
                    ->take(6)
                    ->get(),
            ];
        })->filter(fn ($row) => $row['products']->isNotEmpty());

        return view('home', compact(
            'heroBanners', 'stripBanners', 'categories',
            'deals', 'newest', 'bestSellers', 'featured', 'showcases'
        ));
    }
}
