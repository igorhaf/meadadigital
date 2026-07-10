<?php

namespace App\Http\Controllers\Api;

use App\Http\Controllers\Controller;
use App\Models\Agendamento;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;
use Illuminate\Validation\Rule;
use Illuminate\Validation\ValidationException;

class AgendamentoController extends Controller
{
    public function index(Request $request): JsonResponse
    {
        $filters = $request->validate([
            'servico' => ['sometimes', Rule::in(Agendamento::SERVICOS)],
            'data' => ['sometimes', 'date'],
            'per_page' => ['sometimes', 'integer', 'between:1,100'],
        ]);

        $query = Agendamento::query()
            ->when($filters['servico'] ?? null, fn ($q, $servico) => $q->where('servico', $servico))
            ->when($filters['data'] ?? null, fn ($q, $data) => $q->whereDate('data', $data))
            ->orderBy('data')
            ->orderBy('horario');

        return response()->json($query->paginate((int) ($filters['per_page'] ?? 15))->appends($filters));
    }

    /**
     * Grade de horários de um serviço em uma data, com a disponibilidade de cada um.
     */
    public function horarios(Request $request): JsonResponse
    {
        $filters = $request->validate([
            'servico' => ['required', Rule::in(Agendamento::SERVICOS)],
            'data' => ['required', 'date_format:Y-m-d'],
        ]);

        $ocupados = Agendamento::query()
            ->where('servico', $filters['servico'])
            ->whereDate('data', $filters['data'])
            ->pluck('horario')
            ->all();

        $horarios = array_map(fn (string $horario) => [
            'horario' => $horario,
            'disponivel' => ! in_array($horario, $ocupados, true),
        ], Agendamento::HORARIOS);

        return response()->json([
            'servico' => $filters['servico'],
            'data' => $filters['data'],
            'horarios' => $horarios,
        ]);
    }

    public function store(Request $request): JsonResponse
    {
        $data = $request->validate([
            'servico' => ['required', Rule::in(Agendamento::SERVICOS)],
            'data' => ['required', 'date_format:Y-m-d'],
            'horario' => ['required', Rule::in(Agendamento::HORARIOS)],
        ]);

        $ocupado = Agendamento::query()
            ->where('servico', $data['servico'])
            ->whereDate('data', $data['data'])
            ->where('horario', $data['horario'])
            ->exists();

        if ($ocupado) {
            throw ValidationException::withMessages([
                'horario' => ['Este horário já está agendado. Escolha outro.'],
            ]);
        }

        $agendamento = Agendamento::create($data)->refresh();

        return response()->json($agendamento, 201);
    }

    public function destroy(Agendamento $agendamento): JsonResponse
    {
        $agendamento->delete();

        return response()->json(null, 204);
    }
}
