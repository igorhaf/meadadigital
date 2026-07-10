<?php

namespace App\Support;

use App\Models\Product;
use Illuminate\Database\Eloquent\Builder;

/**
 * Builds the filter facets (brands, sizes, condition counts, price range) shown
 * in the storefront listing sidebar, derived from a base product query.
 */
class ListingFacets
{
    /**
     * @return array<string,mixed>
     */
    public static function build(Builder $base): array
    {
        $brands = (clone $base)->whereNotNull('brand')
            ->distinct()->orderBy('brand')->pluck('brand')->all();

        $sizes = (clone $base)->whereNotNull('size')
            ->distinct()->orderBy('size')->pluck('size')->all();

        $conditions = (clone $base)
            ->selectRaw('condition, count(*) as total')
            ->groupBy('condition')
            ->pluck('total', 'condition')
            ->all();

        $range = (clone $base)->selectRaw('min(price) as min_price, max(price) as max_price')->first();

        return [
            'brands' => $brands,
            'sizes' => $sizes,
            'conditions' => $conditions,
            'conditionLabels' => Product::CONDITIONS,
            'priceMin' => (float) ($range->min_price ?? 0),
            'priceMax' => (float) ($range->max_price ?? 0),
        ];
    }
}
