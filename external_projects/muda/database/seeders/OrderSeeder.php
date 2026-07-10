<?php

namespace Database\Seeders;

use App\Models\Order;
use App\Models\OrderItem;
use App\Models\Product;
use App\Models\User;
use Illuminate\Database\Seeder;

class OrderSeeder extends Seeder
{
    public function run(): void
    {
        $customer = User::where('email', 'cliente@muda.com.br')->first();
        if (! $customer) {
            return;
        }

        $pool = Product::with('images')->inRandomOrder()->take(40)->get();
        $statuses = ['paid', 'paid', 'shipped', 'delivered'];

        for ($i = 0; $i < 8; $i++) {
            $picked = $pool->shuffle()->take(rand(1, 3));

            $order = Order::create([
                'reference' => 'MUDA-' . str_pad((string) (1001 + $i), 5, '0', STR_PAD_LEFT),
                'user_id' => $customer->id,
                'buyer_name' => $customer->name,
                'buyer_email' => $customer->email,
                'buyer_phone' => '(11) 90000-0000',
                'shipping_address' => 'Rua das Flores, 123 — São Paulo, SP',
                'status' => $statuses[array_rand($statuses)],
                'payment_status' => 'approved',
                'payment_method' => 'simulado',
            ]);

            $subtotal = 0;
            foreach ($picked as $p) {
                $qty = rand(1, 2);
                $line = (float) $p->price * $qty;
                $subtotal += $line;

                OrderItem::create([
                    'order_id' => $order->id,
                    'product_id' => $p->id,
                    'seller_id' => $p->seller_id,
                    'title' => $p->title,
                    'image_path' => $p->primary_image_url,
                    'price' => $p->price,
                    'qty' => $qty,
                    'line_total' => $line,
                ]);
            }

            $shipping = $subtotal >= 199 ? 0 : 19.90;
            $order->forceFill([
                'subtotal' => $subtotal,
                'shipping' => $shipping,
                'total' => $subtotal + $shipping,
                'created_at' => now()->subDays(rand(1, 40)),
            ])->save();
        }
    }
}
