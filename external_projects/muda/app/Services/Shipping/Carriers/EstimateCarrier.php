<?php

namespace App\Services\Shipping\Carriers;

use App\Services\Shipping\Contracts\Carrier;
use Illuminate\Support\Facades\Cache;
use Illuminate\Support\Facades\Http;

/**
 * Estimador usado quando nenhuma transportadora está configurada.
 * Usa o ViaCEP (público) para descobrir a UF de destino e estima PAC/SEDEX
 * por zona de distância + peso. Mantém a loja funcional sem contratos.
 */
class EstimateCarrier implements Carrier
{
    /** UF => zona de distância a partir de SP (origem). */
    private const ZONES = [
        'SP' => 1,
        'RJ' => 2, 'MG' => 2, 'PR' => 2,
        'ES' => 3, 'SC' => 3, 'RS' => 3, 'GO' => 3, 'DF' => 3, 'MS' => 3, 'MT' => 3,
        'BA' => 4, 'SE' => 4, 'AL' => 4, 'PE' => 4, 'PB' => 4, 'RN' => 4, 'CE' => 4, 'PI' => 4, 'TO' => 4,
        'MA' => 5, 'PA' => 5, 'AP' => 5, 'AM' => 5, 'RR' => 5, 'AC' => 5, 'RO' => 5,
    ];

    private const PAC_BASE = [1 => 15.0, 2 => 21.0, 3 => 28.0, 4 => 36.0, 5 => 46.0];
    private const PAC_DAYS = [1 => 3, 2 => 5, 3 => 7, 4 => 9, 5 => 12];

    public function name(): string
    {
        return 'Estimativa';
    }

    public function enabled(): bool
    {
        return true;
    }

    public function quote(string $destCep, array $package, float $subtotal): array
    {
        $zone = self::ZONES[$this->ufFromCep($destCep)] ?? 3;
        $kg = max(0.3, $package['weight'] / 1000);
        $extraKg = max(0, ceil($kg - 0.3));

        $pac = round(self::PAC_BASE[$zone] + $extraKg * 6.5, 2);
        $sedex = round($pac * 1.65, 2);
        $pacDays = self::PAC_DAYS[$zone];
        $sedexDays = max(1, (int) ceil($pacDays / 2));

        return [
            ['id' => 'est-pac', 'carrier' => 'Estimativa', 'service' => 'Econômico (PAC)', 'price' => $pac, 'days' => $pacDays, 'label' => 'Econômico · estimativa'],
            ['id' => 'est-sedex', 'carrier' => 'Estimativa', 'service' => 'Expresso (SEDEX)', 'price' => $sedex, 'days' => $sedexDays, 'label' => 'Expresso · estimativa'],
        ];
    }

    private function ufFromCep(string $cep): ?string
    {
        return Cache::remember("viacep:uf:{$cep}", now()->addDay(), function () use ($cep) {
            try {
                $response = Http::timeout(4)->acceptJson()->get("https://viacep.com.br/ws/{$cep}/json/");
                if ($response->successful() && ! ($response->json('erro') ?? false)) {
                    return $response->json('uf');
                }
            } catch (\Throwable $e) {
                // rede indisponível → zona padrão
            }

            return null;
        });
    }
}
