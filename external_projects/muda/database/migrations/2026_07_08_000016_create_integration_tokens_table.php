<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    // Tokens OAuth de integrações (ex.: Melhor Envio).
    public function up(): void
    {
        Schema::create('integration_tokens', function (Blueprint $table) {
            $table->id();
            $table->string('provider')->unique();
            $table->text('access_token')->nullable();
            $table->text('refresh_token')->nullable();
            $table->timestamp('expires_at')->nullable();
            $table->timestamps();
        });
    }

    public function down(): void
    {
        Schema::dropIfExists('integration_tokens');
    }
};
