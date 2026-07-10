<?php

namespace App\Http\Controllers;

use App\Models\Order;
use App\Models\Product;
use App\Services\MercadoPagoService;
use App\Services\Shipping\ShippingService;
use Illuminate\Http\RedirectResponse;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Str;

class CheckoutController extends Controller
{
    private const FREE_SHIPPING_THRESHOLD = 199;
    private const SHIPPING_FEE = 19.90;

    /**
     * Turn the client-side cart into a persisted order and start payment.
     * Prices are recomputed from the database — never trusted from the client.
     */
    public function store(Request $request, MercadoPagoService $mp, ShippingService $shippingService): RedirectResponse
    {
        $data = $request->validate([
            'buyer_name' => ['required', 'string', 'max:255'],
            'buyer_email' => ['required', 'email', 'max:255'],
            'buyer_phone' => ['nullable', 'string', 'max:50'],
            'shipping_address' => ['nullable', 'string', 'max:500'],
            'shipping_option_id' => ['nullable', 'string', 'max:50'],
            'shipping_cep' => ['nullable', 'string', 'max:20'],
            'items' => ['required', 'json'],
        ]);

        $lines = collect(json_decode($data['items'], true) ?: [])
            ->filter(fn ($i) => isset($i['id']) && (int) ($i['qty'] ?? 0) > 0);

        if ($lines->isEmpty()) {
            return back()->with('status', 'Seu carrinho está vazio.');
        }

        $products = Product::active()->whereIn('id', $lines->pluck('id'))->get()->keyBy('id');

        if ($products->isEmpty()) {
            return back()->with('status', 'Os itens do carrinho não estão mais disponíveis.');
        }

        $order = DB::transaction(function () use ($request, $data, $lines, $products, $mp, $shippingService) {
            $order = Order::create([
                'reference' => 'MUDA-' . Str::upper(Str::random(8)),
                'user_id' => $request->user()->id,
                'buyer_name' => $data['buyer_name'],
                'buyer_email' => $data['buyer_email'],
                'buyer_phone' => $data['buyer_phone'] ?? null,
                'shipping_address' => $data['shipping_address'] ?? null,
                'status' => 'pending',
                'payment_status' => 'pending',
                'payment_method' => $mp->enabled() ? 'mercadopago' : 'simulado',
            ]);

            $subtotal = 0;
            foreach ($lines as $line) {
                $product = $products->get((int) $line['id']);
                if (! $product) {
                    continue;
                }

                $qty = min(99, max(1, (int) $line['qty']));
                $lineTotal = (float) $product->price * $qty;
                $subtotal += $lineTotal;

                $order->items()->create([
                    'product_id' => $product->id,
                    'seller_id' => $product->seller_id,
                    'title' => $product->title,
                    'image_path' => $product->primary_image_url,
                    'price' => $product->price,
                    'qty' => $qty,
                    'line_total' => $lineTotal,
                ]);

                $product->increment('sold_count', $qty);
            }

            $freight = $this->resolveShipping($shippingService, $lines, $data, $subtotal);
            $order->update([
                'subtotal' => $subtotal,
                'shipping' => $freight['price'],
                'total' => $subtotal + $freight['price'],
                'shipping_carrier' => $freight['carrier'],
                'shipping_service' => $freight['service'],
                'shipping_cep' => $freight['cep'],
                'shipping_days' => $freight['days'],
            ]);

            return $order;
        });

        // Checkout Transparente: leva à nossa página de pagamento (Payment Brick).
        if ($mp->enabled()) {
            return redirect()->route('payment.show', $order);
        }

        // Fallback (sem credenciais MP): pagamento simulado, para a demo funcionar.
        $order->applyPaymentStatus('approved');

        return redirect()->route('orders.show', $order)->with('placed', true);
    }

    /**
     * Re-quote shipping on the server and resolve the chosen option (never
     * trusting the client price). Falls back to a flat/free rate.
     *
     * @return array{price:float,carrier:?string,service:?string,days:?int,cep:?string}
     */
    private function resolveShipping(ShippingService $shippingService, $lines, array $data, float $subtotal): array
    {
        $cep = preg_replace('/\D/', '', (string) ($data['shipping_cep'] ?? ''));

        if (strlen($cep) === 8) {
            $package = $shippingService->packageForLines($lines);
            $options = $shippingService->quote($cep, $package, $subtotal);

            if (! empty($options)) {
                $chosen = collect($options)->firstWhere('id', $data['shipping_option_id'] ?? null) ?? $options[0];

                return [
                    'price' => (float) $chosen['price'],
                    'carrier' => $chosen['carrier'],
                    'service' => $chosen['service'],
                    'days' => $chosen['days'],
                    'cep' => $cep,
                ];
            }
        }

        $price = $subtotal >= self::FREE_SHIPPING_THRESHOLD ? 0.0 : self::SHIPPING_FEE;

        return ['price' => $price, 'carrier' => null, 'service' => 'Padrão', 'days' => null, 'cep' => $cep ?: null];
    }
}
