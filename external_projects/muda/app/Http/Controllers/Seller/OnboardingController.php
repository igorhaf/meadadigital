<?php

namespace App\Http\Controllers\Seller;

use App\Http\Controllers\Controller;
use App\Models\User;
use Illuminate\Contracts\View\View;
use Illuminate\Http\RedirectResponse;
use Illuminate\Http\Request;
use Illuminate\Support\Str;

class OnboardingController extends Controller
{
    public function create(): View|RedirectResponse
    {
        if (auth()->user()->isSeller()) {
            return redirect()->route('dashboard');
        }

        return view('seller.onboarding');
    }

    public function store(Request $request): RedirectResponse
    {
        $data = $request->validate([
            'store_name' => ['required', 'string', 'max:255'],
            'store_location' => ['nullable', 'string', 'max:255'],
            'store_bio' => ['nullable', 'string', 'max:500'],
        ]);

        $user = auth()->user();
        $user->update($data + [
            'is_seller' => true,
            'role' => $user->isRoot() ? 'root' : 'seller',
            'store_slug' => $this->uniqueSlug($data['store_name']),
        ]);

        return redirect()->route('dashboard')->with('status', 'Sua loja está no ar! Comece a cadastrar produtos.');
    }

    private function uniqueSlug(string $name): string
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
