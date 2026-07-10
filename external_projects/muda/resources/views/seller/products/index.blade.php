@extends('layouts.dashboard')

@section('title', 'Meus produtos')

@section('content')
    <div class="mb-6 flex flex-wrap items-center justify-between gap-3">
        <div>
            <h1 class="text-2xl font-extrabold text-neutral-900">Meus produtos</h1>
            <p class="text-sm text-neutral-500">{{ $products->total() }} produto(s) no seu catálogo</p>
        </div>
        <a href="{{ route('seller.products.create') }}" class="btn-brand">+ Cadastrar produto</a>
    </div>

    <form method="GET" class="mb-4">
        <input name="q" value="{{ request('q') }}" placeholder="Buscar nos meus produtos…"
            class="w-full max-w-sm rounded-xl border border-neutral-300 px-4 py-2.5 text-sm focus:border-brand-400 focus:outline-none focus:ring-2 focus:ring-brand-100">
    </form>

    <div class="card overflow-hidden">
        @if($products->isEmpty())
            <div class="flex flex-col items-center justify-center py-16 text-center">
                <div class="text-5xl">📦</div>
                <p class="mt-4 font-semibold text-neutral-700">Nenhum produto ainda</p>
                <p class="mt-1 text-sm text-neutral-500">Cadastre sua primeira peça e comece a vender.</p>
                <a href="{{ route('seller.products.create') }}" class="btn-brand mt-6">+ Cadastrar produto</a>
            </div>
        @else
            <div class="overflow-x-auto">
                <table class="w-full text-sm">
                    <thead class="border-b border-neutral-200 bg-neutral-50 text-left text-xs uppercase tracking-wide text-neutral-500">
                        <tr>
                            <th class="px-4 py-3">Produto</th>
                            <th class="px-4 py-3">Preço</th>
                            <th class="px-4 py-3">Estoque</th>
                            <th class="px-4 py-3">Status</th>
                            <th class="px-4 py-3 text-right">Ações</th>
                        </tr>
                    </thead>
                    <tbody class="divide-y divide-neutral-100">
                        @foreach($products as $product)
                            <tr class="hover:bg-neutral-50">
                                <td class="px-4 py-3">
                                    <div class="flex items-center gap-3">
                                        <img src="{{ $product->primary_image_url }}" alt="" class="h-12 w-12 shrink-0 rounded-lg object-cover">
                                        <div class="min-w-0">
                                            <a href="{{ $product->url }}" class="line-clamp-1 font-medium text-neutral-800 hover:text-brand-700">{{ $product->title }}</a>
                                            <p class="text-xs text-neutral-400">{{ $product->condition_label }}{{ $product->brand ? ' · '.$product->brand : '' }}</p>
                                        </div>
                                    </div>
                                </td>
                                <td class="px-4 py-3 font-semibold text-neutral-800">{{ money($product->price) }}</td>
                                <td class="px-4 py-3">{{ $product->stock }}</td>
                                <td class="px-4 py-3">
                                    @if($product->is_active)
                                        <span class="chip bg-brand-50 text-brand-700">Ativo</span>
                                    @else
                                        <span class="chip bg-neutral-100 text-neutral-500">Pausado</span>
                                    @endif
                                </td>
                                <td class="px-4 py-3">
                                    <div class="flex items-center justify-end gap-1">
                                        <a href="{{ route('seller.products.edit', $product) }}" class="rounded-lg px-2.5 py-1.5 text-xs font-semibold text-brand-700 hover:bg-brand-50">Editar</a>
                                        <form method="POST" action="{{ route('seller.products.toggle', $product) }}">
                                            @csrf
                                            <button class="rounded-lg px-2.5 py-1.5 text-xs font-medium text-neutral-500 hover:bg-neutral-100">{{ $product->is_active ? 'Pausar' : 'Ativar' }}</button>
                                        </form>
                                        <form method="POST" action="{{ route('seller.products.destroy', $product) }}" onsubmit="return confirm('Remover este produto?')">
                                            @csrf @method('DELETE')
                                            <button class="rounded-lg px-2.5 py-1.5 text-xs font-medium text-red-500 hover:bg-red-50">Excluir</button>
                                        </form>
                                    </div>
                                </td>
                            </tr>
                        @endforeach
                    </tbody>
                </table>
            </div>
        @endif
    </div>

    <div class="mt-6">{{ $products->onEachSide(1)->links() }}</div>
@endsection
