<?php

namespace App\Http\Controllers\Api;

use App\Http\Controllers\Controller;
use App\Models\User;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;
use Illuminate\Validation\Rule;

class UserController extends Controller
{
    public function index(Request $request): JsonResponse
    {
        $filters = $request->validate([
            'busca' => ['sometimes', 'string', 'max:255'],
            'per_page' => ['sometimes', 'integer', 'between:1,100'],
        ]);

        $query = User::query()
            ->when($filters['busca'] ?? null, fn ($q, $busca) => $q->where(
                fn ($sub) => $sub
                    ->where('name', 'ilike', "%{$busca}%")
                    ->orWhere('email', 'ilike', "%{$busca}%")
            ))
            ->orderBy('name');

        return response()->json($query->paginate((int) ($filters['per_page'] ?? 15)));
    }

    public function store(Request $request): JsonResponse
    {
        $data = $request->validate([
            'name' => ['required', 'string', 'max:255'],
            'email' => ['required', 'email', 'max:255', 'unique:users,email'],
            'role' => ['sometimes', Rule::in(User::ROLES)],
            'password' => ['required', 'string', 'min:8'],
        ]);

        $usuario = User::create($data)->refresh();

        return response()->json($usuario, 201);
    }

    public function show(User $usuario): JsonResponse
    {
        return response()->json($usuario);
    }

    public function update(Request $request, User $usuario): JsonResponse
    {
        $data = $request->validate([
            'name' => ['sometimes', 'required', 'string', 'max:255'],
            'email' => ['sometimes', 'required', 'email', 'max:255', Rule::unique('users', 'email')->ignore($usuario->id)],
            'role' => ['sometimes', Rule::in(User::ROLES)],
            'password' => ['sometimes', 'required', 'string', 'min:8'],
        ]);

        $usuario->update($data);

        return response()->json($usuario);
    }

    public function destroy(User $usuario): JsonResponse
    {
        $ehUltimoAdmin = $usuario->role === User::ROLE_ADMIN
            && User::query()->where('role', User::ROLE_ADMIN)->count() === 1;

        if ($ehUltimoAdmin) {
            return response()->json([
                'message' => 'Não é possível excluir o único administrador do sistema.',
            ], 422);
        }

        $usuario->delete();

        return response()->json(null, 204);
    }
}
