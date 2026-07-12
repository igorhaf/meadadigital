<?php

namespace App\Http\Controllers\Seller;

use App\Http\Controllers\Controller;
use App\Models\Category;
use App\Models\Product;
use Illuminate\Contracts\View\View;
use Illuminate\Http\RedirectResponse;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\Storage;
use Illuminate\Support\Str;

class ProductController extends Controller
{
    public function index(Request $request): View
    {
        $products = Product::forSeller(auth()->user())
            ->with('images')
            ->when($request->query('q'), fn ($q, $term) => $q->where('title', 'ilike', "%{$term}%"))
            ->latest()
            ->paginate(15)
            ->withQueryString();

        return view('seller.products.index', compact('products'));
    }

    public function create(): View
    {
        return view('seller.products.create', [
            'product' => new Product(['condition' => 'usado', 'stock' => 1, 'max_installments' => 12, 'is_active' => true, 'weight_grams' => 300, 'length_cm' => 20, 'width_cm' => 15, 'height_cm' => 5]),
            'categories' => $this->categoryOptions(),
        ]);
    }

    public function store(Request $request): RedirectResponse
    {
        $data = $this->validated($request);
        $user = auth()->user();

        $product = Product::create($data + [
            'seller_id' => $user->id,
            'slug' => $this->uniqueSlug($data['title']),
            'seller_name' => $user->store_name,
            'seller_location' => $user->store_location,
            'sku' => 'MUDA-' . Str::upper(Str::random(6)),
        ]);

        $this->syncImages($product, $request);

        return redirect()->route('seller.products.index')->with('status', 'Produto publicado com sucesso!');
    }

    public function edit(Product $product): View
    {
        $this->authorize('update', $product);

        return view('seller.products.edit', [
            'product' => $product->load('images'),
            'categories' => $this->categoryOptions(),
        ]);
    }

    public function update(Request $request, Product $product): RedirectResponse
    {
        $this->authorize('update', $product);

        $product->update($this->validated($request));
        $this->syncImages($product, $request);

        return redirect()->route('seller.products.index')->with('status', 'Produto atualizado.');
    }

    public function destroy(Product $product): RedirectResponse
    {
        $this->authorize('delete', $product);
        $product->delete();

        return redirect()->route('seller.products.index')->with('status', 'Produto removido.');
    }

    public function toggle(Product $product): RedirectResponse
    {
        $this->authorize('update', $product);
        $product->update(['is_active' => ! $product->is_active]);

        return back()->with('status', $product->is_active ? 'Produto reativado.' : 'Produto pausado.');
    }

    /* --------------------------------------------------------------- helpers */

    private function validated(Request $request): array
    {
        return $request->validate([
            'title' => ['required', 'string', 'max:255'],
            'category_id' => ['required', 'exists:categories,id'],
            'description' => ['nullable', 'string'],
            'condition' => ['required', 'in:novo,seminovo,usado'],
            'condition_note' => ['nullable', 'string', 'max:255'],
            'brand' => ['nullable', 'string', 'max:255'],
            'size' => ['nullable', 'string', 'max:50'],
            'color' => ['nullable', 'string', 'max:50'],
            'price' => ['required', 'numeric', 'min:0', 'max:1000000'],
            'compare_at_price' => ['nullable', 'numeric', 'min:0', 'max:1000000'],
            'stock' => ['required', 'integer', 'min:0', 'max:100000'],
            'weight_grams' => ['required', 'integer', 'min:1', 'max:50000'],
            'length_cm' => ['required', 'integer', 'min:1', 'max:200'],
            'width_cm' => ['required', 'integer', 'min:1', 'max:200'],
            'height_cm' => ['required', 'integer', 'min:1', 'max:200'],
            'max_installments' => ['required', 'integer', 'min:1', 'max:12'],
            'free_shipping' => ['nullable', 'boolean'],
            'is_active' => ['nullable', 'boolean'],
            'images' => ['nullable', 'array', 'max:8'],
            'images.*' => ['image', 'mimes:jpeg,jpg,png,webp', 'max:8192'],
            'remove_images' => ['nullable', 'array'],
            'remove_images.*' => ['integer'],
            'gallery' => ['nullable', 'array', 'max:16'],
            'gallery.*' => ['string', 'max:40'],
            'gallery_primary' => ['nullable', 'string', 'max:40'],
        ]);
    }

