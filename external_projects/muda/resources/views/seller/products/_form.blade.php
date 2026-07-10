@php($conditions = ['novo' => 'Novo com etiqueta', 'seminovo' => 'Seminovo', 'usado' => 'Usado'])

<form method="POST" action="{{ $action }}" enctype="multipart/form-data" class="grid gap-6 lg:grid-cols-3">
    @csrf
    @isset($method) @method($method) @endisset

    @if ($errors->any())
        <div class="lg:col-span-3 rounded-xl bg-red-50 px-4 py-3 text-sm text-red-700">
            <ul class="list-inside list-disc space-y-0.5">
                @foreach ($errors->all() as $error)<li>{{ $error }}</li>@endforeach
            </ul>
        </div>
    @endif

    {{-- Main --}}
    <div class="space-y-6 lg:col-span-2">
        <div class="card space-y-4 p-6">
            <div>
                <label class="mb-1 block text-sm font-medium text-neutral-700">Título *</label>
                <input name="title" value="{{ old('title', $product->title) }}" required
                    class="w-full rounded-xl border border-neutral-300 px-4 py-2.5 text-sm focus:border-brand-400 focus:outline-none focus:ring-2 focus:ring-brand-100">
            </div>
            <div>
                <label class="mb-1 block text-sm font-medium text-neutral-700">Descrição</label>
                <textarea name="description" rows="4"
                    class="w-full rounded-xl border border-neutral-300 px-4 py-2.5 text-sm focus:border-brand-400 focus:outline-none focus:ring-2 focus:ring-brand-100">{{ old('description', $product->description) }}</textarea>
            </div>
            <div class="grid gap-4 sm:grid-cols-3">
                <div>
                    <label class="mb-1 block text-sm font-medium text-neutral-700">Marca</label>
                    <input name="brand" value="{{ old('brand', $product->brand) }}" class="w-full rounded-xl border border-neutral-300 px-4 py-2.5 text-sm focus:border-brand-400 focus:outline-none">
                </div>
                <div>
                    <label class="mb-1 block text-sm font-medium text-neutral-700">Tamanho</label>
                    <input name="size" value="{{ old('size', $product->size) }}" class="w-full rounded-xl border border-neutral-300 px-4 py-2.5 text-sm focus:border-brand-400 focus:outline-none">
                </div>
                <div>
                    <label class="mb-1 block text-sm font-medium text-neutral-700">Cor</label>
                    <input name="color" value="{{ old('color', $product->color) }}" class="w-full rounded-xl border border-neutral-300 px-4 py-2.5 text-sm focus:border-brand-400 focus:outline-none">
                </div>
            </div>
            <div>
                <label class="mb-1 block text-sm font-medium text-neutral-700">Observação sobre o estado</label>
                <input name="condition_note" value="{{ old('condition_note', $product->condition_note) }}" placeholder="Ex.: leve desgaste na barra"
                    class="w-full rounded-xl border border-neutral-300 px-4 py-2.5 text-sm focus:border-brand-400 focus:outline-none">
            </div>
        </div>

        <div class="card space-y-3 p-6">
            <label class="block text-sm font-medium text-neutral-700">Imagens do produto</label>

            @if($product->exists && $product->images->isNotEmpty())
                <div class="grid grid-cols-4 gap-3">
                    @foreach($product->images as $image)
                        <label class="group relative cursor-pointer overflow-hidden rounded-xl border border-neutral-200">
                            <img src="{{ $image->path }}" alt="" class="aspect-square w-full object-cover">
                            <span class="absolute inset-x-0 bottom-0 flex items-center gap-1 bg-neutral-900/70 px-2 py-1 text-[11px] font-medium text-white">
                                <input type="checkbox" name="remove_images[]" value="{{ $image->id }}" class="rounded border-white/50 text-red-500 focus:ring-0">
                                Remover
                            </span>
                            @if($image->is_primary)<span class="absolute left-1 top-1 chip bg-brand-600 text-white">Capa</span>@endif
                        </label>
                    @endforeach
                </div>
                <p class="text-xs text-neutral-400">Marque para remover. A primeira imagem é a capa.</p>
            @endif

            <input type="file" name="images[]" accept="image/png,image/jpeg,image/webp" multiple
                class="w-full rounded-xl border border-dashed border-neutral-300 px-4 py-3 text-sm text-neutral-600 file:mr-3 file:rounded-lg file:border-0 file:bg-brand-50 file:px-3 file:py-1.5 file:text-sm file:font-semibold file:text-brand-700 hover:border-brand-400">
            <p class="text-xs text-neutral-400">Envie fotos do seu computador (JPG, PNG ou WEBP, até 5&nbsp;MB cada). Sem imagens, geramos um placeholder colorido.</p>
        </div>
    </div>

    {{-- Sidebar --}}
    <div class="space-y-6">
        <div class="card space-y-4 p-6">
            <div>
                <label class="mb-1 block text-sm font-medium text-neutral-700">Categoria *</label>
                <select name="category_id" required class="w-full rounded-xl border border-neutral-300 px-3 py-2.5 text-sm focus:border-brand-400 focus:outline-none">
                    <option value="">Selecione…</option>
                    @foreach($categories as $root)
                        <optgroup label="{{ $root->name }}">
                            @foreach($root->children as $child)
                                <option value="{{ $child->id }}" @selected((int) old('category_id', $product->category_id) === $child->id)>{{ $child->name }}</option>
                            @endforeach
                        </optgroup>
                    @endforeach
                </select>
            </div>
            <div>
                <label class="mb-1 block text-sm font-medium text-neutral-700">Condição *</label>
                <select name="condition" class="w-full rounded-xl border border-neutral-300 px-3 py-2.5 text-sm focus:border-brand-400 focus:outline-none">
                    @foreach($conditions as $value => $label)
                        <option value="{{ $value }}" @selected(old('condition', $product->condition) === $value)>{{ $label }}</option>
                    @endforeach
                </select>
            </div>
        </div>

        <div class="card space-y-4 p-6">
            <div class="grid grid-cols-2 gap-3">
                <div>
                    <label class="mb-1 block text-sm font-medium text-neutral-700">Preço (R$) *</label>
                    <input name="price" type="number" step="0.01" min="0" value="{{ old('price', $product->price) }}" required class="w-full rounded-xl border border-neutral-300 px-3 py-2.5 text-sm focus:border-brand-400 focus:outline-none">
                </div>
                <div>
                    <label class="mb-1 block text-sm font-medium text-neutral-700">Preço "de"</label>
                    <input name="compare_at_price" type="number" step="0.01" min="0" value="{{ old('compare_at_price', $product->compare_at_price) }}" class="w-full rounded-xl border border-neutral-300 px-3 py-2.5 text-sm focus:border-brand-400 focus:outline-none">
                </div>
                <div>
                    <label class="mb-1 block text-sm font-medium text-neutral-700">Estoque *</label>
                    <input name="stock" type="number" min="0" value="{{ old('stock', $product->stock) }}" required class="w-full rounded-xl border border-neutral-300 px-3 py-2.5 text-sm focus:border-brand-400 focus:outline-none">
                </div>
                <div>
                    <label class="mb-1 block text-sm font-medium text-neutral-700">Máx. parcelas</label>
                    <input name="max_installments" type="number" min="1" max="12" value="{{ old('max_installments', $product->max_installments) }}" class="w-full rounded-xl border border-neutral-300 px-3 py-2.5 text-sm focus:border-brand-400 focus:outline-none">
                </div>
            </div>
            <label class="flex items-center gap-2 text-sm text-neutral-700">
                <input type="hidden" name="free_shipping" value="0">
                <input type="checkbox" name="free_shipping" value="1" @checked(old('free_shipping', $product->free_shipping)) class="rounded border-neutral-300 text-brand-600 focus:ring-brand-500">
                🚚 Oferecer frete grátis
            </label>
            <label class="flex items-center gap-2 text-sm text-neutral-700">
                <input type="hidden" name="is_active" value="0">
                <input type="checkbox" name="is_active" value="1" @checked(old('is_active', $product->is_active)) class="rounded border-neutral-300 text-brand-600 focus:ring-brand-500">
                Publicar (visível na loja)
            </label>
        </div>

        <div class="card space-y-3 p-6">
            <h3 class="text-sm font-bold text-neutral-800">📦 Dimensões para frete</h3>
            <div class="grid grid-cols-2 gap-3">
                <div>
                    <label class="mb-1 block text-xs font-medium text-neutral-600">Peso (g)</label>
                    <input name="weight_grams" type="number" min="1" value="{{ old('weight_grams', $product->weight_grams ?: 300) }}" required class="w-full rounded-xl border border-neutral-300 px-3 py-2 text-sm focus:border-brand-400 focus:outline-none">
                </div>
                <div>
                    <label class="mb-1 block text-xs font-medium text-neutral-600">Comprimento (cm)</label>
                    <input name="length_cm" type="number" min="1" value="{{ old('length_cm', $product->length_cm ?: 20) }}" required class="w-full rounded-xl border border-neutral-300 px-3 py-2 text-sm focus:border-brand-400 focus:outline-none">
                </div>
                <div>
                    <label class="mb-1 block text-xs font-medium text-neutral-600">Largura (cm)</label>
                    <input name="width_cm" type="number" min="1" value="{{ old('width_cm', $product->width_cm ?: 15) }}" required class="w-full rounded-xl border border-neutral-300 px-3 py-2 text-sm focus:border-brand-400 focus:outline-none">
                </div>
                <div>
                    <label class="mb-1 block text-xs font-medium text-neutral-600">Altura (cm)</label>
                    <input name="height_cm" type="number" min="1" value="{{ old('height_cm', $product->height_cm ?: 5) }}" required class="w-full rounded-xl border border-neutral-300 px-3 py-2 text-sm focus:border-brand-400 focus:outline-none">
                </div>
            </div>
            <p class="text-xs text-neutral-400">Usadas para calcular o frete (Correios, Jadlog, Loggi).</p>
        </div>

        <div class="flex gap-2">
            <button type="submit" class="btn-brand flex-1">{{ $submitLabel }}</button>
            <a href="{{ route('seller.products.index') }}" class="btn-outline">Cancelar</a>
        </div>
    </div>
</form>
