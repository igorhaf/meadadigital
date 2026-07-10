<?php

namespace Database\Seeders;

use App\Models\Category;
use Illuminate\Database\Seeder;
use Illuminate\Support\Str;

class CategorySeeder extends Seeder
{
    /**
     * Root category => [icon, accent, [subcategories...]]
     */
    // 6 categorias macro (cabem no menu horizontal sem cortar). Nenhuma
    // subcategoria foi removida — apenas reagrupadas em macros mais enxutas.
    private array $tree = [
        'Roupas Femininas' => ['👗', '#ec4899', ['Vestidos', 'Blusas & Camisas', 'Calças & Jeans', 'Saias & Shorts']],
        'Roupas Masculinas' => ['👔', '#3b82f6', ['Camisas', 'Camisetas', 'Calças', 'Jaquetas']],
        'Calçados & Acessórios' => ['👟', '#f59e0b', ['Tênis', 'Sapatos', 'Bolsas', 'Bijuterias']],
        'Artesanato & Brindes' => ['🎨', '#14b8a6', ['Crochê & Tricô', 'Cerâmica & Vasos', 'Bijuterias Artesanais', 'Decoração Artesanal', 'Lembrancinhas', 'Papelaria', 'Brindes Personalizados', 'Velas & Aromas']],
        'Infantil' => ['🧸', '#22c55e', ['Roupas Infantis', 'Brinquedos', 'Calçados Infantis']],
        'Casa & Cultura' => ['🏡', '#0ea5e9', ['Decoração', 'Utensílios', 'Enxoval', 'Livros', 'Discos de Vinil', 'HQs & Mangás']],
    ];

    public function run(): void
    {
        $rootPos = 0;

        foreach ($this->tree as $name => [$icon, $accent, $children]) {
            $root = Category::create([
                'name' => $name,
                'slug' => Str::slug($name),
                'icon' => $icon,
                'accent' => $accent,
                'position' => $rootPos++,
            ]);

            $childPos = 0;
            foreach ($children as $child) {
                Category::create([
                    'parent_id' => $root->id,
                    'name' => $child,
                    'slug' => Str::slug($child),
                    'icon' => $icon,
                    'accent' => $accent,
                    'position' => $childPos++,
                ]);
            }
        }
    }
}
