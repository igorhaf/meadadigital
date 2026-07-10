<?php

namespace Database\Seeders;

use App\Models\User;
use Illuminate\Database\Seeder;
use Illuminate\Support\Facades\Hash;

class DatabaseSeeder extends Seeder
{
    public function run(): void
    {
        // Root user: acts as a tenant (sells/manages products) AND administers the
        // whole site (banners, social links, etc.).
        User::updateOrCreate(
            ['email' => 'root@muda.com.br'],
            [
                'name' => 'Root Muda',
                'password' => Hash::make('password'),
                'role' => 'root',
                'is_seller' => true,
                'store_name' => 'Muda Oficial',
                'store_slug' => 'muda-oficial',
                'store_bio' => 'Loja oficial da Muda — curadoria especial de achados de brechó.',
                'store_location' => 'São Paulo, SP',
            ]
        );

        // Demo customer (to showcase "Meus pedidos").
        User::updateOrCreate(
            ['email' => 'cliente@muda.com.br'],
            [
                'name' => 'Cliente Demo',
                'password' => Hash::make('password'),
                'role' => 'customer',
                'is_seller' => false,
            ]
        );

        $this->call([
            CategorySeeder::class,
            SellerSeeder::class,
            ProductSeeder::class,
            BannerSeeder::class,
            SiteSettingSeeder::class,
            PageSeeder::class,
            OrderSeeder::class,
        ]);
    }
}
