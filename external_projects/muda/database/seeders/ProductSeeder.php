<?php

namespace Database\Seeders;

use App\Models\Category;
use App\Models\Product;
use App\Models\ProductImage;
use App\Models\User;
use Illuminate\Database\Seeder;
use Illuminate\Support\Str;

class ProductSeeder extends Seeder
{
    private array $sizePools = [
        'clothing' => ['PP', 'P', 'M', 'G', 'GG'],
        'shoes' => ['35', '36', '37', '38', '39', '40', '41', '42'],
        'shoes_k' => ['22', '24', '26', '28', '30', '32'],
        'kids' => ['2 anos', '4 anos', '6 anos', '8 anos', '10 anos'],
        'unico' => ['Único'],
    ];

    private array $colors = ['Preto', 'Branco', 'Azul', 'Vermelho', 'Verde', 'Bege', 'Rosa', 'Cinza', 'Marrom', 'Estampado'];

    private array $notes = [
        'Pequenos sinais de uso, nada que comprometa.',
        'Leve desgaste natural do tempo.',
        'Ótimo estado geral, super conservado.',
        'Usado pouquíssimas vezes.',
        'Conservadíssimo, parece novo.',
        'Sem defeitos aparentes.',
    ];

    public function run(): void
    {
        $womenBrands = ['Zara', 'Farm', 'Renner', 'C&A', 'Hering', 'Amaro', 'Shoulder', 'Forever 21'];
        $menBrands = ['Reserva', 'Aramis', 'Lacoste', 'Colcci', 'Osklen', 'Polo Wear', 'Hering'];
        $shoeBrands = ['Nike', 'Adidas', 'Vans', 'Converse', 'Puma', 'Melissa', 'Arezzo', 'Mizuno'];
        $accBrands = ['Ray-Ban', 'Michael Kors', 'Guess', 'Fossil', 'Chilli Beans', 'Schutz', 'Petite Jolie'];
        $kidsBrands = ['Tip Top', 'Puket', 'Carinhoso', 'Marisol', 'Lilica Ripilica', 'Kyly'];
        $homeBrands = ['Tok&Stok', 'Oxford', 'Nadir', 'Tramontina', 'Camicado', 'Zara Home', 'Buettner'];
        $mediaBrands = ['Companhia das Letras', 'Panini', 'Universal Music', 'Sony Music', 'Editora Abril'];
        // Feito à mão: "marca" = ateliê/artesão
        $artBrands = ['Ateliê Manual', 'Feito à Mão', 'Arte & Fio', 'Cerâmica Viva', 'Raiz Artesanal', 'Mãos de Ouro'];
        $giftBrands = ['Personaliza', 'Mimo Fácil', 'Detalhes & Cia', 'Lembrança Feliz', 'Cantinho Criativo'];

        // slug => [brands, items[], sizeKey|null, [priceMin, priceMax]]
        $catalog = [
            'vestidos' => [$womenBrands, ['Vestido Floral Midi', 'Vestido Longo de Festa', 'Vestido Jeans Chemise', 'Vestido Tubinho Preto', 'Vestido Ciganinha', 'Vestido de Linho'], 'clothing', [39, 159]],
            'blusas-camisas' => [$womenBrands, ['Blusa de Tricô', 'Camisa Social Feminina', 'Body Canelado', 'Blusa Cropped', 'Camisa de Linho', 'Regata Básica'], 'clothing', [25, 89]],
            'calcas-jeans' => [$womenBrands, ['Calça Jeans Skinny', 'Calça Wide Leg', 'Calça Alfaiataria', 'Calça Cargo', 'Calça Flare Jeans'], 'clothing', [45, 139]],
            'saias-shorts' => [$womenBrands, ['Saia Midi Plissada', 'Short Jeans Destroyed', 'Saia Lápis', 'Short Alfaiataria', 'Saia Jeans'], 'clothing', [29, 99]],
            'camisas' => [$menBrands, ['Camisa Xadrez Flanela', 'Camisa Social Slim', 'Camisa de Linho', 'Camisa Jeans', 'Camisa Estampada'], 'clothing', [35, 119]],
            'camisetas' => [$menBrands, ['Camiseta Básica Algodão', 'Camiseta Estampada', 'Polo Piquet', 'Camiseta Oversized', 'Regata Esportiva'], 'clothing', [19, 69]],
            'calcas' => [$menBrands, ['Calça Jeans Reta', 'Calça Sarja', 'Calça Chino', 'Calça Cargo Masculina', 'Bermuda Sarja'], 'clothing', [39, 129]],
            'jaquetas' => [$menBrands, ['Jaqueta Jeans', 'Jaqueta de Couro', 'Blusão Moletom', 'Jaqueta Corta-Vento', 'Jaqueta Bomber'], 'clothing', [69, 259]],
            'tenis' => [$shoeBrands, ['Tênis Air Max', 'Tênis Superstar', 'Tênis Old Skool', 'Tênis All Star', 'Tênis Chunky'], 'shoes', [79, 349]],
            'sapatos' => [$shoeBrands, ['Sapato Oxford Couro', 'Scarpin Salto Alto', 'Mocassim', 'Sapatilha Bico Fino', 'Sandália Salto Bloco'], 'shoes', [49, 199]],
            'bolsas' => [$accBrands, ['Bolsa Tote de Couro', 'Bolsa Transversal', 'Mochila Urbana', 'Clutch de Festa', 'Bolsa Saco'], 'unico', [49, 299]],
            'bijuterias' => [$accBrands, ['Colar Camadas Dourado', 'Brinco Argola', 'Conjunto de Pulseiras', 'Anel Solitário', 'Óculos de Sol Aviador'], 'unico', [15, 120]],
            'croche-trico' => [$artBrands, ['Manta de Crochê', 'Cachecol de Tricô', 'Sousplat de Crochê', 'Amigurumi Bichinho', 'Touca de Lã', 'Jogo de Tapetes'], null, [29, 159]],
            'ceramica-vasos' => [$artBrands, ['Vaso de Cerâmica Pintado', 'Cachepô Artesanal', 'Conjunto de Canecas', 'Prato Decorativo', 'Tigela de Barro'], null, [35, 199]],
            'bijuterias-artesanais' => [$artBrands, ['Colar de Miçangas', 'Brinco de Macramê', 'Pulseira de Sementes', 'Anel de Pedras Naturais', 'Bracelete Boho'], 'unico', [19, 120]],
            'decoracao-artesanal' => [$artBrands, ['Mandala de Parede', 'Cesto de Fibra Natural', 'Quadro em Macramê', 'Móbile Decorativo', 'Luminária Artesanal'], null, [29, 189]],
            'lembrancinhas' => [$giftBrands, ['Sabonete Artesanal', 'Kit Mini Suculentas', 'Marcador de Livro', 'Chaveiro Personalizado', 'Caixinha de Doces'], null, [5, 49]],
            'papelaria' => [$giftBrands, ['Caderno Artesanal', 'Planner Personalizado', 'Kit de Adesivos', 'Cartões Feitos à Mão', 'Bloco de Notas'], null, [9, 79]],
            'brindes-personalizados' => [$giftBrands, ['Caneca Personalizada', 'Ecobag Estampada', 'Squeeze Personalizado', 'Camiseta Personalizada', 'Botton Personalizado'], null, [12, 99]],
            'velas-aromas' => [$giftBrands, ['Vela Aromática de Soja', 'Difusor de Ambiente', 'Sachê Perfumado', 'Home Spray', 'Kit Aromaterapia'], null, [15, 89]],
            'roupas-infantis' => [$kidsBrands, ['Conjunto Body Bebê', 'Vestido Infantil Festa', 'Macacão Jeans Infantil', 'Conjunto Moletom', 'Camiseta Dino'], 'kids', [19, 79]],
            'brinquedos' => [$kidsBrands, ['Blocos de Montar', 'Boneca de Pano', 'Carrinho Controle Remoto', 'Quebra-Cabeça 500pçs', 'Pelúcia Urso'], null, [25, 149]],
            'calcados-infantis' => [$kidsBrands, ['Tênis Infantil LED', 'Sandália Infantil', 'Bota Infantil', 'Sapatilha Infantil', 'Chuteira Society Infantil'], 'shoes_k', [29, 99]],
            'decoracao' => [$homeBrands, ['Espelho Redondo', 'Quadro Decorativo', 'Luminária de Mesa', 'Almofada Boho', 'Relógio de Parede'], null, [29, 199]],
            'utensilios' => [$homeBrands, ['Jogo de Panelas', 'Conjunto Taças Cristal', 'Tábua de Corte Madeira', 'Jarra de Vidro', 'Kit Facas Chef'], null, [39, 249]],
            'enxoval' => [$homeBrands, ['Jogo de Cama Casal', 'Toalha de Banho', 'Manta de Sofá', 'Colcha Patchwork', 'Jogo Americano'], null, [39, 179]],
            'livros' => [$mediaBrands, ['O Hobbit', 'Sapiens: Uma Breve História', 'A Revolução dos Bichos', 'Dom Casmurro', 'O Pequeno Príncipe'], null, [12, 59]],
            'discos-de-vinil' => [$mediaBrands, ['Vinil Rock Progressivo', 'Vinil MPB Clássico', 'Vinil Anos 80', 'Vinil Bossa Nova', 'Vinil Rock Nacional'], null, [29, 149]],
            'hqs-mangas' => [$mediaBrands, ['Mangá Shounen Vol. 1', 'One Piece Vol. 10', 'Graphic Novel do Batman', 'Encadernado Homem-Aranha', 'Box Coleção Mangá'], null, [15, 79]],
        ];

        $categoryIds = Category::pluck('id', 'slug');
        $sellers = User::where('is_seller', true)->get();   // 8 lojistas + root (super-tenant)
        $counter = 0;

        foreach ($catalog as $slug => [$brands, $items, $sizeKey, $priceRange]) {
            $categoryId = $categoryIds[$slug] ?? null;
            if (! $categoryId) {
                continue;
            }

            foreach ($items as $item) {
                $counter++;
                $brand = fake()->randomElement($brands);
                $condition = $this->weightedCondition();
                [$price, $compareAt, $installments] = $this->pricing($priceRange);
                $seller = $sellers->random();

                $product = Product::create([
                    'category_id' => $categoryId,
                    'seller_id' => $seller->id,
                    'title' => $item,
                    'slug' => Str::slug($item) . '-' . $counter,
                    'description' => $this->description($item, $brand, $condition),
                    'condition' => $condition,
                    'condition_note' => $condition === 'novo' ? null : fake()->randomElement($this->notes),
                    'brand' => $brand,
                    'size' => $sizeKey ? fake()->randomElement($this->sizePools[$sizeKey]) : null,
                    'color' => $sizeKey && $sizeKey !== 'unico' ? fake()->randomElement($this->colors) : null,
                    'price' => $price,
                    'compare_at_price' => $compareAt,
                    'max_installments' => $installments,
                    'free_shipping' => $price >= 199 || fake()->boolean(30),
                    'stock' => fake()->randomElement([1, 1, 1, 2, 3]),  // brechó pieces are mostly unique
                    'weight_grams' => fake()->numberBetween(150, 1800),
                    'length_cm' => fake()->numberBetween(18, 40),
                    'width_cm' => fake()->numberBetween(12, 30),
                    'height_cm' => fake()->numberBetween(3, 20),
                    'sku' => 'MUDA-' . str_pad((string) $counter, 5, '0', STR_PAD_LEFT),
                    'is_active' => true,
                    'is_featured' => fake()->boolean(14),
                    'rating' => fake()->randomFloat(1, 4.0, 5.0),
                    'reviews_count' => fake()->numberBetween(0, 240),
                    'sold_count' => fake()->numberBetween(0, 480),
                    'seller_name' => $seller->store_name,
                    'seller_location' => $seller->store_location,
                ]);

                $this->attachImages($product);
            }
        }
    }

