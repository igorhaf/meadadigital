<?php

namespace App\Http\Controllers;

use App\Services\Shipping\ShippingService;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;

class ShippingController extends Controller
{
    /**
     * Quote shipping options for a destination CEP and a set of cart lines.
     * GET so it needs no CSRF token: /api/frete/cotar?cep=..&items=[{"id":1,"qty":2}]
     */
    public function quote(Request $request, ShippingService $shipping): JsonResponse
    {
        $cep = preg_replace('/\D/', '', (string) $request->query('cep', ''));

        if (strlen($cep) !== 8) {
            return response()->json(['error' => 'CEP inválido', 'options' => []], 422);
        }

        $lines = collect(json_decode((string) $request->query('items', '[]'), true) ?: [])
            ->filter(fn ($i) => isset($i['id']) && (int) ($i['qty'] ?? 0) > 0)
            ->values();

        if ($lines->isEmpty()) {
            return response()->json(['options' => []]);
        }

        $package = $shipping->packageForLines($lines);
        $options = $shipping->quote($cep, $package, $package['subtotal']);

        return response()->json([
            'cep' => $cep,
            'options' => array_map(fn ($o) => [
                'id' => $o['id'],
                'carrier' => $o['carrier'],
                'service' => $o['service'],
                'label' => $o['label'],
                'price' => $o['price'],
                'days' => $o['days'],
            ], $options),
        ]);
    }
}
