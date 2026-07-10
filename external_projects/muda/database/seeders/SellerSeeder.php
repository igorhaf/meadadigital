<?php

namespace Database\Seeders;

use App\Models\User;
use Illuminate\Database\Seeder;
use Illuminate\Support\Facades\Hash;
use Illuminate\Support\Str;

class SellerSeeder extends Seeder
{
    /** [pessoa, loja, cidade, bio] */
    private array $sellers = [
        ['Luana Prado', 'Brechó da Lu', 'São Paulo, SP', 'Garimpos femininos selecionados a dedo, com muito carinho e estilo.'],
        ['Carlos Menezes', 'Garimpo Vintage', 'Rio de Janeiro, RJ', 'Peças vintage e streetwear raro para quem foge do óbvio.'],
        ['Mariana Alves', 'Desapega Chic', 'Belo Horizonte, MG', 'Moda circular chique: marcas queridas por preços justos.'],
        ['Pedro Faria', 'Ateliê Reuse', 'Curitiba, PR', 'Roupas restauradas e customizadas com propósito sustentável.'],
        ['Juliana Rocha', 'Segunda Mão Store', 'Porto Alegre, RS', 'O melhor da segunda mão: qualidade que dura mais uma vida.'],
        ['Fernanda Lima', 'Reveste Brechó', 'Recife, PE', 'Achados nordestinos com muito axé e bom gosto.'],
        ['Roberto Dias', 'Baú de Tesouros', 'Salvador, BA', 'Relíquias, acessórios e colecionáveis garimpados na Bahia.'],
        ['Camila Souza', 'Circular Moda', 'Florianópolis, SC', 'Moda consciente da ilha: menos consumo, mais estilo.'],
    ];

    public function run(): void
    {
        foreach ($this->sellers as [$name, $store, $location, $bio]) {
            $slug = Str::slug($store);
            User::updateOrCreate(
                ['email' => $slug . '@muda.com.br'],
                [
                    'name' => $name,
                    'password' => Hash::make('password'),
                    'role' => 'seller',
                    'is_seller' => true,
                    'store_name' => $store,
                    'store_slug' => $slug,
                    'store_bio' => $bio,
                    'store_location' => $location,
                ]
            );
        }
    }
}
