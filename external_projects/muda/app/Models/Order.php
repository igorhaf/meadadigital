<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Model;
use Illuminate\Database\Eloquent\Relations\BelongsTo;
use Illuminate\Database\Eloquent\Relations\HasMany;
use Illuminate\Support\Carbon;

class Order extends Model
{
    protected $fillable = [
        'reference', 'user_id', 'buyer_name', 'buyer_email', 'buyer_phone',
        'shipping_address', 'status', 'payment_status', 'payment_method',
        'mp_preference_id', 'mp_payment_id', 'paid_at', 'subtotal', 'shipping', 'total',
        'shipping_carrier', 'shipping_service', 'shipping_cep', 'shipping_days',
    ];

    protected $casts = [
        'subtotal' => 'decimal:2',
        'shipping' => 'decimal:2',
        'total' => 'decimal:2',
        'paid_at' => 'datetime',
    ];

    public const STATUSES = [
        'pending' => 'Aguardando pagamento',
        'paid' => 'Pago',
        'shipped' => 'Enviado',
        'delivered' => 'Entregue',
        'cancelled' => 'Cancelado',
    ];

    /** Maps Mercado Pago payment statuses to friendly labels. */
    public const PAYMENT_STATUSES = [
        'pending' => 'Pendente',
        'in_process' => 'Em análise',
        'approved' => 'Aprovado',
        'authorized' => 'Autorizado',
        'rejected' => 'Recusado',
        'refunded' => 'Estornado',
        'charged_back' => 'Estornado',
        'cancelled' => 'Cancelado',
    ];

    public function user(): BelongsTo
    {
        return $this->belongsTo(User::class);
    }

    public function items(): HasMany
    {
        return $this->hasMany(OrderItem::class);
    }

    public function getStatusLabelAttribute(): string
    {
        return self::STATUSES[$this->status] ?? ucfirst((string) $this->status);
    }

    public function getPaymentStatusLabelAttribute(): string
    {
        return self::PAYMENT_STATUSES[$this->payment_status] ?? ucfirst((string) $this->payment_status);
    }

    public function isPaid(): bool
    {
        return in_array($this->payment_status, ['approved', 'authorized'], true);
    }

    /**
     * Apply a Mercado Pago payment status to the order, keeping the overall
     * fulfillment status and paid_at in sync.
     */
    public function applyPaymentStatus(string $mpStatus, ?string $paymentId = null): void
    {
        $this->payment_status = $mpStatus;

        if ($paymentId) {
            $this->mp_payment_id = $paymentId;
        }

        if (in_array($mpStatus, ['approved', 'authorized'], true)) {
            $this->paid_at ??= Carbon::now();
            if ($this->status === 'pending') {
                $this->status = 'paid';
            }
        } elseif (in_array($mpStatus, ['rejected', 'cancelled'], true) && $this->status === 'pending') {
            $this->status = 'cancelled';
        }

        $this->save();
    }
}
