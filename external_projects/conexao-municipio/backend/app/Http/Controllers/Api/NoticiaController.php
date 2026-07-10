<?php

namespace App\Http\Controllers\Api;

use App\Http\Controllers\Controller;
use App\Models\Noticia;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;

class NoticiaController extends Controller
{
    public function index(Request $request): JsonResponse
    {
        $filters = $request->validate([
            'limit' => ['sometimes', 'integer', 'between:1,50'],
        ]);

        $noticias = Noticia::query()
            ->orderByDesc('data')
            ->orderByDesc('id')
            ->limit((int) ($filters['limit'] ?? 10))
            ->get();

        return response()->json($noticias);
    }
}
