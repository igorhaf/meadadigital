<?php

namespace App\Http\Middleware;

use Closure;
use Illuminate\Http\Request;
use Symfony\Component\HttpFoundation\Response;

class EnsureUserHasRole
{
    /**
     * Usage: ->middleware('role:seller') or 'role:root'.
     * Root always passes; 'seller' also accepts root (root is a super-tenant).
     */
    public function handle(Request $request, Closure $next, string $role): Response
    {
        $user = $request->user();

        if (! $user) {
            return redirect()->route('login');
        }

        $allowed = match ($role) {
            'root' => $user->isRoot(),
            'seller' => $user->isSeller(),
            default => $user->role === $role,
        };

        abort_unless($allowed, 403, 'Você não tem permissão para acessar esta área.');

        return $next($request);
    }
}
