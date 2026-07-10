<?php

namespace App\Http\Controllers\Seller;

use App\Http\Controllers\Controller;
use Illuminate\Contracts\View\View;
use Illuminate\Http\RedirectResponse;
use Illuminate\Http\Request;

class StoreController extends Controller
{
    public function edit(): View
    {
        return view('seller.store', ['user' => auth()->user()]);
    }

    public function update(Request $request): RedirectResponse
    {
        $data = $request->validate([
            'store_name' => ['required', 'string', 'max:255'],
            'store_location' => ['nullable', 'string', 'max:255'],
            'store_bio' => ['nullable', 'string', 'max:500'],
        ]);

        auth()->user()->update($data);

        return redirect()->route('seller.store.edit')->with('status', 'Loja atualizada com sucesso.');
    }
}
