<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Builder;
use Illuminate\Database\Eloquent\Model;
use Illuminate\Database\Eloquent\Relations\BelongsTo;
use Illuminate\Database\Eloquent\Relations\HasMany;

class Page extends Model
{
    public const SCOPE_SHARED = 'shared';
    public const SCOPE_PERSONAL = 'personal';

    public const KINDS = ['note', 'vault', 'calendar', 'tasks', 'registro', 'registro_item', 'meds', 'diet', 'gastos'];

    protected $fillable = [
        'parent_id',
        'owner_id',
        'scope',
        'kind',
        'title',
        'icon',
        'content',
        'meta',
        'position',
    ];

    protected function casts(): array
    {
        return ['meta' => 'array', 'is_system' => 'boolean'];
    }

    public function parent(): BelongsTo
    {
        return $this->belongsTo(Page::class, 'parent_id');
    }

    public function children(): HasMany
    {
        return $this->hasMany(Page::class, 'parent_id')->orderBy('position');
    }

    public function owner(): BelongsTo
    {
        return $this->belongsTo(User::class, 'owner_id');
    }

    public function vaultEntries(): HasMany
    {
        return $this->hasMany(VaultEntry::class)->orderBy('position');
    }

    public function events(): HasMany
    {
        return $this->hasMany(CalendarEvent::class)->orderBy('starts_at');
    }

    public function taskItems(): HasMany
    {
        return $this->hasMany(TaskItem::class)->orderBy('done')->orderBy('position');
    }

    public function medications(): HasMany
    {
        return $this->hasMany(Medication::class)->orderBy('person')->orderBy('name');
    }

    public function expenseEntries(): HasMany
    {
        return $this->hasMany(ExpenseEntry::class)->orderByDesc('date');
    }

    public function scopeShared(Builder $query): Builder
    {
        return $query->where('scope', self::SCOPE_SHARED);
    }

    public function scopePersonalOf(Builder $query, int $userId): Builder
    {
        return $query->where('scope', self::SCOPE_PERSONAL)->where('owner_id', $userId);
    }

    /** Todas as páginas que o usuário enxerga (compartilhadas + pessoais dele). */
    public function scopeAccessibleBy(Builder $query, User $user): Builder
    {
        return $query->where(fn (Builder $q) => $q
            ->where('scope', self::SCOPE_SHARED)
            ->orWhere(fn (Builder $q2) => $q2
                ->where('scope', self::SCOPE_PERSONAL)
                ->where('owner_id', $user->id)));
    }

    public function isAccessibleBy(User $user): bool
    {
        return $this->scope === self::SCOPE_SHARED || $this->owner_id === $user->id;
    }
}
