<?php

namespace Database\Seeders;

use App\Models\Denuncia;
use Illuminate\Database\Seeder;
use Illuminate\Support\Facades\DB;

class DenunciaSeeder extends Seeder
{
    /**
     * Popula 48 denúncias: 12 pendentes, 18 em andamento e 18 resolvidas.
     * As 5 mais recentes (#1001–#1005) reproduzem exatamente a tela do dashboard.
     */
    public function run(): void
    {
        if (Denuncia::query()->exists()) {
            return;
        }

        $titulos = [
            'Esgoto a céu aberto', 'Semáforo com defeito', 'Bueiro entupido',
            'Placa de trânsito danificada', 'Terreno baldio sujo', 'Iluminação pública apagada',
            'Vazamento de água', 'Asfalto irregular', 'Mato alto em praça',
            'Entulho na calçada', 'Poda de árvore necessária', 'Pichação em prédio público',
            'Barulho excessivo', 'Fiação exposta', 'Ponto de ônibus danificado',
        ];

        $enderecos = [
            'Rua das Acácias, 210', 'Av. Brasil, 1500', 'Rua Sete de Setembro, 89',
            'Praça da Matriz', 'Rua dos Pinheiros, 340', 'Av. Getúlio Vargas, 720',
            'Rua XV de Novembro, 55', 'Travessa das Palmeiras, 12', 'Rua São João, 431',
            'Av. Independência, 980', 'Rua das Hortênsias, 67', 'Rua Marechal Deodoro, 204',
        ];

        // 43 registros antigos: 10 pendentes, 16 em andamento, 17 resolvidos
        $statusAntigos = array_merge(
            array_fill(0, 10, Denuncia::STATUS_PENDENTE),
            array_fill(0, 16, Denuncia::STATUS_EM_ANDAMENTO),
            array_fill(0, 17, Denuncia::STATUS_RESOLVIDO),
        );

        $registros = [];

        foreach ($statusAntigos as $i => $status) {
            $registros[] = [
                'id' => 958 + $i,
                'titulo' => $titulos[$i % count($titulos)],
                'descricao' => 'Denúncia registrada pelo munícipe através do aplicativo Conexão Município.',
                'endereco' => $enderecos[$i % count($enderecos)],
                'status' => $status,
                'data' => now()->parse('2025-01-10')->addDays((int) floor($i * 1.6))->toDateString(),
            ];
        }

        // 5 denúncias recentes — idênticas à tela do dashboard
        $recentes = [
            ['id' => 1001, 'titulo' => 'Buraco na rua', 'endereco' => 'Av. Principal, 123', 'status' => Denuncia::STATUS_PENDENTE, 'data' => '2025-03-27'],
            ['id' => 1002, 'titulo' => 'Lâmpada queimada', 'endereco' => 'Rua das Flores, 45', 'status' => Denuncia::STATUS_EM_ANDAMENTO, 'data' => '2025-03-26'],
            ['id' => 1003, 'titulo' => 'Lixo acumulado', 'endereco' => 'Praça Central', 'status' => Denuncia::STATUS_RESOLVIDO, 'data' => '2025-03-25'],
            ['id' => 1004, 'titulo' => 'Árvore caída', 'endereco' => 'Rua dos Ipês, 78', 'status' => Denuncia::STATUS_EM_ANDAMENTO, 'data' => '2025-03-24'],
            ['id' => 1005, 'titulo' => 'Calçada danificada', 'endereco' => 'Av. Secundária, 56', 'status' => Denuncia::STATUS_PENDENTE, 'data' => '2025-03-23'],
        ];

        foreach ($recentes as $recente) {
            $registros[] = $recente + [
                'descricao' => 'Denúncia registrada pelo munícipe através do aplicativo Conexão Município.',
            ];
        }

        $now = now();

        foreach ($registros as $registro) {
            Denuncia::query()->insert($registro + ['created_at' => $now, 'updated_at' => $now]);
        }

        if (DB::getDriverName() === 'pgsql') {
            DB::statement("SELECT setval(pg_get_serial_sequence('denuncias', 'id'), (SELECT max(id) FROM denuncias))");
        }
    }
}
