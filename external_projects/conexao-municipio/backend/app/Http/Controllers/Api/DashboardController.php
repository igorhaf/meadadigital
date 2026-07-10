<?php

namespace App\Http\Controllers\Api;

use App\Http\Controllers\Controller;
use App\Models\Denuncia;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;
use Illuminate\Validation\Rule;

class DashboardController extends Controller
{
    public function __invoke(Request $request): JsonResponse
    {
        $filters = $request->validate([
            'periodo' => ['sometimes', Rule::in(Denuncia::PERIODOS)],
        ]);

        $periodo = $filters['periodo'] ?? 'todos';

        $porStatus = Denuncia::query()
            ->noPeriodo($periodo)
            ->where('status', '!=', Denuncia::STATUS_ARQUIVADO)
            ->selectRaw('status, count(*) as total')
            ->groupBy('status')
            ->pluck('total', 'status');

        $recentes = Denuncia::query()
            ->noPeriodo($periodo)
            ->where('status', '!=', Denuncia::STATUS_ARQUIVADO)
            ->orderByDesc('data')
            ->orderByDesc('id')
            ->limit(5)
            ->get();

        return response()->json([
            'total' => $porStatus->sum(),
            'pendentes' => (int) $porStatus->get(Denuncia::STATUS_PENDENTE, 0),
            'em_andamento' => (int) $porStatus->get(Denuncia::STATUS_EM_ANDAMENTO, 0),
            'resolvidas' => (int) $porStatus->get(Denuncia::STATUS_RESOLVIDO, 0),
            'recentes' => $recentes,
        ]);
    }
}
