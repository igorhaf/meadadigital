<!DOCTYPE html>
<html lang="pt-BR" class="h-full">
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <meta name="csrf-token" content="{{ csrf_token() }}">
    <title>@yield('title', 'Entrar') · {{ settings()->site_name }}</title>
    <link rel="icon" type="image/svg+xml" href="{{ asset('favicon.svg') }}">
    <meta name="theme-color" content="#b0552c">

    <link rel="preconnect" href="https://fonts.bunny.net">
    <link href="https://fonts.bunny.net/css?family=albert-sans:400,500,600,700,800|fraunces:600,700&display=swap" rel="stylesheet">

    @vite(['resources/css/app.css', 'resources/js/app.js'])
</head>
<body class="min-h-full bg-gradient-to-br from-sand-50 to-brand-50">
    <div class="flex min-h-screen flex-col items-center justify-center px-4 py-12">
        <a href="{{ route('home') }}" class="mb-8 flex items-center gap-3 text-neutral-900">
            @include('partials.logo', ['logoSize' => 'h-12 w-12'])
            <span class="font-display text-3xl font-bold tracking-tight">{{ settings()->site_name }}</span>
        </a>

        <div class="w-full max-w-md">
            @yield('content')
        </div>

        <a href="{{ route('home') }}" class="mt-8 text-sm text-neutral-500 transition hover:text-brand-700">← Voltar para o site</a>
    </div>
</body>
</html>
