<form method="POST" action="{{ $action }}" class="mx-auto max-w-2xl space-y-6">
    @csrf
    @isset($method) @method($method) @endisset

    @if ($errors->any())
        <div class="rounded-xl bg-red-50 px-4 py-3 text-sm text-red-700">
            <ul class="list-inside list-disc">@foreach ($errors->all() as $e)<li>{{ $e }}</li>@endforeach</ul>
        </div>
    @endif

    <div class="card space-y-4 p-6">
        <div>
            <label class="mb-1 block text-sm font-medium text-neutral-700">Título *</label>
            <input name="title" value="{{ old('title', $banner->title) }}" required class="w-full rounded-xl border border-neutral-300 px-4 py-2.5 text-sm focus:border-brand-400 focus:outline-none">
        </div>
        <div>
            <label class="mb-1 block text-sm font-medium text-neutral-700">Subtítulo</label>
            <input name="subtitle" value="{{ old('subtitle', $banner->subtitle) }}" class="w-full rounded-xl border border-neutral-300 px-4 py-2.5 text-sm focus:border-brand-400 focus:outline-none">
        </div>
        <div class="grid gap-4 sm:grid-cols-2">
            <div>
                <label class="mb-1 block text-sm font-medium text-neutral-700">Texto do botão</label>
                <input name="cta_label" value="{{ old('cta_label', $banner->cta_label) }}" class="w-full rounded-xl border border-neutral-300 px-4 py-2.5 text-sm focus:border-brand-400 focus:outline-none">
            </div>
            <div>
                <label class="mb-1 block text-sm font-medium text-neutral-700">Link (URL)</label>
                <input name="link_url" value="{{ old('link_url', $banner->link_url) }}" placeholder="/categoria/tenis" class="w-full rounded-xl border border-neutral-300 px-4 py-2.5 text-sm focus:border-brand-400 focus:outline-none">
            </div>
        </div>
        <div class="grid gap-4 sm:grid-cols-4">
            <div>
                <label class="mb-1 block text-sm font-medium text-neutral-700">Cor inicial</label>
                <input name="bg_from" type="color" value="{{ old('bg_from', $banner->bg_from) }}" class="h-11 w-full rounded-xl border border-neutral-300">
            </div>
            <div>
                <label class="mb-1 block text-sm font-medium text-neutral-700">Cor final</label>
                <input name="bg_to" type="color" value="{{ old('bg_to', $banner->bg_to) }}" class="h-11 w-full rounded-xl border border-neutral-300">
            </div>
            <div>
                <label class="mb-1 block text-sm font-medium text-neutral-700">Posição</label>
                <select name="placement" class="w-full rounded-xl border border-neutral-300 px-3 py-2.5 text-sm focus:border-brand-400 focus:outline-none">
                    <option value="hero" @selected(old('placement', $banner->placement) === 'hero')>Hero</option>
                    <option value="strip" @selected(old('placement', $banner->placement) === 'strip')>Tile</option>
                </select>
            </div>
            <div>
                <label class="mb-1 block text-sm font-medium text-neutral-700">Ordem</label>
                <input name="position" type="number" min="0" value="{{ old('position', $banner->position) }}" class="w-full rounded-xl border border-neutral-300 px-3 py-2.5 text-sm focus:border-brand-400 focus:outline-none">
            </div>
        </div>
        <label class="flex items-center gap-2 text-sm text-neutral-700">
            <input type="hidden" name="is_active" value="0">
            <input type="checkbox" name="is_active" value="1" @checked(old('is_active', $banner->is_active)) class="rounded border-neutral-300 text-brand-600 focus:ring-brand-500">
            Ativo (visível na home)
        </label>
    </div>

    <div class="flex gap-2">
        <button class="btn-brand">{{ $submitLabel }}</button>
        <a href="{{ route('admin.banners.index') }}" class="btn-outline">Cancelar</a>
    </div>
</form>
