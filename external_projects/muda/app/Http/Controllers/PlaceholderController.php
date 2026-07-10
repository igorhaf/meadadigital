<?php

namespace App\Http\Controllers;

use Illuminate\Http\Request;
use Illuminate\Http\Response;

/**
 * Generates deterministic, self-contained SVG placeholder images so the demo
 * storefront renders without any external image host. Colour is derived from
 * the seed, so a given product always gets the same look.
 */
class PlaceholderController extends Controller
{
    public function show(Request $request): Response
    {
        $seed = (string) $request->query('seed', 'muda');
        $text = trim((string) $request->query('text', $seed));
        $w = (int) min(1600, max(80, (int) $request->query('w', 600)));
        $h = (int) min(1600, max(80, (int) $request->query('h', 600)));

        $hue = crc32($seed) % 360;
        $hue2 = ($hue + 40) % 360;

        $lines = $this->wrap($text, 16);
        $fontSize = $w >= 400 ? 34 : 24;
        $lineHeight = $fontSize * 1.2;
        $blockHeight = count($lines) * $lineHeight;
        $startY = ($h / 2) - ($blockHeight / 2) + $fontSize;

        $tspans = '';
        foreach ($lines as $i => $line) {
            $y = $startY + $i * $lineHeight;
            $tspans .= sprintf(
                '<tspan x="%d" y="%.1f">%s</tspan>',
                (int) ($w / 2),
                $y,
                htmlspecialchars($line, ENT_QUOTES)
            );
        }

        $svg = <<<SVG
        <svg xmlns="http://www.w3.org/2000/svg" width="{$w}" height="{$h}" viewBox="0 0 {$w} {$h}" role="img">
          <defs>
            <linearGradient id="g" x1="0" y1="0" x2="1" y2="1">
              <stop offset="0" stop-color="hsl({$hue}, 62%, 58%)"/>
              <stop offset="1" stop-color="hsl({$hue2}, 58%, 42%)"/>
            </linearGradient>
          </defs>
          <rect width="{$w}" height="{$h}" fill="url(#g)"/>
          <circle cx="{$w}" cy="0" r="{$h}" fill="#ffffff" opacity="0.08"/>
          <circle cx="0" cy="{$h}" r="{$h}" fill="#000000" opacity="0.06"/>
          <text text-anchor="middle" font-family="'Segoe UI', system-ui, sans-serif"
                font-size="{$fontSize}" font-weight="700" fill="#ffffff" opacity="0.95">
            {$tspans}
          </text>
        </svg>
        SVG;

        return response($svg, 200, [
            'Content-Type' => 'image/svg+xml',
            'Cache-Control' => 'public, max-age=31536000, immutable',
        ]);
    }

    /**
     * Naive word wrap into lines no longer than $limit characters.
     *
     * @return array<int,string>
     */
    private function wrap(string $text, int $limit): array
    {
        $words = preg_split('/\s+/', $text) ?: [];
        $lines = [];
        $current = '';

        foreach ($words as $word) {
            $candidate = $current === '' ? $word : "$current $word";
            if (mb_strlen($candidate) > $limit && $current !== '') {
                $lines[] = $current;
                $current = $word;
            } else {
                $current = $candidate;
            }
        }

        if ($current !== '') {
            $lines[] = $current;
        }

        return array_slice($lines ?: [$text], 0, 4);
    }
}
