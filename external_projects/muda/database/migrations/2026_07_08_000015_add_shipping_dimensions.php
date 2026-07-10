<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    public function up(): void
    {
        Schema::table('products', function (Blueprint $table) {
            $table->unsignedInteger('weight_grams')->default(300)->after('stock');
            $table->unsignedInteger('length_cm')->default(20)->after('weight_grams');
            $table->unsignedInteger('width_cm')->default(15)->after('length_cm');
            $table->unsignedInteger('height_cm')->default(5)->after('width_cm');
        });

        Schema::table('orders', function (Blueprint $table) {
            $table->string('shipping_carrier')->nullable()->after('shipping');   // Correios | Jadlog | Loggi | Estimativa
            $table->string('shipping_service')->nullable()->after('shipping_carrier'); // PAC | SEDEX | .Package | ...
            $table->string('shipping_cep')->nullable()->after('shipping_service');
            $table->unsignedSmallInteger('shipping_days')->nullable()->after('shipping_cep');
        });
    }

    public function down(): void
    {
        Schema::table('products', function (Blueprint $table) {
            $table->dropColumn(['weight_grams', 'length_cm', 'width_cm', 'height_cm']);
        });
        Schema::table('orders', function (Blueprint $table) {
            $table->dropColumn(['shipping_carrier', 'shipping_service', 'shipping_cep', 'shipping_days']);
        });
    }
};
