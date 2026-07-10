<?php

namespace App\Services\Shipping\Carriers;

use App\Services\Shipping\Contracts\Carrier;
use App\Services\Shipping\MelhorEnvioService;
use Illuminate\Support\Facades\Http;

/**
 * Melhor Envio — agregador que cota várias transportadoras (Correios PAC/SEDEX,
 * Jadlog, Loggi, Azul Cargo…) numa única chamada. Token self-service (sandbox
 * grátis). Docs: https://docs.melhorenvio.com.br/
 */
class MelhorEnvioCarrier implements Carrier
{
    public function name(): string
    {
        return 'Melhor Envio';
    }

    public function enabled(): bool
    {
        return (bool) config('shipping.carriers.melhorenvio.enabled')
            && filled(app(MelhorEnvioService::class)->accessToken());
    }

    public function quote(string $destCep, array $package, float $subtotal): array
    {
        $service = app(MelhorEnvioService::class);
        $token = $service->accessToken();
        if (! $token) {
            return [];
        }

        $c = config('shipping.carriers.melhorenvio');
        $base = $service->baseUrl();
        $origin = preg_replace('/\D/', '', (string) config('shipping.origin_cep'));

        $response = Http::withToken($token)
            ->acceptJson()
            ->withHeaders(['User-Agent' => $c['user_agent'] ?: 'Muda (contato@muda.com.br)'])
            ->post($base . '/api/v2/me/shipment/calculate', [
                'from' => ['postal_code' => $origin],
                'to' => ['postal_code' => $destCep],
                'package' => [
                    'weight' => round(max(0.1, $package['weight'] / 1000), 3), // kg
                    'width' => (int) $package['width'],
                    'height' => (int) $package['height'],
                    'length' => (int) $package['length'],
                ],
                'options' => [
                    'insurance_value' => round($subtotal, 2),
                    'receipt' => false,
                    'own_hand' => false,
                ],
            ]);

        if (! $response->successful()) {
            return [];
        }

        $options = [];
        foreach ($response->json() ?: [] as $service) {
            // Serviços indisponíveis vêm com "error" e sem "price".
            if (! empty($service['error']) || empty($service['price'])) {
                continue;
            }

            $company = $service['company']['name'] ?? 'Transportadora';
            $days = (int) ($service['delivery_time'] ?? $service['delivery_range']['max'] ?? 0);

            $options[] = [
                'id' => 'me-' . $service['id'],
                'carrier' => $company,
                'service' => $service['name'] ?? 'Frete',
                'price' => (float) $service['price'],
                'days' => $days,
                'label' => trim($company . ' ' . ($service['name'] ?? '')),
            ];
        }

        return $options;
    }
}
