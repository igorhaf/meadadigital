<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    public function up(): void
    {
        Schema::create('agendamentos', function (Blueprint $table) {
            $table->id();
            $table->string('servico')->index();
            $table->date('data');
            $table->string('horario', 5);
            $table->timestamps();

            // um mesmo horário não pode ser agendado duas vezes para o mesmo serviço
            $table->unique(['servico', 'data', 'horario']);
        });
    }

    public function down(): void
    {
        Schema::dropIfExists('agendamentos');
    }
};
