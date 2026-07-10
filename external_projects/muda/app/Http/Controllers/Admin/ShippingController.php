<?php

namespace App\Http\Controllers\Admin;

use App\Http\Controllers\Controller;
use App\Services\Shipping\MelhorEnvioService;
use Illuminate\Contracts\View\View;
use Illuminate\Http\RedirectResponse;
use Illuminate\Http\Request;
use Illuminate\Support\Str;

class ShippingController extends Controller
{
    public function index(MelhorEnvioService $me): View
    {
        return view('admin.shipping', [
            'configured' => $me->configured(),
            'connected' => $me->connected(),
            'sandbox' => (bool) config('shipping.carriers.melhorenvio.sandbox'),
            'redirect' => config('shipping.carriers.melhorenvio.redirect_uri'),
            'origin' => config('shipping.origin_cep'),
        ]);
    }

    /** Inicia o OAuth: manda o root para autorizar no Melhor Envio. */
    public function connect(MelhorEnvioService $me): RedirectResponse
    {
        abort_unless($me->configured(), 400, 'Credenciais do Melhor Envio ausentes no .env.');

        $state = Str::random(40);
        session(['me_oauth_state' => $state]);

        return redirect()->away($me->authorizeUrl($state));
    }

    /** Callback do Melhor Envio: troca o code por token. */
    public function callback(Request $request, MelhorEnvioService $me): RedirectResponse
    {
        if ($request->query('state') !== session('me_oauth_state')) {
            return redirect()->route('admin.shipping')->with('status', 'Conexão cancelada (state inválido).');
        }

        $code = (string) $request->query('code');

        if ($code === '' || ! $me->exchangeCode($code)) {
            return redirect()->route('admin.shipping')->with('status', 'Não foi possível conectar ao Melhor Envio.');
        }

        return redirect()->route('admin.shipping')->with('status', 'Melhor Envio conectado com sucesso! 🎉');
    }

    public function disconnect(MelhorEnvioService $me): RedirectResponse
    {
        $me->disconnect();

        return redirect()->route('admin.shipping')->with('status', 'Melhor Envio desconectado.');
    }
}
