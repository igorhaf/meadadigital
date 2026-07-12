<?php

namespace App\Support;

use Illuminate\Http\UploadedFile;
use Illuminate\Support\Facades\Storage;
use Illuminate\Support\Str;

/**
 * Reencoda uploads de imagem para o tamanho que o site realmente usa
 * (cards da galeria + zoom/lupa): corrige a rotação EXIF, limita o lado
 * maior e comprime. Evita gravar fotos de celular de 5-8MB no disco —
 * a saída típica fica em 100-400KB.
 *
 * Sem dependência externa: usa somente a extensão GD (com fallback para
 * gravar o arquivo original caso o GD não esteja disponível).
 */
class ImageOptimizer
{
    /** Lado maior máximo (px) — suficiente para o zoom da galeria. */
    private const MAX_DIMENSION = 1600;

    private const JPEG_QUALITY = 82;

    private const WEBP_QUALITY = 80;

    /**
     * Otimiza e grava o upload no disco `public`, dentro de $dir.
     * Retorna o caminho relativo gravado (ex.: "products/aB3xY9….jpg").
     */
    public static function store(UploadedFile $file, string $dir): string
    {
        if (! extension_loaded('gd')) {
            return $file->store($dir, 'public');
        }

        [$image, $keepAlpha] = self::load($file);
        if ($image === null) {
            return $file->store($dir, 'public');
        }

        $image = self::applyExifOrientation($image, $file);
        $image = self::scaleDown($image, $keepAlpha);

        ob_start();
        if ($keepAlpha) {
            imagesavealpha($image, true);
            imagewebp($image, null, self::WEBP_QUALITY);
            $ext = 'webp';
        } else {
            imageinterlace($image, true);
            imagejpeg($image, null, self::JPEG_QUALITY);
            $ext = 'jpg';
        }
        $bytes = ob_get_clean();
        imagedestroy($image);

        if ($bytes === false || $bytes === '') {
            return $file->store($dir, 'public');
        }

        $path = trim($dir, '/') . '/' . Str::random(25) . '.' . $ext;
        Storage::disk('public')->put($path, $bytes);

        return $path;
    }

    /**
     * Carrega o upload como recurso GD.
     *
     * @return array{0: \GdImage|null, 1: bool} imagem + se deve preservar alpha
     */
    private static function load(UploadedFile $file): array
    {
        $path = $file->getRealPath();
        $info = $path ? @getimagesize($path) : false;
        if ($info === false) {
            return [null, false];
        }

        switch ($info[2]) {
            case IMAGETYPE_JPEG:
                $image = @imagecreatefromjpeg($path);

                return [$image ?: null, false];

            case IMAGETYPE_PNG:
                $image = @imagecreatefrompng($path);
                if (! $image) {
                    return [null, false];
                }
                imagepalettetotruecolor($image);
                imagealphablending($image, false);
                imagesavealpha($image, true);

                return [$image, true];

            case IMAGETYPE_WEBP:
                if (! function_exists('imagecreatefromwebp')) {
                    return [null, false];
                }
                $image = @imagecreatefromwebp($path);
                if ($image) {
                    imagealphablending($image, false);
                    imagesavealpha($image, true);
                }

                return [$image ?: null, true];

            default:
                return [null, false];
        }
    }

    /** Aplica a rotação indicada pelo EXIF (fotos de celular em retrato). */
    private static function applyExifOrientation(\GdImage $image, UploadedFile $file): \GdImage
    {
        if ($file->getMimeType() !== 'image/jpeg' || ! function_exists('exif_read_data')) {
            return $image;
        }

        $exif = @exif_read_data($file->getRealPath());
        $angle = match ((int) ($exif['Orientation'] ?? 1)) {
            3 => 180,
            6 => -90,
            8 => 90,
            default => 0,
        };

        if ($angle !== 0 && ($rotated = imagerotate($image, $angle, 0))) {
            imagedestroy($image);

            return $rotated;
        }

        return $image;
    }

    /** Reduz para MAX_DIMENSION no lado maior (nunca amplia). */
    private static function scaleDown(\GdImage $image, bool $keepAlpha): \GdImage
    {
        $w = imagesx($image);
        $h = imagesy($image);
        $longest = max($w, $h);

        if ($longest <= self::MAX_DIMENSION) {
            return $image;
        }

        $ratio = self::MAX_DIMENSION / $longest;
        $scaled = imagescale($image, (int) round($w * $ratio), (int) round($h * $ratio), IMG_BICUBIC);
        if (! $scaled) {
            return $image;
        }

        imagedestroy($image);
        if ($keepAlpha) {
            imagealphablending($scaled, false);
            imagesavealpha($scaled, true);
        }

        return $scaled;
    }
}
