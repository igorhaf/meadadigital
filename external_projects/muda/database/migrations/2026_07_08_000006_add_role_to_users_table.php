<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    // Forward-looking seam for the multitenant / admin phase.
    public function up(): void
    {
        Schema::table('users', function (Blueprint $table) {
            $table->string('role')->default('customer')->after('email');  // customer | seller | root
            $table->boolean('is_seller')->default(false)->after('role');
            $table->string('store_name')->nullable()->after('is_seller');
        });
    }

    public function down(): void
    {
        Schema::table('users', function (Blueprint $table) {
            $table->dropColumn(['role', 'is_seller', 'store_name']);
        });
    }
};
