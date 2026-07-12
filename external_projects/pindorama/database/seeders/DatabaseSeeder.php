<?php

namespace Database\Seeders;

use App\Models\AttendanceLocation;
use App\Models\Service;
use App\Models\ServiceCategory;
use App\Models\User;
use Illuminate\Database\Seeder;
use Illuminate\Support\Facades\Hash;
use Illuminate\Support\Str;

class DatabaseSeeder extends Seeder
{
    /** Foto de capa por título de serviço (arquivos em public/images/services). */
    private const SERVICE_IMAGES = [
        'Acupuntura Sistêmica' => '/images/services/acupuntura.jpg',
        'Auriculoterapia' => '/images/services/auriculoterapia.jpg',
        'Reiki Nível 1' => '/images/services/reiki.jpg',
        'Consulta Ayurvédica' => '/images/services/ayurveda.jpg',
        'Massagem Relaxante' => '/images/services/massagem.jpg',
        'Yoga Individual' => '/images/services/yoga.jpg',
        'Meditação Guiada' => '/images/services/meditacao.jpg',
    ];

    /** @var array<string, ServiceCategory> */
    private array $cats = [];

    public function run(): void
    {
        $this->seedCategories();

        // Root: administra o site e também passa em todos os gates de tenant.
        User::updateOrCreate(['email' => 'root@pindorama.com.br'], [
            'name' => 'Root Pindorama',
            'password' => Hash::make('password'),
            'role' => 'root',
            'is_professional' => true,
        ]);

        // Cliente/paciente demo.
        User::updateOrCreate(['email' => 'cliente@pindorama.com.br'], [
            'name' => 'Carla Cliente',
            'password' => Hash::make('password'),
            'role' => 'customer',
            'is_professional' => false,
        ]);

        $this->seedProfessional('ana@pindorama.com.br', 'Ana Prado', 'ana-prado', [
            'headline' => 'Acupunturista • Terapeuta Reiki',
            'bio' => "Há 10 anos cuidando do equilíbrio de corpo e mente com práticas integrativas.\nAtendimento humanizado e individualizado.",
            'city' => 'São Paulo', 'state' => 'SP', 'whatsapp' => '11999990000',
            'brand_primary' => '#b0552c', 'brand_secondary' => '#a0711a', 'is_verified' => true,
        ], ['acupuntura', 'auriculoterapia', 'reiki'], [
            ['name' => 'Consultório Vila Mariana', 'is_online' => false, 'address' => 'Rua Sena Madureira, 120', 'neighborhood' => 'Vila Mariana', 'city' => 'São Paulo', 'state' => 'SP'],
            ['name' => 'Atendimento Online', 'is_online' => true],
        ], [
            ['acupuntura', 'Acupuntura Sistêmica', 'presencial', 60, 180, 240, true, 42, ['Consultório Vila Mariana']],
            ['auriculoterapia', 'Auriculoterapia', 'presencial', 40, 120, null, false, 17, ['Consultório Vila Mariana']],
            ['reiki', 'Reiki Nível 1', 'ambos', 50, 150, null, true, 31, ['Consultório Vila Mariana', 'Atendimento Online']],
        ]);

        $this->seedProfessional('bruno@pindorama.com.br', 'Bruno Lima', 'bruno-lima', [
            'headline' => 'Terapeuta Ayurvédico',
            'bio' => 'Consultas ayurvédicas e massoterapia com foco em bem-estar integral.',
            'city' => 'Rio de Janeiro', 'state' => 'RJ', 'whatsapp' => '21988887777',
            'brand_primary' => '#2f5a44', 'brand_secondary' => '#3f7357', 'is_verified' => true,
        ], ['ayurveda', 'massoterapia'], [
            ['name' => 'Espaço Bem-Viver Ipanema', 'is_online' => false, 'address' => 'Rua Visconde de Pirajá, 500', 'neighborhood' => 'Ipanema', 'city' => 'Rio de Janeiro', 'state' => 'RJ'],
            ['name' => 'Atendimento Online', 'is_online' => true],
        ], [
            ['ayurveda', 'Consulta Ayurvédica', 'online', 90, 300, 380, true, 25, ['Atendimento Online']],
            ['massoterapia', 'Massagem Relaxante', 'presencial', 60, 160, null, false, 53, ['Espaço Bem-Viver Ipanema']],
        ]);

        $this->seedProfessional('celia@pindorama.com.br', 'Célia Nunes', 'celia-nunes', [
            'headline' => 'Instrutora de Yoga & Meditação',
            'bio' => 'Práticas de yoga e meditação guiada para reduzir ansiedade e melhorar o sono.',
            'city' => 'São Paulo', 'state' => 'SP', 'whatsapp' => '11977776666',
            'brand_primary' => '#3f7357', 'brand_secondary' => '#c08f24', 'is_verified' => false,
        ], ['yoga', 'meditacao'], [
            ['name' => 'Estúdio Pinheiros', 'is_online' => false, 'address' => 'Rua dos Pinheiros, 800', 'neighborhood' => 'Pinheiros', 'city' => 'São Paulo', 'state' => 'SP'],
            ['name' => 'Atendimento Online', 'is_online' => true],
        ], [
            ['yoga', 'Yoga Individual', 'ambos', 60, 130, null, false, 12, ['Estúdio Pinheiros', 'Atendimento Online']],
            ['meditacao', 'Meditação Guiada', 'online', 45, 90, null, false, 9, ['Atendimento Online']],
        ]);

        $this->seedCommission();
        $this->seedEvents();
        $this->seedBanners();

        $this->call([SiteSettingSeeder::class, PageSeeder::class]);
    }

