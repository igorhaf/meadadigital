@extends('layouts.app')

@section('title', 'Agendamento ' . $appointment->reference)

@section('content')
@php($start = $appointment->start_at->setTimezone($appointment->timezone))
@php($end = $appointment->end_at->setTimezone($appointment->timezone))
<div class="container-site max-w-2xl py-10">
    <a href="{{ route('appointments.index') }}" class="text-sm text-neutral-500 hover:underline">← Meus agendamentos</a>

    @if(session('status'))
        <div class="mt-4 rounded-xl bg-brand-50 px-4 py-3 text-sm font-medium text-brand-800">{{ session('status') }}</div>
    @endif
    @if(session('error'))
        <div class="mt-4 rounded-xl bg-red-50 px-4 py-3 text-sm font-medium text-red-700">{{ session('error') }}</div>
    @endif

    <div class="card mt-4 overflow-hidden">
        <div class="bg-brand-700 p-6 text-white">
            <p class="text-xs uppercase tracking-wide text-white/70">{{ $appointment->reference }}</p>
            <h1 class="mt-1 text-2xl font-extrabold">{{ $appointment->service_title }}</h1>
            <p class="mt-1 text-white/80">{{ $appointment->status_label }}</p>
        </div>
        <dl class="divide-y divide-neutral-100 px-6">
            <div class="flex justify-between py-3 text-sm"><dt class="text-neutral-500">Data e hora</dt><dd class="font-medium text-neutral-800">{{ $start->format('d/m/Y') }} · {{ $start->format('H:i') }}–{{ $end->format('H:i') }}</dd></div>
            <div class="flex justify-between py-3 text-sm"><dt class="text-neutral-500">Terapeuta</dt><dd class="font-medium text-neutral-800">{{ $appointment->professional?->display_name ?? '—' }}</dd></div>
            <div class="flex justify-between py-3 text-sm"><dt class="text-neutral-500">Local</dt><dd class="font-medium text-neutral-800">{{ $appointment->location_label ?: '—' }}</dd></div>
            <div class="flex justify-between py-3 text-sm"><dt class="text-neutral-500">Valor</dt><dd class="font-medium text-neutral-800">{{ money($appointment->total) }}</dd></div>
            <div class="flex justify-between py-3 text-sm"><dt class="text-neutral-500">Pagamento</dt><dd class="font-medium text-neutral-800">{{ $appointment->payment_status_label }}</dd></div>
        </dl>
        <div class="flex flex-wrap gap-3 border-t border-neutral-100 p-6">
            @if(! $appointment->isPaid() && $appointment->status === 'pending' && $appointment->service?->requires_prepayment)
                <a href="{{ route('payment.show', $appointment) }}" class="btn-brand">Pagar agora</a>
            @endif
            @if(in_array($appointment->status, ['pending', 'confirmed']))
                <form method="POST" action="{{ route('appointments.cancel', $appointment) }}" onsubmit="return confirm('Cancelar este agendamento?')">
                    @csrf
                    <button class="btn-outline text-red-600">Cancelar agendamento</button>
                </form>
            @endif
        </div>
    </div>
</div>
@endsection
