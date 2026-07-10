<?php

namespace App\Http\Controllers\Api;

use App\Http\Controllers\Controller;
use App\Models\Denuncia;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;
use Illuminate\Validation\Rule;
use Symfony\Component\HttpFoundation\StreamedResponse;

class DenunciaController extends Controller
{
    public function index(Request $request): JsonResponse
    {
        $filters = $request->validate([
            'status' => ['sometimes', 'string', Rule::in(Denuncia::STATUSES)],
            'categoria' => ['sometimes', 'string', Rule::in(Denuncia::CATEGORIAS)],
            'origem' => ['sometimes', 'string', Rule::in(Denuncia::ORIGENS)],
            'busca' => ['sometimes', 'string', 'max:255'],
            'periodo' => ['sometimes', Rule::in(Denuncia::PERIODOS)],
            'per_page' => ['sometimes', 'integer', 'between:1,100'],
        ]);

        $query = $this->filtrar($filters)
            ->orderByDesc('data')
            ->orderByDesc('id');

        return response()->json($query->paginate((int) ($filters['per_page'] ?? 15))->appends($filters));
    }

    public function export(Request $request): StreamedResponse
    {
        $filters = $request->validate([
            'status' => ['sometimes', 'string', Rule::in(Denuncia::STATUSES)],
            'busca' => ['sometimes', 'string', 'max:255'],
            'periodo' => ['sometimes', Rule::in(Denuncia::PERIODOS)],
        ]);

        $query = $this->filtrar($filters)
            ->orderByDesc('data')
            ->orderByDesc('id');

        return response()->streamDownload(function () use ($query) {
            $handle = fopen('php://output', 'w');
            // BOM UTF-8 para o Excel reconhecer acentuação
            fwrite($handle, "\u{FEFF}");
            fputcsv($handle, ['ID', 'Título', 'Descrição', 'Endereço', 'Data', 'Status'], ';');

            $query->lazy()->each(function (Denuncia $denuncia) use ($handle) {
                fputcsv($handle, [
                    $denuncia->id,
                    $denuncia->titulo,
                    $denuncia->descricao,
                    $denuncia->endereco,
                    $denuncia->data->format('d/m/Y'),
                    $denuncia->status,
                ], ';');
            });

            fclose($handle);
        }, 'denuncias.csv', ['Content-Type' => 'text/csv; charset=UTF-8']);
    }

    private function filtrar(array $filters): \Illuminate\Database\Eloquent\Builder
    {
        return Denuncia::query()
            ->when($filters['status'] ?? null, fn ($q, $status) => $q->where('status', $status))
            ->when($filters['categoria'] ?? null, fn ($q, $categoria) => $q->where('categoria', $categoria))
            ->when($filters['origem'] ?? null, fn ($q, $origem) => $q->where('origem', $origem))
            ->when($filters['busca'] ?? null, fn ($q, $busca) => $q->where(
                fn ($sub) => $sub
                    ->where('titulo', 'ilike', "%{$busca}%")
                    ->orWhere('endereco', 'ilike', "%{$busca}%")
            ))
            ->noPeriodo($filters['periodo'] ?? null);
    }

    public function store(Request $request): JsonResponse
    {
        $data = $request->validate([
            'titulo' => ['required', 'string', 'max:255'],
            'descricao' => ['nullable', 'string'],
            'endereco' => ['required', 'string', 'max:255'],
            'categoria' => ['nullable', Rule::in(Denuncia::CATEGORIAS)],
            'origem' => ['sometimes', Rule::in(Denuncia::ORIGENS)],
            'status' => ['sometimes', Rule::in(Denuncia::STATUSES)],
            'data' => ['required', 'date'],
        ]);

        $denuncia = Denuncia::create($data)->refresh();

        return response()->json($denuncia, 201);
    }

    public function show(Denuncia $denuncia): JsonResponse
    {
        return response()->json($denuncia);
    }

    public function update(Request $request, Denuncia $denuncia): JsonResponse
    {
        $data = $request->validate([
            'titulo' => ['sometimes', 'required', 'string', 'max:255'],
            'descricao' => ['nullable', 'string'],
            'endereco' => ['sometimes', 'required', 'string', 'max:255'],
            'categoria' => ['nullable', Rule::in(Denuncia::CATEGORIAS)],
            'status' => ['sometimes', Rule::in(Denuncia::STATUSES)],
            'data' => ['sometimes', 'required', 'date'],
        ]);

        $denuncia->update($data);

        return response()->json($denuncia);
    }

    public function destroy(Denuncia $denuncia): JsonResponse
    {
        $denuncia->delete();

        return response()->json(null, 204);
    }
}
