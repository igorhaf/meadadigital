<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    public function up(): void
    {
        Schema::table('orders', function (Blueprint $table) {
            $table->string('payment_status')->default('pending')->after('status'); // pending|approved|in_process|rejected|refunded|cancelled
            $table->string('payment_method')->nullable()->after('payment_status');  // mercadopago | simulado
            $table->string('mp_preference_id')->nullable()->after('payment_method');
            $table->string('mp_payment_id')->nullable()->after('mp_preference_id');
            $table->timestamp('paid_at')->nullable()->after('mp_payment_id');

            $table->index('payment_status');
        });
    }

    public function down(): void
    {
        Schema::table('orders', function (Blueprint $table) {
            $table->dropColumn(['payment_status', 'payment_method', 'mp_preference_id', 'mp_payment_id', 'paid_at']);
        });
    }
};
