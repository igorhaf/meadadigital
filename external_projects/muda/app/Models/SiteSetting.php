<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Model;

class SiteSetting extends Model
{
    protected $fillable = [
        'site_name', 'tagline', 'announcement',
        'instagram_url', 'facebook_url', 'tiktok_url', 'twitter_url', 'whatsapp',
        'contact_email', 'contact_phone', 'about',
    ];

    /** In-request memoized single settings row. */
    protected static ?SiteSetting $current = null;

    /**
     * The single settings row (created/edited by the root user in a later phase).
     */
    public static function current(): self
    {
        return static::$current ??= static::query()->first() ?? new static([
            'site_name' => 'Muda',
            'tagline' => 'Brechó marketplace',
        ]);
    }

    /**
     * Social links present on this row, keyed for easy iteration in Blade.
     *
     * @return array<string,string>
     */
    public function socialLinks(): array
    {
        return array_filter([
            'instagram' => $this->instagram_url,
            'facebook' => $this->facebook_url,
            'tiktok' => $this->tiktok_url,
            'twitter' => $this->twitter_url,
            'whatsapp' => $this->whatsapp ? 'https://wa.me/' . preg_replace('/\D/', '', $this->whatsapp) : null,
        ]);
    }
}