    private function seedBanners(): void
    {
        // Gradientes na paleta da identidade (terracota / verde-mata / dourado);
        // heroes levam foto de fundo (public/images/banners) com scrim do bg_from.
        $hero = [
            ['Equilíbrio que nasce da terra', 'Acupuntura, reiki, ayurveda e mais de dez práticas integrativas com terapeutas verificados.', 'Explorar terapias', '/busca', '#78381f', '#bf6d3d', '/images/banners/hero-terra.jpg'],
            ['Cuidado no seu ritmo, onde você estiver', 'Sessões presenciais ou online — escolha o melhor horário e agende em minutos.', 'Conhecer os terapeutas', '/terapeutas', '#213c30', '#3f7357', '/images/banners/hero-online.jpg'],
            ['Rodas, cursos e vivências', 'Experiências em grupo para aprofundar sua prática, ao vivo ou online.', 'Ver agenda de eventos', '/eventos', '#815816', '#c08f24', '/images/banners/hero-eventos.jpg'],
        ];

        $strip = [
            ['Atendimento online', 'Bem-estar sem sair de casa', 'Buscar sessões', '/busca', '#274a39', '#3f7357', null],
            ['Seja um terapeuta', 'Atenda no espaço Pindorama ou online', 'Começar agora', '/seja-terapeuta', '#93421f', '#cd8c5e', null],
            ['Eventos abertos', 'Rodas de cura e meditações gratuitas', 'Ver eventos', '/eventos', '#815816', '#d2a63f', null],
        ];

        foreach (['hero' => $hero, 'strip' => $strip] as $placement => $banners) {
            foreach ($banners as $i => [$title, $subtitle, $cta, $link, $from, $to, $image]) {
                \App\Models\Banner::updateOrCreate(['placement' => $placement, 'position' => $i + 1], [
                    'title' => $title,
                    'subtitle' => $subtitle,
                    'cta_label' => $cta,
                    'link_url' => $link,
                    'bg_from' => $from,
                    'bg_to' => $to,
                    'image_path' => $image,
                    'is_active' => true,
                ]);
            }
        }
    }

    private function seedEvents(): void
    {
        $ana = User::where('email', 'ana@pindorama.com.br')->first();
        $bruno = User::where('email', 'bruno@pindorama.com.br')->first();

        \App\Models\Event::updateOrCreate(['slug' => 'roda-de-cura-reiki'], [
            'professional_id' => $ana->id,
            'title' => 'Roda de Cura com Reiki',
            'description' => "Encontro em grupo para harmonização energética com Reiki.\nTraga um tapete ou almofada.",
            'type' => 'roda', 'modality' => 'presencial', 'location_label' => 'Consultório Vila Mariana, São Paulo',
            'starts_at' => now()->addDays(7)->setTime(19, 0), 'ends_at' => now()->addDays(7)->setTime(21, 0),
            'capacity' => 10, 'price' => 0, 'is_free' => true, 'status' => 'published', 'reminder_hours' => 24,
        ]);

        \App\Models\Event::updateOrCreate(['slug' => 'curso-introducao-ayurveda'], [
            'professional_id' => $bruno->id,
            'title' => 'Curso: Introdução ao Ayurveda',
            'description' => 'Fundamentos do Ayurveda em 4 encontros online.',
            'type' => 'curso', 'modality' => 'online', 'location_label' => 'Google Meet (link enviado por email)',
            'starts_at' => now()->addDays(14)->setTime(20, 0), 'ends_at' => now()->addDays(14)->setTime(22, 0),
            'capacity' => 20, 'price' => 150, 'is_free' => false, 'allow_discount' => true, 'discount_percent' => 10,
            'status' => 'published', 'reminder_hours' => 48,
        ]);
    }

    private function seedCommission(): void
    {
        // Aluguel/comissão padrão da plataforma: 20%.
        \App\Models\CommissionRule::updateOrCreate(
            ['scope_type' => 'default', 'scope_id' => null],
            ['rate_type' => 'percent', 'rate_value' => 20, 'is_active' => true],
        );

        // Sala física do espaço Pindorama + vincula um consultório a ela (regra por sala 30%).
        $room = \App\Models\Room::updateOrCreate(['name' => 'Sala Zen'], [
            'description' => 'Sala do espaço Pindorama', 'is_active' => true, 'position' => 1,
        ]);
        $consultorio = \App\Models\AttendanceLocation::where('name', 'Consultório Vila Mariana')->first();
        if ($consultorio) {
            $consultorio->update(['room_id' => $room->id]);
        }
        \App\Models\CommissionRule::updateOrCreate(
            ['scope_type' => 'room', 'scope_id' => $room->id],
            ['rate_type' => 'percent', 'rate_value' => 30, 'is_active' => true],
        );
    }

