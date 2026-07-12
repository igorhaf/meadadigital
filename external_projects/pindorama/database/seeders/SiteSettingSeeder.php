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
            'site_name' => 'Pindorama',
            'tagline' => 'Terapias integrativas e práticas de bem-estar, com agenda online',
            'announcement' => '🌿 Agende online com terapeutas verificados — presencial ou à distância, em até 12x',
            'instagram_url' => 'https://instagram.com/pindorama',
            'facebook_url' => 'https://facebook.com/pindorama',
            'tiktok_url' => null,
            'twitter_url' => null,
            'whatsapp' => '+55 11 90000-0000',
            'contact_email' => 'contato@pindorama.com.br',
            'contact_phone' => '(11) 90000-0000',
            'about' => 'A Pindorama é um marketplace de terapias integrativas e práticas de saúde. '
                . 'Conectamos terapeutas de acupuntura, reiki, ayurveda, massoterapia e mais a quem busca '
                . 'cuidado e bem-estar, com agendamento e pagamento online.',
        ]);
    }
}
