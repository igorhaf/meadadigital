<?php

namespace App\Services\Shipping;

use App\Models\IntegrationToken;
use Illuminate\Support\Carbon;
use Illuminate\Support\Facades\Http;

/**
 * OAuth2 do Melhor Envio: gera a URL de autorização, troca o code por token,
 * renova automaticamente e entrega o access token válido para as cotações.
 */
class MelhorEnvioService
{
    private const PROVIDER = 'melhorenvio';
    private const SCOPE = 'shipping-calculate';

    public function baseUrl(): string
    {
        return config('shipping.carriers.melhorenvio.sandbox')
            ? 'https://sandbox.melhorenvio.com.br'
            : 'https://www.melhorenvio.com.br';
    }

    public function configured(): bool
    {
        $c = config('shipping.carriers.melhorenvio');

        return filled($c['client_id']) && filled($c['client_secret']) && filled($c['redirect_uri']);
    }

    public function connected(): bool
    {
        return filled($this->accessToken());
    }

    public function authorizeUrl(string $state): string
    {
        $c = config('shipping.carriers.melhorenvio');

        return $this->baseUrl() . '/oauth/authorize?' . http_build_query([
            'client_id' => $c['client_id'],
            'redirect_uri' => $c['redirect_uri'],
            'response_type' => 'code',
            'scope' => self::SCOPE,
            'state' => $state,
        ]);
    }

    /** Troca o authorization code por access/refresh token e persiste. */
    public function exchangeCode(string $code): bool
    {
        $c = config('shipping.carriers.melhorenvio');

        $response = Http::acceptJson()->asJson()->post($this->baseUrl() . '/oauth/token', [
            'grant_type' => 'authorization_code',
            'client_id' => (int) $c['client_id'],
            'client_secret' => $c['client_secret'],
            'redirect_uri' => $c['redirect_uri'],
            'code' => $code,
        ]);

        return $this->store($response->json());
    }

    /** Retorna um access token válido, renovando via refresh token se expirado. */
    public function accessToken(): ?string
    {
        $token = IntegrationToken::where('provider', self::PROVIDER)->first();

        if (! $token || ! $token->access_token) {
            return null;
        }

        if ($token->isExpired() && $token->refresh_token) {
            $this->refresh($token);
            $token->refresh();
        }

        return $token->access_token;
    }

    public function disconnect(): void
    {
        IntegrationToken::where('provider', self::PROVIDER)->delete();
    }

    private function refresh(IntegrationToken $token): void
    {
        $c = config('shipping.carriers.melhorenvio');

        $response = Http::acceptJson()->asJson()->post($this->baseUrl() . '/oauth/token', [
            'grant_type' => 'refresh_token',
            'client_id' => (int) $c['client_id'],
            'client_secret' => $c['client_secret'],
            'refresh_token' => $token->refresh_token,
        ]);

        $this->store($response->json());
    }

    /** @param  array<string,mixed>|null  $data */
    private function store(?array $data): bool
    {
        if (! $data || empty($data['access_token'])) {
            return false;
        }

        IntegrationToken::updateOrCreate(
            ['provider' => self::PROVIDER],
            [
                'access_token' => $data['access_token'],
                'refresh_token' => $data['refresh_token'] ?? null,
                'expires_at' => isset($data['expires_in'])
                    ? Carbon::now()->addSeconds((int) $data['expires_in'] - 60)
                    : null,
            ]
        );

        return true;
    }
}
