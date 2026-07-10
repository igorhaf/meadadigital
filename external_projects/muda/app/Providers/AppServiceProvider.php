<?php

namespace App\Providers;

use App\Models\Category;
use App\Models\SiteSetting;
use Illuminate\Support\Facades\Schema;
use Illuminate\Support\Facades\View;
use Illuminate\Support\ServiceProvider;

class AppServiceProvider extends ServiceProvider
{
    public function register(): void
    {
        //
    }

    public function boot(): void
    {
        // Share chrome data (nav + site settings) only when the main layout renders.
        // Scoping to the layout avoids extra queries on image/API requests, and
        // @include'd partials (header/footer) inherit these variables automatically.
        View::composer('layouts.app', function ($view) {
            if (! Schema::hasTable('categories')) {
                return; // migrations not run yet
            }

            $view->with('navCategories', Category::active()->roots()
                ->with(['children' => fn ($q) => $q->active()->orderBy('position')])
                ->orderBy('position')
                ->get());

            $view->with('settings', SiteSetting::current());
        });
    }
}
