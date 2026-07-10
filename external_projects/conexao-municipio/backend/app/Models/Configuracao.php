<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Model;

class Configuracao extends Model
{
    public const CHAVES = [
        'nome_municipio',
        'email_contato',
        'telefone_contato',
        'notificacoes_email',
        'denuncias_por_pagina',
    ];

    protected $table = 'configuracoes';

    protected $fillable = [
        'chave',
        'valor',
    ];

    /**
     * Retorna todas as configurações como mapa chave => valor.
     *
     * @return array<string, string|null>
     */
    public static function comoMapa(): array
    {
        return self::query()->pluck('valor', 'chave')->all();
    }
}
