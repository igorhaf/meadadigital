<?php

namespace Database\Seeders;

use App\Models\ContactMessage;
use App\Models\Page;
use Illuminate\Database\Seeder;

class PageSeeder extends Seeder
{
    public function run(): void
    {
        $pages = [
            [
                'slug' => 'central-de-ajuda',
                'title' => 'Central de ajuda',
                'body' => <<<TXT
                Bem-vindo à Central de ajuda da Muda! Aqui reunimos as dúvidas mais comuns de quem compra e vende no nosso marketplace de brechó.

                Como faço um pedido?
                Basta adicionar as peças ao carrinho e finalizar a compra. Você acompanha tudo em "Meus pedidos".

                Quais as formas de pagamento?
                Aceitamos cartão de crédito em até 12x sem juros. (No ambiente de demonstração o pagamento é simulado.)

                Prazo de entrega
                O prazo varia conforme a localização do vendedor e o seu endereço. A estimativa aparece na página do produto.

                Não encontrou o que procurava?
                Fale com a gente pela página de Contato — nosso time responde rapidinho.
                TXT,
            ],
            [
                'slug' => 'trocas-e-devolucoes',
                'title' => 'Trocas e devoluções',
                'body' => <<<TXT
                Na Muda você compra com tranquilidade. Seguimos o Código de Defesa do Consumidor e valorizamos a moda circular.

                Direito de arrependimento
                Você tem até 7 dias corridos após o recebimento para desistir da compra, sem precisar justificar.

                Peça com defeito
                Se o produto chegar diferente do anunciado ou com defeito não informado, você pode solicitar troca ou reembolso em até 30 dias.

                Como solicitar
                Acesse "Meus pedidos", selecione o item e clique em solicitar troca/devolução. O frete de devolução em caso de defeito é por nossa conta.

                Reembolso
                Após a análise, o estorno é feito pelo mesmo meio de pagamento em até 10 dias úteis.
                TXT,
            ],
            [
                'slug' => 'privacidade',
                'title' => 'Política de privacidade',
                'body' => <<<TXT
                A sua privacidade é prioridade para a Muda. Esta política explica como tratamos seus dados, em conformidade com a LGPD (Lei nº 13.709/2018).

                Dados que coletamos
                Coletamos apenas o necessário para o funcionamento da loja: nome, e-mail, endereço de entrega e histórico de pedidos.

                Como usamos seus dados
                Utilizamos suas informações para processar pedidos, dar suporte e melhorar sua experiência. Não vendemos seus dados a terceiros.

                Seus direitos
                Você pode solicitar acesso, correção ou exclusão dos seus dados a qualquer momento pela página de Contato.

                Segurança
                Adotamos medidas técnicas e organizacionais para proteger suas informações contra acessos não autorizados.
                TXT,
            ],
        ];

        foreach ($pages as $page) {
            Page::updateOrCreate(['slug' => $page['slug']], $page);
        }

        // A couple of sample messages so the admin inbox isn't empty.
        if (ContactMessage::count() === 0) {
            ContactMessage::insert([
                ['name' => 'Ana Beatriz', 'email' => 'ana@example.com', 'subject' => 'Dúvida sobre frete', 'message' => 'Oi! Vocês entregam em todo o Brasil?', 'is_read' => false, 'created_at' => now()->subDays(2), 'updated_at' => now()->subDays(2)],
                ['name' => 'Marcos Silva', 'email' => 'marcos@example.com', 'subject' => 'Parceria', 'message' => 'Tenho um brechó e gostaria de vender na Muda.', 'is_read' => true, 'created_at' => now()->subDay(), 'updated_at' => now()->subDay()],
            ]);
        }
    }
}
