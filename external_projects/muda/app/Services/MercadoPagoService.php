<?php

namespace App\Services;

use App\Models\Order;
use Illuminate\Support\Facades\Http;
use Illuminate\Support\Str;

/**
 * Thin wrapper around the Mercado Pago REST API for Checkout Pro.
 * Credentials live in config/services.php (fed by the MP_* env vars).
 */
class MercadoPagoService
{
    private const API = 'https://api.mercadopago.com';

    public function enabled(): bool
    {
        return (bool) config('services.mercadopago.enabled')
            && filled($this->token())
            && filled($this->publicKey());
    }

    public function token(): ?string
    {
        return config('services.mercadopago.access_token');
    }

    public function publicKey(): ?string
    {
        return config('services.mercadopago.public_key');
    }

    public function isSandbox(): bool
    {
        return Str::startsWith((string) $this->token(), 'TEST-');
    }

    public function environmentLabel(): string
    {
        return $this->isSandbox() ? 'Teste (sandbox)' : 'Produção';
    }

    private function baseUrl(): string
    {
        return rtrim((string) (config('services.mercadopago.base_url') ?: config('app.url')), '/');
    }

    /** back_urls/auto_return só valem com URL pública (não localhost). */
    private function hasPublicBase(): bool
    {
        return ! Str::contains($this->baseUrl(), ['localhost', '127.0.0.1', '::1']);
    }

    /**
     * Create a Checkout Pro preference for an order and return the MP response
     * (includes id, init_point and sandbox_init_point).
     *
     * @return array<string,mixed>
     */
    public function createPreference(Order $order): array
    {
        $base = $this->baseUrl();

        $items = $order->items->map(fn ($item) => [
            'id' => (string) $item->product_id,
            'title' => Str::limit($item->title, 250),
            'quantity' => (int) $item->qty,
            'unit_price' => (float) $item->price,
            'currency_id' => 'BRL',
            'picture_url' => $this->absoluteUrl($item->image_path, $base),
        ])->values()->all();

        if ((float) $order->shipping > 0) {
            $items[] = [
                'id' => 'frete',
                'title' => 'Frete',
                'quantity' => 1,
                'unit_price' => (float) $order->shipping,
                'currency_id' => 'BRL',
            ];
        }

        $payload = [
            'items' => $items,
            'payer' => [
                'name' => $order->buyer_name,
                'email' => $order->buyer_email,
            ],
            'external_reference' => $order->reference,
            'statement_descriptor' => 'MUDA',
            'metadata' => ['order_id' => $order->id],
            'back_urls' => [
                'success' => "{$base}/checkout/retorno?resultado=sucesso",
                'pending' => "{$base}/checkout/retorno?resultado=pendente",
                'failure' => "{$base}/checkout/retorno?resultado=falha",
            ],
            'notification_url' => "{$base}/webhooks/mercadopago",
        ];

        // auto_return exige back_url pública; só habilita fora de localhost.
        if ($this->hasPublicBase()) {
            $payload['auto_return'] = 'approved';
        }

        $response = Http::withToken($this->token())
            ->acceptJson()
            ->withHeaders(['X-Idempotency-Key' => $order->reference])
            ->post(self::API . '/checkout/preferences', $payload);

        $response->throw();

        return $response->json();
    }

    /**
     * Checkout Transparente — create a payment from the Payment Brick formData.
     * Amount and external_reference are set server-side (never trusted from the
     * client). $data is the Brick payload (token, payment_method_id, payer…).
     *
     * @param  array<string,mixed>  $data
     * @return array<string,mixed>
     */
    public function createPayment(Order $order, array $data): array
    {
        $base = $this->baseUrl();

        $payload = array_merge($data, [
            'transaction_amount' => (float) $order->total,
            'description' => 'Pedido ' . $order->reference,
            'external_reference' => $order->reference,
            'metadata' => ['order_id' => $order->id],
        ]);

        // notification_url só é aceita se for pública (não localhost).
        if ($this->hasPublicBase()) {
            $payload['notification_url'] = "{$base}/webhooks/mercadopago";
        }

        $response = Http::withToken($this->token())
            ->acceptJson()
            ->withHeaders(['X-Idempotency-Key' => $order->reference . '-' . Str::random(8)])
            ->post(self::API . '/v1/payments', $payload);

        $response->throw();

        return $response->json();
    }

    /**
     * Fetch a payment from MP by id. Source of truth for order status —
     * we re-query MP instead of trusting the webhook body.
     *
     * @return array<string,mixed>|null
     */
    public function getPayment(string $paymentId): ?array
    {
        $response = Http::withToken($this->token())->acceptJson()
            ->get(self::API . "/v1/payments/{$paymentId}");

        return $response->successful() ? $response->json() : null;
    }

    private function absoluteUrl(?string $path, string $base): ?string
    {
        if (! $path) {
            return null;
        }

        return Str::startsWith($path, ['http://', 'https://']) ? $path : $base . $path;
    }
}