    private function weightedCondition(): string
    {
        $r = mt_rand(1, 100);

        return $r <= 15 ? 'novo' : ($r <= 50 ? 'seminovo' : 'usado');
    }

    /**
     * @return array{0: float, 1: float|null, 2: int}
     */
    private function pricing(array $range): array
    {
        $price = mt_rand($range[0], $range[1]) - 0.10;

        $compareAt = fake()->boolean(45)
            ? floor($price * fake()->randomFloat(2, 1.15, 1.6)) + 0.90
            : null;

        $installments = min(12, max(1, (int) floor($price / 15)));

        return [$price, $compareAt, $installments];
    }

    private function description(string $item, string $brand, string $condition): string
    {
        $labels = ['novo' => 'nova com etiqueta', 'seminovo' => 'seminova', 'usado' => 'usada'];

        return "{$item} da marca {$brand}, peça {$labels[$condition]} garimpada com carinho. "
            . 'Ideal para quem busca estilo com consumo consciente. Confira as medidas e o estado nas fotos '
            . 'e aproveite este achado de brechó antes que acabe — a maioria das peças é única!';
    }

    private function attachImages(Product $product): void
    {
        $count = fake()->numberBetween(2, 4);

        for ($i = 0; $i < $count; $i++) {
            ProductImage::create([
                'product_id' => $product->id,
                'path' => placeholder_image($product->slug . '-' . $i, $product->title, 700, 700),
                'alt' => $product->title . ' - foto ' . ($i + 1),
                'is_primary' => $i === 0,
                'position' => $i,
            ]);
        }
    }
}
