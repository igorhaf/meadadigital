@extends('layouts.app')

@section('title', 'Meus agendamentos')

@section('content')
@php($statusChip = [
    'pending' => 'bg-amber-100 text-amber-800',
    'confirmed' => 'bg-brand-100 text-brand-800',
    'completed' => 'bg-neutral-200 text-neutral-700',
    'cancelled' => 'bg-red-100 text-red-700',
    'no_show' => 'bg-red-100 text-red-700',
])
<div class="container-site max-w-3xl py-10">
    <h1 class="mb-6 text-2xl font-extrabold text-neutral-900">Meus agendamentos</h1>

    @if(session('status'))
        <div class="mb-4 rounded-xl bg-brand-50 px-4 py-3 text-sm font-medium text-brand-800">{{ session('status') }}</div>
    @endif

    @if($appointments->isEmpty())
        <div class="card p-10 text-center text-neutral-500">
            <p>Você ainda não tem agendamentos.</p>
            <a href="{{ route('home') }}" class="mt-3 inline-block font-medium text-brand-700 hover:underline">Encontrar um terapeuta</a>
        </div>
    @else
        <div class="space-y-3">
            @foreach($appointments as $appt)
                <a href="{{ route('appointments.show', $appt) }}" class="card flex items-center justify-between p-4 transition hover:shadow-md">
                    <div>
                        <p class="font-semibold text-neutral-900">{{ $appt->service_title }}</p>
                        <p class="text-sm text-neutral-500">
                            {{ $appt->start_at->setTimezone($appt->timezone)->format('d/m/Y \à\s H:i') }}
                            · {{ $appt->professional?->display_name ?? 'Terapeuta' }}
                        </p>
                    </div>
                    <span class="chip {{ $statusChip[$appt->status] ?? 'bg-neutral-100 text-neutral-600' }}">{{ $appt->status_label }}</span>
                </a>
            @endforeach
        </div>
        <div class="mt-6">{{ $appointments->links() }}</div>
    @endif
</div>
@endsection
