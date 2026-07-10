<?php

namespace App\Policies;

use App\Models\Product;
use App\Models\User;

class ProductPolicy
{
    /** Root manages everything; sellers manage only their own products. */
    public function manage(User $user, Product $product): bool
    {
        return $user->isRoot() || $product->seller_id === $user->id;
    }

    public function update(User $user, Product $product): bool
    {
        return $this->manage($user, $product);
    }

    public function delete(User $user, Product $product): bool
    {
        return $this->manage($user, $product);
    }
}
