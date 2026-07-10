<!DOCTYPE html>
<html lang="pt-BR" class="h-full">
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <meta name="csrf-token" content="{{ csrf_token() }}">
    <title>@yield('title', 'Entrar') · Muda</title>
    @vite(['resources/css/app.css', 'resources/js/app.js'])
</head>
<body class="min-h-full bg-gradient-to-br from-brand-50 to-neutral-100">
    <div class="flex min-h-screen flex-col items-center justify-center px-4 py-12">
        <a href="{{ route('home') }}" class="mb-8 flex items-center gap-2 text-3xl font-extrabold text-brand-700">
            <span class="text-4xl">🌱</span> Muda
        </a>

        <div class="w-full max-w-md">
            @yield('content')
        </div>

        <a href="{{ route('home') }}" class="mt-8 text-sm text-neutral-500 transition hover:text-brand-700">← Voltar para a loja</a>
    </div>
</body>
</html>
