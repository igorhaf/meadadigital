<?php

namespace Database\Seeders;

use App\Models\Banner;
use Illuminate\Database\Seeder;

class BannerSeeder extends Seeder
{
    public function run(): void
    {
        $hero = [
            [
                'title' => 'Garimpe seu próximo achado',
                'subtitle' => 'Milhares de peças únicas de brechó com até 70% OFF. Novos garimpos todo dia.',
                'cta_label' => 'Ver ofertas',
                'link_url' => '/busca?sort=price_asc',
                'bg_from' => '#7c3aed',
                'bg_to' => '#4f46e5',
            ],
            [
                'title' => 'Moda circular é o futuro',
                'subtitle' => 'Dê uma nova vida às peças, economize e vista-se com estilo consciente.',
                'cta_label' => 'Explorar moda',
                'link_url' => '/categoria/roupas-femininas',
                'bg_from' => '#db2777',
                'bg_to' => '#9333ea',
            ],
            [
                'title' => 'Vintage & Colecionáveis',
                'subtitle' => 'Relíquias que contam histórias: vinis, livros e HQs garimpados a dedo.',
                'cta_label' => 'Descobrir',
                'link_url' => '/categoria/casa-cultura',
                'bg_from' => '#0d9488',
                'bg_to' => '#0369a1',
            ],
        ];

        foreach ($hero as $i => $banner) {
            Banner::create($banner + ['placement' => 'hero', 'position' => $i]);
        }

        $strip = [
            [
                'title' => 'Tênis a partir de R$79',
                'subtitle' => 'Streetwear garimpado',
                'cta_label' => 'Ver tênis',
                'link_url' => '/categoria/tenis',
                'bg_from' => '#f59e0b',
                'bg_to' => '#ea580c',
            ],
            [
                'title' => 'Feito à mão',
                'subtitle' => 'Artesanato & decoração',
                'cta_label' => 'Explorar',
                'link_url' => '/categoria/artesanato-brindes',
                'bg_from' => '#14b8a6',
                'bg_to' => '#6d28d9',
            ],
            [
                'title' => 'Cantinho Kids',
                'subtitle' => 'Brinquedos e enxoval',
                'cta_label' => 'Ver infantil',
                'link_url' => '/categoria/infantil',
                'bg_from' => '#22c55e',
                'bg_to' => '#0d9488',
            ],
        ];

        foreach ($strip as $i => $banner) {
            Banner::create($banner + ['placement' => 'strip', 'position' => $i]);
        }
    }
}
