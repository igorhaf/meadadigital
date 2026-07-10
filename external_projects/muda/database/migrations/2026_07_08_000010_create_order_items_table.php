<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    public function up(): void
    {
        Schema::create('order_items', function (Blueprint $table) {
            $table->id();
            $table->foreignId('order_id')->constrained()->cascadeOnDelete();
            $table->foreignId('product_id')->nullable()->constrained()->nullOnDelete();
            $table->foreignId('seller_id')->nullable()->constrained('users')->nullOnDelete();

            // Snapshots so the order stays intact even if the product changes/is removed.
            $table->string('title');
            $table->string('image_path')->nullable();
            $table->decimal('price', 10, 2);
            $table->unsignedInteger('qty')->default(1);
            $table->decimal('line_total', 10, 2);

            $table->timestamps();

            $table->index(['seller_id', 'order_id']);
        });
    }

    public function down(): void
    {
        Schema::dropIfExists('order_items');
    }
};
