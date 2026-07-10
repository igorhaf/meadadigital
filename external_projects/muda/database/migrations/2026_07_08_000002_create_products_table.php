<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    public function up(): void
    {
        Schema::create('products', function (Blueprint $table) {
            $table->id();
            $table->foreignId('category_id')->constrained()->cascadeOnDelete();

            $table->string('title');
            $table->string('slug')->unique();
            $table->text('description')->nullable();

            // Brechó-specific attributes
            $table->string('condition')->default('usado');   // novo | seminovo | usado
            $table->string('condition_note')->nullable();      // e.g. "leve desgaste na barra"
            $table->string('brand')->nullable();
            $table->string('size')->nullable();                // M, 38, Único...
            $table->string('color')->nullable();

            // Pricing
            $table->decimal('price', 10, 2);
            $table->decimal('compare_at_price', 10, 2)->nullable();  // "de/por"
            $table->unsignedTinyInteger('max_installments')->default(12);
            $table->boolean('free_shipping')->default(false);

            // Inventory / status
            $table->unsignedInteger('stock')->default(1);
            $table->string('sku')->nullable();
            $table->boolean('is_active')->default(true);
            $table->boolean('is_featured')->default(false);

            // Social proof (denormalized for the storefront)
            $table->decimal('rating', 2, 1)->default(0);
            $table->unsignedInteger('reviews_count')->default(0);
            $table->unsignedInteger('sold_count')->default(0);
            $table->unsignedInteger('views')->default(0);

            // Seller display info (real seller relation comes in the multitenant phase)
            $table->string('seller_name')->nullable();
            $table->string('seller_location')->nullable();

            $table->timestamps();

            $table->index(['category_id', 'is_active']);
            $table->index('is_featured');
            $table->index('price');
        });
    }

    public function down(): void
    {
        Schema::dropIfExists('products');
    }
};
