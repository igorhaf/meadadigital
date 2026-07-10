<?php

use App\Models\SiteSetting;

if (! function_exists('money')) {
    /**
     * Format a numeric value as Brazilian Real, e.g. 1234.5 -> "R$ 1.234,50".
     */
    function money(int|float|string|null $value): string
    {
        return 'R$ ' . number_format((float) $value, 2, ',', '.');
    }
}

if (! function_exists('placeholder_image')) {
    /**
     * Build a URL to the self-contained SVG placeholder image generator.
     * Keeps the demo storefront hermetic (no external image hosts required).
     */
    function placeholder_image(string $seed, string $text = '', int $w = 600, int $h = 600): string
    {
        return '/ph?' . http_build_query([
            'seed' => $seed,
            'text' => $text !== '' ? $text : $seed,
            'w' => $w,
            'h' => $h,
        ]);
    }
}

if (! function_exists('settings')) {
    /**
     * Access the single site-settings row (managed by the root user later).
     */
    function settings(): SiteSetting
    {
        return SiteSetting::current();
    }
}
