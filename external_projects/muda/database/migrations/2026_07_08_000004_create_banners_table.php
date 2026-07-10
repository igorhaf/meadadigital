<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    public function up(): void
    {
        Schema::create('banners', function (Blueprint $table) {
            $table->id();
            $table->string('title');
            $table->string('subtitle')->nullable();
            $table->string('cta_label')->nullable();
            $table->string('link_url')->nullable();
            $table->string('image_path')->nullable();
            $table->string('bg_from')->default('#7c3aed');   // gradient start
            $table->string('bg_to')->default('#4f46e5');     // gradient end
            $table->string('placement')->default('hero');    // hero | strip
            $table->unsignedInteger('position')->default(0);
            $table->boolean('is_active')->default(true);
            $table->timestamps();

            $table->index(['placement', 'is_active', 'position']);
        });
    }

    public function down(): void
    {
        Schema::dropIfExists('banners');
    }
};