    private function seedCategories(): void
    {
        $tree = [
            ['Medicina Tradicional Chinesa', 'mtc', '☯️', '#b0552c', [
                ['Acupuntura', 'acupuntura', '🪡'],
                ['Auriculoterapia', 'auriculoterapia', '👂'],
            ]],
            ['Terapias Energéticas', 'energeticas', '✨', '#a0711a', [
                ['Reiki', 'reiki', '🙌'],
                ['Florais', 'florais', '🌼'],
            ]],
            ['Ayurveda', 'ayurveda-raiz', '🌸', '#2f5a44', [['Ayurveda', 'ayurveda', '🌸']]],
            ['Terapias Corporais', 'corporais', '💆', '#bf6d3d', [['Massoterapia', 'massoterapia', '💆']]],
            ['Movimento & Mente', 'movimento', '🧘', '#3f7357', [
                ['Yoga', 'yoga', '🧘'],
                ['Meditação', 'meditacao', '🌬️'],
            ]],
        ];

        $pos = 1;
        foreach ($tree as [$name, $slug, $icon, $accent, $children]) {
            $root = ServiceCategory::updateOrCreate(['slug' => $slug], [
                'name' => $name, 'icon' => $icon, 'accent' => $accent, 'position' => $pos++, 'is_active' => true,
            ]);
            $cpos = 1;
            foreach ($children as [$cname, $cslug, $cicon]) {
                $this->cats[$cslug] = ServiceCategory::updateOrCreate(['slug' => $cslug], [
                    'name' => $cname, 'icon' => $cicon, 'accent' => $accent, 'parent_id' => $root->id,
                    'position' => $cpos++, 'is_active' => true,
                ]);
            }
        }
    }

    /**
     * @param  array<string,mixed>  $profile
     * @param  array<int,string>  $specialties
     * @param  array<int,array<string,mixed>>  $locations
     * @param  array<int,array{0:string,1:string,2:string,3:int,4:int,5:?int,6:bool,7:int,8:array<int,string>}>  $services
     */
    private function seedProfessional(string $email, string $name, string $slug, array $profile, array $specialties, array $locations, array $services): void
    {
        $pro = User::updateOrCreate(['email' => $email], array_merge([
            'name' => $name,
            'password' => Hash::make('password'),
            'role' => 'professional',
            'is_professional' => true,
            'professional_name' => $name,
            'professional_slug' => $slug,
        ], $profile));

        $pro->specialties()->sync(collect($specialties)->map(fn ($s) => $this->cats[$s]->id)->all());

        $locByName = [];
        foreach ($locations as $loc) {
            $location = AttendanceLocation::updateOrCreate(
                ['professional_id' => $pro->id, 'name' => $loc['name']],
                array_merge(['is_active' => true, 'is_online' => false], $loc),
            );
            $locByName[$loc['name']] = $location;

            // Horário semanal: seg–sex, 09–12 e 14–18.
            $location->availabilities()->delete();
            foreach (range(1, 5) as $weekday) {
                foreach ([['09:00', '12:00'], ['14:00', '18:00']] as [$start, $end]) {
                    \App\Models\ProfessionalAvailability::create([
                        'professional_id' => $pro->id,
                        'attendance_location_id' => $location->id,
                        'weekday' => $weekday,
                        'start_time' => $start,
                        'end_time' => $end,
                        'is_active' => true,
                    ]);
                }
            }
        }

        foreach ($services as [$catSlug, $title, $modality, $duration, $price, $compare, $featured, $bookings, $locNames]) {
            $service = Service::updateOrCreate(['slug' => Str::slug($name . ' ' . $title)], [
                'professional_id' => $pro->id,
                'service_category_id' => $this->cats[$catSlug]->id,
                'title' => $title,
                'description' => "Sessão de {$title} com {$name}.",
                'modality' => $modality,
                'duration_minutes' => $duration,
                'price' => $price,
                'compare_at_price' => $compare,
                'max_installments' => 3,
                'is_active' => true,
                'is_featured' => $featured,
                'bookings_count' => $bookings,
                'rating' => 4.8,
                'reviews_count' => 12,
                'professional_name' => $name,
                'professional_city' => $profile['city'] ?? null,
                'professional_state' => $profile['state'] ?? null,
            ]);
            $service->locations()->sync(collect($locNames)->map(fn ($n) => $locByName[$n]->id)->all());

            if ($imagePath = self::SERVICE_IMAGES[$title] ?? null) {
                \App\Models\ServiceImage::updateOrCreate(
                    ['service_id' => $service->id, 'position' => 1],
                    ['path' => $imagePath, 'alt' => "{$title} — {$name}", 'is_primary' => true],
                );
            }
        }
    }
}
