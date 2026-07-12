<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    /**
     * Moderação de produtos (onda 1 do cadastro por WhatsApp).
     *
     * Peça cadastrada pela IA nasce SEM preço e com a categoria apenas SUGERIDA —
     * o preço é acordado depois, no atendimento humano. Por isso price e
     * category_id passam a aceitar NULL, e um CHECK garante que nada seja
     * APROVADO sem os dois.
     *
     * O status é coluna PRÓPRIA, separada de is_active: is_active é o
     * "pausar/reativar" do vendedor. Se pendente fosse is_active=false, o vendedor
     * publicaria o próprio produto pendente clicando em "Ativar" — furando o root.
     */
    public function up(): void
    {
        Schema::table('products', function (Blueprint $table) {
            // Nasce sem preço e sem categoria confirmada.
            $table->decimal('price', 10, 2)->nullable()->change();
            $table->unsignedBigInteger('category_id')->nullable()->change();

            $table->string('status', 20)->default('aprovado');
            $table->boolean('category_suggested')->default(false);
            $table->string('source', 20)->default('painel');
            $table->text('rejection_reason')->nullable();

            $table->index('status');
        });

        // Trava de banco: APROVADO exige preço e categoria. Sem isso, uma peça sem
        // preço poderia chegar à vitrine como R$ 0,00. O banco recusa — não depende
        // de ninguém lembrar de validar na aplicação.
        DB::statement("
            ALTER TABLE products
            ADD CONSTRAINT products_approved_requires_price_and_category
            CHECK (status <> 'aprovado' OR (price IS NOT NULL AND category_id IS NOT NULL))
        ");

        DB::statement("
            ALTER TABLE products
            ADD CONSTRAINT products_status_valid
            CHECK (status IN ('rascunho', 'pendente', 'aprovado', 'recusado'))
        ");
    }

    public function down(): void
    {
        DB::statement('ALTER TABLE products DROP CONSTRAINT IF EXISTS products_approved_requires_price_and_category');
        DB::statement('ALTER TABLE products DROP CONSTRAINT IF EXISTS products_status_valid');

        Schema::table('products', function (Blueprint $table) {
            $table->dropIndex(['status']);
            $table->dropColumn(['status', 'category_suggested', 'source', 'rejection_reason']);

            // Só volta a NOT NULL se não houver linha sem preço/categoria.
            $table->decimal('price', 10, 2)->nullable(false)->change();
            $table->unsignedBigInteger('category_id')->nullable(false)->change();
        });
    }
};
