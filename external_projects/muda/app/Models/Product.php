<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Builder;
use Illuminate\Database\Eloquent\Model;
use Illuminate\Database\Eloquent\Relations\BelongsTo;
use Illuminate\Database\Eloquent\Relations\HasMany;

class Product extends Model
{
    protected $fillable = [
        'category_id', 'seller_id', 'title', 'slug', 'description',
        'condition', 'condition_note', 'brand', 'size', 'color',
        'price', 'compare_at_price', 'max_installments', 'free_shipping',
        'stock', 'weight_grams', 'length_cm', 'width_cm', 'height_cm', 'sku', 'is_active', 'is_featured',
        'rating', 'reviews_count', 'sold_count', 'views',
        'seller_name', 'seller_location',
    ];

    protected $casts = [
        'price' => 'decimal:2',
        'compare_at_price' => 'decimal:2',
        'rating' => 'decimal:1',
        'free_shipping' => 'boolean',
        'is_active' => 'boolean',
        'is_featured' => 'boolean',
    ];

    public const CONDITIONS = [
        'novo' => 'Novo',
        'seminovo' => 'Seminovo',
        'usado' => 'Usado',
    ];

    /* ---------------------------------------------------------------- Relations */

    public function category(): BelongsTo
    {
        return $this->belongsTo(Category::class);
    }

    public function seller(): BelongsTo
    {
        return $this->belongsTo(User::class, 'seller_id');
    }

    public function images(): HasMany
    {
        return $this->hasMany(ProductImage::class)->orderByDesc('is_primary')->orderBy('position');
    }

    /* ------------------------------------------------------------------- Scopes */

    public function scopeActive(Builder $query): Builder
    {
        return $query->where('is_active', true);
    }

    public function scopeFeatured(Builder $query): Builder
    {
        return $query->where('is_featured', true);
    }

    /** Row-level tenant scope: only this seller's products. */
    public function scopeForSeller(Builder $query, User $seller): Builder
    {
        return $query->where('seller_id', $seller->id);
    }

    public function scopeSearch(Builder $query, ?string $term): Builder
    {
        if (! $term) {
            return $query;
        }

        $like = '%' . str_replace(' ', '%', trim($term)) . '%';

        return $query->where(function (Builder $inner) use ($like) {
            $inner->where('title', 'ilike', $like)
                ->orWhere('brand', 'ilike', $like)
                ->orWhere('description', 'ilike', $like);
        });
    }

    /**
     * Apply storefront listing filters coming from the request query string.
     *
     * @param  array<string,mixed>  $filters
     */
    public function scopeFilter(Builder $query, array $filters): Builder
    {
        $query->search($filters['q'] ?? null);

        $query->when($filters['condition'] ?? null, function (Builder $q, $conditions) {
            $q->whereIn('condition', (array) $conditions);
        });

        $query->when($filters['brand'] ?? null, function (Builder $q, $brands) {
            $q->whereIn('brand', (array) $brands);
        });

        $query->when($filters['size'] ?? null, function (Builder $q, $sizes) {
            $q->whereIn('size', (array) $sizes);
        });

        $query->when($filters['min'] ?? null, fn (Builder $q, $min) => $q->where('price', '>=', (float) $min));
        $query->when($filters['max'] ?? null, fn (Builder $q, $max) => $q->where('price', '<=', (float) $max));

        $query->when(($filters['shipping'] ?? null) === 'free', fn (Builder $q) => $q->where('free_shipping', true));

        return match ($filters['sort'] ?? 'relevance') {
            'price_asc'  => $query->orderBy('price'),
            'price_desc' => $query->orderByDesc('price'),
            'newest'     => $query->latest(),
            'best_selling' => $query->orderByDesc('sold_count'),
            'rating'     => $query->orderByDesc('rating'),
            default      => $query->orderByDesc('is_featured')->orderByDesc('sold_count'),
        };
    }

    /* ---------------------------------------------------------------- Accessors */

    public function getRouteKeyName(): string
    {
        return 'slug';
    }

    public function getUrlAttribute(): string
    {
        return route('products.show', $this->slug);
    }

    public function getConditionLabelAttribute(): string
    {
        return self::CONDITIONS[$this->condition] ?? ucfirst($this->condition);
    }

    public function getPrimaryImageUrlAttribute(): string
    {
        $image = $this->relationLoaded('images')
            ? $this->images->first()
            : $this->images()->first();

        return $image?->path ?? placeholder_image($this->slug, $this->title);
    }

    public function getDiscountPercentAttribute(): ?int
    {
        if (! $this->compare_at_price || $this->compare_at_price <= $this->price) {
            return null;
        }

        return (int) round((1 - $this->price / $this->compare_at_price) * 100);
    }

    public function getInstallmentValueAttribute(): float
    {
        $n = max(1, (int) $this->max_installments);

        return round($this->price / $n, 2);
    }

    /**
     * Turn the storefront card data into a plain array for Vue islands (add-to-cart).
     *
     * @return array<string,mixed>
     */
    public function toCartPayload(): array
    {
        return [
            'id' => $this->id,
            'title' => $this->title,
            'slug' => $this->slug,
            'price' => (float) $this->price,
            'image' => $this->primary_image_url,
            'url' => $this->url,
            'seller' => $this->seller_name,
            'freeShipping' => (bool) $this->free_shipping,
        ];
    }
}
