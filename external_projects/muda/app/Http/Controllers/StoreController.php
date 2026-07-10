<?php

namespace App\Http\Controllers;

use App\Models\User;
use Illuminate\Contracts\View\View;

class StoreController extends Controller
{
    /** Public storefront for a single seller (tenant). */
    public function show(User $user): View
    {
        abort_unless($user->is_seller, 404);

        $products = $user->products()->active()->with('images')->latest()->paginate(24);

        return view('stores.show', compact('user', 'products'));
    }
}
