<?php

namespace Database\Seeders;

use App\Models\Configuracao;
use Illuminate\Database\Seeder;

class ConfiguracaoSeeder extends Seeder
{
    public function run(): void
    {
        $padroes = [
            'nome_municipio' => 'Prefeitura Municipal',
            'email_contato' => 'admin@prefeitura.gov.br',
            'telefone_contato' => '(00) 0000-0000',
            'notificacoes_email' => '1',
            'denuncias_por_pagina' => '15',
        ];

        foreach ($padroes as $chave => $valor) {
            Configuracao::query()->firstOrCreate(
                ['chave' => $chave],
                ['valor' => $valor],
            );
        }
    }
}
