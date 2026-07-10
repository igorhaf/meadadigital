<?php

namespace App\Http\Controllers\Auth;

use App\Http\Controllers\Controller;
use App\Models\User;
use Illuminate\Contracts\View\View;
use Illuminate\Http\RedirectResponse;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\Auth;
use Illuminate\Support\Str;
use Illuminate\Validation\Rules\Password;

class RegisterController extends Controller
{
    public function create(): View
    {
        return view('auth.register');
    }

    public function store(Request $request): RedirectResponse
    {
        $data = $request->validate([
            'name' => ['required', 'string', 'max:255'],
            'email' => ['required', 'email', 'max:255', 'unique:users,email'],
            'password' => ['required', 'confirmed', Password::min(8)],
            'become_seller' => ['nullable', 'boolean'],
            'store_name' => ['nullable', 'required_if:become_seller,1', 'string', 'max:255'],
        ]);

        $isSeller = config('muda.selling_enabled') && (bool) ($data['become_seller'] ?? false);

        $user = User::create([
            'name' => $data['name'],
            'email' => $data['email'],
            'password' => $data['password'],   // hashed via the model cast
            'role' => $isSeller ? 'seller' : 'customer',
            'is_seller' => $isSeller,
            'store_name' => $isSeller ? $data['store_name'] : null,
            'store_slug' => $isSeller ? $this->uniqueStoreSlug($data['store_name']) : null,
        ]);

        Auth::login($user);
        $request->session()->regenerate();

        return redirect()->intended($isSeller ? route('dashboard') : route('home'));
    }

    private function uniqueStoreSlug(string $name): string
    {
        $base = Str::slug($name) ?: 'loja';
        $slug = $base;
        $i = 1;

        while (User::where('store_slug', $slug)->exists()) {
            $slug = $base . '-' . (++$i);
        }

        return $slug;
    }
}