    private function categoryOptions()
    {
        return Category::with('children')->roots()->orderBy('position')->get();
    }

    private function uniqueSlug(string $title): string
    {
        $base = Str::slug($title) ?: 'produto';
        $slug = $base;
        $i = 1;
        while (Product::where('slug', $slug)->exists()) {
            $slug = $base . '-' . (++$i);
        }

        return $slug;
    }

    /**
     * Sync the product gallery from uploaded files.
     *
     * Removes images the seller unchecked, stores newly uploaded files on the
     * public disk, and falls back to a generated placeholder if the gallery
     * would otherwise be empty.
     */
    private function syncImages(Product $product, Request $request): void
    {
        // 1. Remove images the seller marked for deletion (and their files).
        $remove = array_filter((array) $request->input('remove_images', []));
        if ($remove) {
            $product->images()->whereIn('id', $remove)->get()->each(function ($image) {
                $this->deleteStoredFile($image->path);
                $image->delete();
            });
        }

        // 2. Store newly uploaded files (local upload -> public disk). O índice do
        // arquivo enviado é referenciado pelos tokens "new:IDX" da galeria.
        $created = [];
        foreach (array_values((array) $request->file('images', [])) as $i => $file) {
            if (! $file || ! $file->isValid()) {
                continue;
            }

            // Reencoda para o tamanho usado na galeria/zoom (economia de disco).
            $stored = \App\Support\ImageOptimizer::store($file, 'products');

            $created[$i] = $product->images()->create([
                'path' => '/storage/' . $stored,
                'alt' => $product->title,
                'is_primary' => false,
                'position' => 900 + $i,                     // appended; normalized below
            ]);
        }

        // 3. Guarantee at least one image (generated placeholder).
        if ($product->images()->doesntExist()) {
            $product->images()->create([
                'path' => placeholder_image($product->slug, $product->title, 700, 700),
                'alt' => $product->title,
                'is_primary' => true,
                'position' => 0,
            ]);

            return;
        }

        // 4. Galeria do formulário: tokens "existing:ID" / "new:IDX" na ordem final
        // escolhida por arrastar-e-soltar; gallery_primary aponta o destaque (thumb).
        $byToken = [];
        foreach ($product->images()->get() as $image) {
            $byToken["existing:{$image->id}"] = $image;
        }
        foreach ($created as $i => $image) {
            $byToken["new:{$i}"] = $image;
        }

        $orderTokens = array_values(array_filter(
            (array) $request->input('gallery', []),
            fn ($t) => isset($byToken[$t]),
        ));

        if ($orderTokens) {
            $primaryToken = (string) $request->input('gallery_primary', '');
            $seen = [];
            $position = 0;
            foreach ($orderTokens as $token) {
                $image = $byToken[$token];
                if (isset($seen[$image->id])) {
                    continue;
                }
                $seen[$image->id] = true;
                $image->forceFill([
                    'position' => $position++,
                    'is_primary' => $token === $primaryToken,
                ])->save();
            }

            // Imagens fora da lista (não deveria ocorrer): vão para o fim.
            foreach ($product->images()->get() as $image) {
                if (! isset($seen[$image->id])) {
                    $image->forceFill(['position' => $position++, 'is_primary' => false])->save();
                }
            }

            // Garante exatamente um destaque.
            if (! $product->images()->where('is_primary', true)->exists()) {
                $product->images()->orderBy('position')->first()
                    ?->forceFill(['is_primary' => true])->save();
            }

            return;
        }

        // 4b. Sem JS: renormaliza e a primeira imagem vira a capa (comportamento clássico).
        $product->images()->orderBy('position')->orderBy('id')->get()
            ->each(fn ($image, $i) => $image->forceFill([
                'position' => $i,
                'is_primary' => $i === 0,
            ])->save());
    }

    /** Delete a locally-stored upload from the public disk (ignores external/placeholder URLs). */
    private function deleteStoredFile(?string $path): void
    {
        if ($path && str_starts_with($path, '/storage/')) {
            Storage::disk('public')->delete(substr($path, strlen('/storage/')));
        }
    }
}
