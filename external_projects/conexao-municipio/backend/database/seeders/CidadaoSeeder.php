<?php

namespace Database\Seeders;

use App\Models\Denuncia;
use App\Models\Noticia;
use Illuminate\Database\Seeder;

/**
 * Dados da experiência do cidadão (app mobile): notícias da cidade,
 * denúncias registradas pelo app e backfill de categoria nas denúncias antigas.
 */
class CidadaoSeeder extends Seeder
{
    public function run(): void
    {
        $this->backfillCategorias();
        $this->seedDenunciasMobile();
        $this->seedNoticias();
    }

    /** Deduz a categoria das denúncias antigas a partir do título. */
    private function backfillCategorias(): void
    {
        $mapa = [
            'buracos' => ['buraco', 'asfalto'],
            'iluminacao' => ['lâmpada', 'iluminação', 'fiação', 'poste'],
            'lixo' => ['lixo', 'entulho', 'terreno baldio'],
            'esgoto' => ['esgoto', 'bueiro', 'vazamento'],
            'transito' => ['semáforo', 'placa', 'ponto de ônibus', 'calçada'],
            'vandalismo' => ['pichação', 'vandalismo'],
        ];

        Denuncia::query()
            ->whereNull('categoria')
            ->each(function (Denuncia $denuncia) use ($mapa) {
                $titulo = mb_strtolower($denuncia->titulo);

                foreach ($mapa as $categoria => $palavras) {
                    foreach ($palavras as $palavra) {
                        if (str_contains($titulo, $palavra)) {
                            $denuncia->update(['categoria' => $categoria]);

                            return;
                        }
                    }
                }

                $denuncia->update(['categoria' => Denuncia::CATEGORIA_OUTROS]);
            });
    }

    /** Denúncias de exemplo registradas pelo aplicativo do cidadão. */
    private function seedDenunciasMobile(): void
    {
        $denuncias = [
            [
                'titulo' => 'Buraco na Rua das Flores',
                'descricao' => 'Buraco grande próximo ao número 123.',
                'endereco' => 'Rua das Flores, 123',
                'categoria' => Denuncia::CATEGORIA_BURACOS,
                'origem' => Denuncia::ORIGEM_MOBILE,
                'status' => Denuncia::STATUS_PENDENTE,
                'data' => '2025-03-18',
            ],
            [
                'titulo' => 'Lâmpada queimada',
                'descricao' => 'Poste na Av. Principal, em frente ao mercado.',
                'endereco' => 'Av. Principal, s/n',
                'categoria' => Denuncia::CATEGORIA_ILUMINACAO,
                'origem' => Denuncia::ORIGEM_MOBILE,
                'status' => Denuncia::STATUS_RESOLVIDO,
                'data' => '2025-03-15',
            ],
        ];

        foreach ($denuncias as $denuncia) {
            Denuncia::query()->firstOrCreate(
                [
                    'titulo' => $denuncia['titulo'],
                    'origem' => Denuncia::ORIGEM_MOBILE,
                    'data' => $denuncia['data'],
                ],
                $denuncia,
            );
        }
    }

    private function seedNoticias(): void
    {
        if (Noticia::query()->exists()) {
            return;
        }

        Noticia::query()->insert([
            [
                'titulo' => 'Prefeitura anuncia novo programa de habitação',
                'resumo' => 'Famílias de baixa renda poderão se inscrever a partir do próximo mês.',
                'data' => '2025-03-20',
                'created_at' => now(),
                'updated_at' => now(),
            ],
            [
                'titulo' => 'Campanha de vacinação contra a gripe começa na segunda',
                'resumo' => 'Postos de saúde atenderão das 8h às 17h durante toda a semana.',
                'data' => '2025-03-18',
                'created_at' => now(),
                'updated_at' => now(),
            ],
            [
                'titulo' => 'Obras de recapeamento avançam na região central',
                'resumo' => 'Trechos da Av. Principal terão interdição parcial no fim de semana.',
                'data' => '2025-03-15',
                'created_at' => now(),
                'updated_at' => now(),
            ],
        ]);
    }
}
