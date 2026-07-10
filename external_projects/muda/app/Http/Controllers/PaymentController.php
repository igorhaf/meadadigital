<?php

namespace App\Http\Controllers;

use App\Models\Order;
use App\Services\MercadoPagoService;
use Illuminate\Contracts\View\View;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\RedirectResponse;
use Illuminate\Http\Request;

class PaymentController extends Controller
{
    /** Página de pagamento (Checkout Transparente — Payment Brick do MP). */
    public function show(Order $order, MercadoPagoService $mp): View|RedirectResponse
    {
        abort_unless($order->user_id === auth()->id() || auth()->user()->isRoot(), 403);

        if ($order->isPaid()) {
            return redirect()->route('orders.show', $order);
        }

        if (! $mp->enabled()) {
            $order->applyPaymentStatus('approved');

            return redirect()->route('orders.show', $order)->with('placed', true);
        }

        $order->load('items');

        return view('payment.show', ['order' => $order, 'publicKey' => $mp->publicKey()]);
    }

    /** Recebe a formData do Brick e cria o pagamento via API de Pagamentos. */
    public function process(Request $request, Order $order, MercadoPagoService $mp): JsonResponse
    {
        abort_unless($order->user_id === auth()->id() || auth()->user()->isRoot(), 403);

        if ($order->isPaid()) {
            return response()->json(['status' => $order->payment_status, 'redirect' => route('orders.show', $order)]);
        }

        try {
            $payment = $mp->createPayment($order, $request->all());
        } catch (\Throwable $e) {
            report($e);

            return response()->json(['error' => 'Não foi possível processar o pagamento. Tente outro cartão.'], 422);
        }

        $status = (string) ($payment['status'] ?? 'rejected');
        $order->update(['payment_method' => 'mercadopago']);
        $order->applyPaymentStatus($status, (string) ($payment['id'] ?? ''));

        $pix = data_get($payment, 'point_of_interaction.transaction_data');

        return response()->json([
            'status' => $status,
            'detail' => $payment['status_detail'] ?? null,
            'redirect' => route('orders.show', ['order' => $order, 'pago' => 1]),
            // Dados para exibir o QR do PIX / boleto quando o pagamento fica pendente.
            'qr_code' => $pix['qr_code'] ?? null,
            'qr_code_base64' => $pix['qr_code_base64'] ?? null,
            'ticket_url' => $pix['ticket_url'] ?? data_get($payment, 'transaction_details.external_resource_url'),
        ]);
    }

    /** "Pagar novamente" para um pedido pendente → volta ao Brick. */
    public function retry(Order $order, MercadoPagoService $mp): RedirectResponse
    {
        abort_unless($order->user_id === auth()->id() || auth()->user()->isRoot(), 403);

        if ($order->isPaid()) {
            return redirect()->route('orders.show', $order);
        }

        if (! $mp->enabled()) {
            $order->applyPaymentStatus('approved');

            return redirect()->route('orders.show', $order)->with('placed', true);
        }

        return redirect()->route('payment.show', $order);
    }

    /**
     * Notificação servidor-a-servidor do Mercado Pago. Nunca confiamos no corpo:
     * buscamos o pagamento por id na API e atualizamos o pedido a partir disso.
     */
    public function webhook(Request $request, MercadoPagoService $mp): JsonResponse
    {
        $type = $request->input('type', $request->query('topic'));
        $paymentId = $request->input('data.id', $request->query('id') ?: $request->query('data.id'));

        if (($type === 'payment' || $type === 'merchant_order') && $paymentId) {
            $payment = $mp->getPayment((string) $paymentId);

            if ($payment && ($reference = $payment['external_reference'] ?? null)) {
                Order::where('reference', $reference)->first()
                    ?->applyPaymentStatus((string) $payment['status'], (string) $payment['id']);
            }
        }

        return response()->json(['received' => true]);
    }
}
