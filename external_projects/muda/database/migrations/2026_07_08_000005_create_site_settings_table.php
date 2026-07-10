<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    public function up(): void
    {
        // Single-row site configuration managed by the root user (next phase).
        Schema::create('site_settings', function (Blueprint $table) {
            $table->id();
            $table->string('site_name')->default('Muda');
            $table->string('tagline')->nullable();
            $table->string('announcement')->nullable();   // top bar message

            $table->string('instagram_url')->nullable();
            $table->string('facebook_url')->nullable();
            $table->string('tiktok_url')->nullable();
            $table->string('twitter_url')->nullable();
            $table->string('whatsapp')->nullable();

            $table->string('contact_email')->nullable();
            $table->string('contact_phone')->nullable();
            $table->text('about')->nullable();

            $table->timestamps();
        });
    }

    public function down(): void
    {
        Schema::dropIfExists('site_settings');
    }
};
