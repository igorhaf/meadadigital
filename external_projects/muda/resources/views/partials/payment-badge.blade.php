@php
    $map = [
        'approved' => ['bg-brand-50 text-brand-700', 'Aprovado'],
        'authorized' => ['bg-brand-50 text-brand-700', 'Autorizado'],
        'pending' => ['bg-amber-50 text-amber-700', 'Pendente'],
        'in_process' => ['bg-amber-50 text-amber-700', 'Em análise'],
        'rejected' => ['bg-red-50 text-red-600', 'Recusado'],
        'cancelled' => ['bg-red-50 text-red-600', 'Cancelado'],
        'refunded' => ['bg-neutral-100 text-neutral-600', 'Estornado'],
        'charged_back' => ['bg-neutral-100 text-neutral-600', 'Estornado'],
    ];
    [$cls, $label] = $map[$order->payment_status] ?? ['bg-neutral-100 text-neutral-600', $order->payment_status_label];
@endphp
<span class="chip {{ $cls }}">{{ $label }}</span>
