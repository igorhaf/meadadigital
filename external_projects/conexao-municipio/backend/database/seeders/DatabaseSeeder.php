<?php

namespace Database\Seeders;

use App\Models\User;
use Illuminate\Database\Console\Seeds\WithoutModelEvents;
use Illuminate\Database\Seeder;

class DatabaseSeeder extends Seeder
{
    use WithoutModelEvents;

    /**
     * Seed the application's database.
     */
    public function run(): void
    {
        $admin = User::query()->where('email', 'admin@prefeitura.gov.br')->first();

        if ($admin) {
            $admin->update(['role' => User::ROLE_ADMIN]);
        } else {
            User::factory()->create([
                'name' => 'Administrador',
                'email' => 'admin@prefeitura.gov.br',
                'role' => User::ROLE_ADMIN,
            ]);
        }

        $this->call(DenunciaSeeder::class);
        $this->call(ConfiguracaoSeeder::class);
        $this->call(CidadaoSeeder::class);
    }
}
