<?php

return [

    /*
    |--------------------------------------------------------------------------
    | Marketplace / venda por lojistas
    |--------------------------------------------------------------------------
    |
    | Liga/desliga os pontos de entrada de "Vender no Muda" (links no header,
    | footer e cadastro) e o acesso ao painel do lojista / onboarding.
    | A funcionalidade continua no código — apenas fica oculta ao público.
    | O usuário root sempre mantém acesso, para seguir construindo o recurso.
    |
    */

    'selling_enabled' => (bool) env('MUDA_SELLING_ENABLED', false),

];
