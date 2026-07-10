<?php

namespace Database\Seeders;

use App\Models\SiteSetting;
use Illuminate\Database\Seeder;

class SiteSettingSeeder extends Seeder
{
    public function run(): void
    {
        SiteSetting::query()->delete();

        SiteSetting::create([
            'site_name' => 'Muda',
            'tagline' => 'O marketplace de brechó que renova o seu guarda-roupa',
            'announcement' => null,
            'instagram_url' => 'https://instagram.com/muda',
            'facebook_url' => 'https://facebook.com/muda',
            'tiktok_url' => 'https://tiktok.com/@muda',
            'twitter_url' => 'https://twitter.com/muda',
            'whatsapp' => '+55 11 90000-0000',
            'contact_email' => 'contato@muda.com.br',
            'contact_phone' => '(11) 90000-0000',
            'about' => 'A Muda é um marketplace de moda circular e brechó. Conectamos garimpeiros '
                . 'e brechós de todo o Brasil a quem busca peças únicas com preço justo e consumo consciente.',
        ]);
    }
}
