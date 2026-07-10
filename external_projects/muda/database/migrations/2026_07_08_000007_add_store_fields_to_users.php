<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    public function up(): void
    {
        Schema::table('users', function (Blueprint $table) {
            $table->string('store_slug')->nullable()->unique()->after('store_name');
            $table->text('store_bio')->nullable()->after('store_slug');
            $table->string('store_location')->nullable()->after('store_bio');
        });
    }

    public function down(): void
    {
        Schema::table('users', function (Blueprint $table) {
            $table->dropColumn(['store_slug', 'store_bio', 'store_location']);
        });
    }
};
