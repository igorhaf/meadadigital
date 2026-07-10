<?php

namespace App\Http\Controllers\Api;

use App\Http\Controllers\Controller;
use App\Models\Configuracao;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;

class ConfiguracaoController extends Controller
{
    public function index(): JsonResponse
    {
        return response()->json(Configuracao::comoMapa());
    }

    public function update(Request $request): JsonResponse
    {
        $data = $request->validate([
            'nome_municipio' => ['sometimes', 'required', 'string', 'max:255'],
            'email_contato' => ['sometimes', 'required', 'email', 'max:255'],
            'telefone_contato' => ['sometimes', 'nullable', 'string', 'max:30', 'regex:/^\(\d{2}\) \d{4,5}-\d{4}$/'],
            'notificacoes_email' => ['sometimes', 'required', 'in:0,1'],
            'denuncias_por_pagina' => ['sometimes', 'required', 'integer', 'between:5,100'],
        ]);

        foreach ($data as $chave => $valor) {
            Configuracao::query()->updateOrCreate(
                ['chave' => $chave],
                ['valor' => (string) $valor],
            );
        }

        return response()->json(Configuracao::comoMapa());
    }
}
