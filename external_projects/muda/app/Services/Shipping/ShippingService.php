<?php

namespace App\Services\Shipping;

use App\Models\Product;
use App\Services\Shipping\Carriers\EstimateCarrier;
use App\Services\Shipping\Carriers\MelhorEnvioCarrier;
use Illuminate\Support\Collection;

class ShippingService
{
    /** @return \App\Services\Shipping\Contracts\Carrier[] */
    private function carriers(): array
    {
        // Melhor Envio agrega Correios (PAC/SEDEX), Jadlog, Loggi e outras.
        return [
            app(MelhorEnvioCarrier::class),
        ];
    }

    /**
     * Quote all configured carriers for a destination CEP + package.
     * Falls back to an estimate (ViaCEP + weight) when nothing is configured.
     *
     * @return array<int,array<string,mixed>>
     */
    public function quote(string $destCep, array $package, float $subtotal): array
    {
        $destCep = preg_replace('/\D/', '', $destCep) ?? '';

        if (strlen($destCep) !== 8) {
            return [];
        }

        $options = [];
        foreach ($this->carriers() as $carrier) {
            if (! $carrier->enabled()) {
                continue;
            }
            try {
                $options = array_merge($options, $carrier->quote($destCep, $package, $subtotal));
            } catch (\Throwable $e) {
                report($e);
            }
        }

        if (empty($options) && config('shipping.fallback')) {
            $options = app(EstimateCarrier::class)->quote($destCep, $package, $subtotal);
        }

        // Frete grátis acima do limite: adiciona uma opção grátis (mais econômica).
        $freeAbove = config('shipping.free_above');
        if ($freeAbove && $subtotal >= (float) $freeAbove && ! empty($options)) {
            $days = collect($options)->min('days') ?: 8;
            $options[] = [
                'id' => 'gratis',
                'carrier' => 'Muda',
                'service' => 'Frete grátis',
                'price' => 0.0,
                'days' => (int) $days,
                'label' => 'Frete grátis',
            ];
        }

        usort($options, fn ($a, $b) => $a['price'] <=> $b['price']);

        return $options;
    }

    /**
     * Build a shipping package from cart lines [{id, qty}].
     *
     * @param  Collection<int,array{id:int,qty:int}>  $lines
     * @return array{weight:int,length:int,width:int,height:int,subtotal:float}
     */
    public function packageForLines(Collection $lines): array
    {
        $products = Product::whereIn('id', $lines->pluck('id'))->get()->keyBy('id');
        $default = config('shipping.default_package');

        $weight = 0;
        $length = $default['length'];
        $width = $default['width'];
        $height = 0;
        $subtotal = 0.0;

        foreach ($lines as $line) {
            $product = $products->get((int) $line['id']);
            if (! $product) {
                continue;
            }
            $qty = max(1, (int) $line['qty']);
            $weight += ($product->weight_grams ?: $default['weight']) * $qty;
            $length = max($length, $product->length_cm ?: $default['length']);
            $width = max($width, $product->width_cm ?: $default['width']);
            $height += ($product->height_cm ?: $default['height']) * $qty;
            $subtotal += (float) $product->price * $qty;
        }

        return [
            'weight' => max(100, $weight),
            'length' => min(100, max(16, $length)),
            'width' => min(100, max(11, $width)),
            'height' => min(100, max(2, $height)),
            'subtotal' => $subtotal,
        ];
    }
}
