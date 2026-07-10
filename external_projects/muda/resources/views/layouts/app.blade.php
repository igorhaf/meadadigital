<!DOCTYPE html>
<html lang="pt-BR" class="h-full">
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <meta name="csrf-token" content="{{ csrf_token() }}">

    <title>@hasSection('title')@yield('title') · {{ $settings->site_name }}@else{{ $settings->site_name }} · {{ $settings->tagline }}@endif</title>
    <meta name="description" content="@yield('description', $settings->tagline)">

    <link rel="icon" href="{{ placeholder_image('muda-favicon', '🌱', 64, 64) }}">

    @vite(['resources/css/app.css', 'resources/js/app.js'])
</head>
<body class="flex min-h-full flex-col">
    @include('partials.header')

    <main class="flex-1">
        @yield('content')
    </main>

    @include('partials.footer')

    {{-- Global cart drawer island --}}
    <div data-island="CartDrawer"></div>
</body>
</html>
