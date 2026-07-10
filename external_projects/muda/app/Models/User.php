<?php

namespace App\Models;

// use Illuminate\Contracts\Auth\MustVerifyEmail;
use Database\Factories\UserFactory;
use Illuminate\Database\Eloquent\Attributes\Fillable;
use Illuminate\Database\Eloquent\Attributes\Hidden;
use Illuminate\Database\Eloquent\Factories\HasFactory;
use Illuminate\Database\Eloquent\Relations\HasMany;
use Illuminate\Foundation\Auth\User as Authenticatable;
use Illuminate\Notifications\Notifiable;

#[Fillable(['name', 'email', 'password', 'role', 'is_seller', 'store_name', 'store_slug', 'store_bio', 'store_location', 'google_id', 'avatar'])]
#[Hidden(['password', 'remember_token'])]
class User extends Authenticatable
{
    /** @use HasFactory<UserFactory> */
    use HasFactory, Notifiable;

    protected function casts(): array
    {
        return [
            'email_verified_at' => 'datetime',
            'password' => 'hashed',
            'is_seller' => 'boolean',
        ];
    }

    /* ---------------------------------------------------------------- Relations */

    /** Products this user sells (their tenant catalog). */
    public function products(): HasMany
    {
        return $this->hasMany(Product::class, 'seller_id');
    }

    /** Orders this user placed as a customer. */
    public function orders(): HasMany
    {
        return $this->hasMany(Order::class);
    }

    /** Line items sold by this user (across buyers' orders). */
    public function sales(): HasMany
    {
        return $this->hasMany(OrderItem::class, 'seller_id');
    }

    /* ------------------------------------------------------------------- Roles */

    public function isRoot(): bool
    {
        return $this->role === 'root';
    }

    public function isSeller(): bool
    {
        return $this->is_seller || $this->isRoot();
    }

    public function getStoreUrlAttribute(): ?string
    {
        return $this->store_slug ? route('stores.show', $this->store_slug) : null;
    }

    public function getInitialAttribute(): string
    {
        return mb_strtoupper(mb_substr($this->store_name ?: $this->name, 0, 1));
    }
}
