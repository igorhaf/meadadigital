<?php

namespace App\Http\Middleware;

use Closure;
use Illuminate\Http\Request;
use Symfony\Component\HttpFoundation\Response;

class EnsureSellingEnabled
{
    /**
     * Blocks the seller/onboarding areas when the marketplace feature flag is
     * off. The root user always passes so the feature can keep being built.
     */
    public function handle(Request $request, Closure $next): Response
    {
        if (! config('muda.selling_enabled') && ! $request->user()?->isRoot()) {
            abort(404);
        }

        return $next($request);
    }
}
