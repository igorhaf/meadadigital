<?php

return [

    // CEP de origem (de onde as encomendas saem).
    'origin_cep' => env('SHIPPING_ORIGIN_CEP', '01310100'),

    // Frete grátis acima deste valor (subtotal). null desativa.
    'free_above' => env('SHIPPING_FREE_ABOVE', 199),

    // Caixa/peso padrão quando o produto não tem medidas.
    'default_package' => [
        'weight' => 300,   // gramas
        'length' => 20,    // cm
        'width' => 15,
        'height' => 5,
    ],

    // Mostra estimativa (via ViaCEP + peso) quando nenhuma transportadora responde.
    'fallback' => (bool) env('SHIPPING_FALLBACK', true),

    'carriers' => [

        // Melhor Envio — agregador (Correios PAC/SEDEX, Jadlog, Loggi, Azul…).
        // OAuth2: cadastre um aplicativo em Melhor Envio → Configurações → Tokens.
        'melhorenvio' => [
            'enabled' => (bool) env('MELHORENVIO_ENABLED', false),
            'client_id' => env('MELHORENVIO_CLIENT_ID'),
            'client_secret' => env('MELHORENVIO_CLIENT_SECRET'),
            'redirect_uri' => env('MELHORENVIO_REDIRECT_URI'),
            'sandbox' => (bool) env('MELHORENVIO_SANDBOX', true),
            // O Melhor Envio exige User-Agent com um e-mail de contato.
            'user_agent' => env('MELHORENVIO_USER_AGENT', 'Muda (contato@muda.com.br)'),
        ],
    ],
];
