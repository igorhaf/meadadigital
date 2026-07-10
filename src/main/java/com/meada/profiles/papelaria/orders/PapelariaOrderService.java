package com.meada.profiles.papelaria.orders;

import com.meada.profiles.papelaria.PapelariaConfig;
import com.meada.profiles.papelaria.PapelariaConfigRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Regras dos pedidos papelaria (camada 8.15 / perfil papelaria). Clone de
 * {@link com.meada.profiles.padaria.orders.PadariaOrderService} (camada 8.8) + as escapadas
 * (lead time da data condicional + fulfillment retirada/entrega) + a ESCAPADA PROVA DE ARTE (gate
 * {@code art_not_approved}). As exceções de validação
 * ({@link PapelariaOrderRepository.LeadTimeViolationException},
 * {@link PapelariaOrderRepository.AddressRequiredException},
 * {@link PapelariaOrderRepository.InvalidOptionException}) sobem do repositório.
 *
 * <p>{@link #create} é chamado pelo {@code PedidoPapelariaConfirmHandler} (vindo da IA). Lê a config
 * do tenant (taxa + lead default) e delega ao repositório — que recalcula os totais a partir do
 * catálogo + opções (IGNORA o total que a IA mandou, line = unit × tiragem) e aplica as travas.
 *
 * <p>{@link #updateStatus} valida a transição (→ 409 se inválida) e, na transição
 * arte_aprovacao→em_producao, EXIGE {@code art_approved=true} (senão {@link ArtNotApprovedException} →
 * 409 art_not_approved). Ao persistir, dispara a notificação outbound do novo status via
 * {@link PapelariaOrderNotifier}. O aceite/recusa e a subida de arte são AÇÃO HUMANA (a IA não
 * transiciona nem sobe arte).
 */
@Service
public class PapelariaOrderService {

    private final PapelariaOrderRepository orderRepository;
    private final PapelariaConfigRepository configRepository;
    private final PapelariaOrderNotifier notifier;

    public PapelariaOrderService(PapelariaOrderRepository orderRepository,
                                 PapelariaConfigRepository configRepository,
                                 PapelariaOrderNotifier notifier) {
        this.orderRepository = orderRepository;
        this.configRepository = configRepository;
        this.notifier = notifier;
    }

    /** Pedido não encontrado / de outro tenant (→ 404). */
    public static class OrderNotFoundException extends RuntimeException {}

    /** Transição de status inválida (→ 409 invalid_status_transition). */
    public static class InvalidStatusTransitionException extends RuntimeException {}

    /** Status alvo desconhecido (→ 400 invalid_status). */
    public static class InvalidStatusException extends RuntimeException {}

    /**
     * ESCAPADA — tentativa de mover arte_aprovacao→em_producao sem a arte aprovada
     * ({@code art_approved=false}) → 409 art_not_approved. O cliente precisa aprovar a arte primeiro
     * (PATCH /art ou tag {@code <aprovacao_arte>}).
     */
    public static class ArtNotApprovedException extends RuntimeException {}
    public static class DepositRequiredException extends RuntimeException {}
    public static class InvalidDepositException extends RuntimeException {}

    /**
     * Cria um pedido a partir das linhas confirmadas pela IA. A taxa de entrega + o lead default vêm
     * da config (0/5 se ausente). O repositório faz o snapshot de preço+nome+made_to_order+opções,
     * recalcula os totais (descarta o total da IA; line = unit × tiragem), valida o lead time e o
     * endereço conforme o fulfillment. Propaga {@code LeadTimeViolationException}/
     * {@code AddressRequiredException}/{@code InvalidOptionException} para o handler decidir (que
     * aborta sem criar — devolve empty).
     */
    @Transactional
    public PapelariaOrder create(UUID companyId, UUID conversationId, UUID contactId,
                                 String fulfillment, String deliveryAddress, List<OrderLineInput> lines,
                                 LocalDate pickupOrDeliveryDate, String deliveryPeriod, String notes) {
        PapelariaConfig config = configRepository.findByCompany(companyId);
        return orderRepository.createOrder(
            companyId, conversationId, contactId, fulfillment, deliveryAddress, lines,
            config.deliveryFeeCents(), config.leadTimeDaysDefault(),
            pickupOrDeliveryDate, deliveryPeriod, notes);
    }

    public List<PapelariaOrder> list(UUID companyId, String status, int limit, int offset) {
        return orderRepository.listByCompany(companyId, status, limit, offset);
    }

    public long count(UUID companyId, String status) {
        return orderRepository.countByCompany(companyId, status);
    }

    public Optional<PapelariaOrder> get(UUID companyId, UUID id) {
        return orderRepository.findById(companyId, id);
    }

    /**
     * Pedido em 'arte_aprovacao' da conversa do contato (resolução do {@code <aprovacao_arte>} sem
     * order_id explícito). Empty se nenhum pedido daquela conversa aguarda aprovação de arte.
     */
    public Optional<PapelariaOrder> getArteAprovacaoByConversation(UUID companyId, UUID conversationId) {
        return orderRepository.findArteAprovacaoByConversation(companyId, conversationId);
    }

    /**
     * Transiciona o status do pedido (gate humano). Valida o alvo (enum) e a transição. Na transição
     * arte_aprovacao→em_producao, EXIGE {@code art_approved=true} (senão {@link ArtNotApprovedException}).
     * Persiste — gravando rejection_reason SÓ quando o alvo é {@code recusado} — e notifica o cliente
     * com o texto fixo do novo status (concatenando o motivo na recusa, defensivamente). A notificação
     * é best-effort (não reverte).
     */
    @Transactional
    public PapelariaOrder updateStatus(UUID companyId, UUID id, String newStatusId, String rejectionReason) {
        PapelariaOrderStatus newStatus = PapelariaOrderStatus.fromId(newStatusId)
            .orElseThrow(InvalidStatusException::new);

        PapelariaOrder current = orderRepository.findById(companyId, id)
            .orElseThrow(OrderNotFoundException::new);
        PapelariaOrderStatus from = PapelariaOrderStatus.fromId(current.status())
            .orElseThrow(InvalidStatusException::new);

        if (!from.canTransitionTo(newStatus)) {
            throw new InvalidStatusTransitionException();
        }

        // ESCAPADA: arte_aprovacao→em_producao SÓ com a arte aprovada.
        if (from == PapelariaOrderStatus.ARTE_APROVACAO && newStatus == PapelariaOrderStatus.EM_PRODUCAO
                && !current.artApproved()) {
            throw new ArtNotApprovedException();
        }
        // Onda #1: com sinal REGISTRADO e não pago, a produção não começa (409 deposit_required).
        if (from == PapelariaOrderStatus.ARTE_APROVACAO && newStatus == PapelariaOrderStatus.EM_PRODUCAO
                && current.depositCents() != null && current.depositCents() > 0 && !current.depositPaid()) {
            throw new DepositRequiredException();
        }

        // rejection_reason só faz sentido na recusa; nas demais transições passa null.
        String reasonToPersist = newStatus == PapelariaOrderStatus.RECUSADO ? rejectionReason : null;
        orderRepository.updateStatus(companyId, id, newStatus.id(), reasonToPersist);

        // Notificação outbound do novo status (best-effort).
        String text = newStatus.notificationText();
        if (newStatus == PapelariaOrderStatus.RECUSADO && rejectionReason != null && !rejectionReason.isBlank()) {
            text = text + " Motivo: " + rejectionReason.strip();
        }
        if (text != null) {
            notifier.notifyStatus(companyId, current.conversationId(), text);
        }

        return orderRepository.findById(companyId, id).orElseThrow(OrderNotFoundException::new);
    }

    /**
     * ESCAPADA — a equipe sobe a arte (link) e move o pedido para 'arte_aprovacao' (ação HUMANA do
     * painel). SÓ vale a partir de 'aceito' (aceito→arte_aprovacao). Grava {@code art_url} + status e
     * notifica o cliente ("sua arte está pronta, dê uma olhada e aprove"). artUrl em branco → 400-ish
     * IllegalArgumentException (o controller mapeia).
     */
    @Transactional
    public PapelariaOrder setArtUrl(UUID companyId, UUID id, String artUrl) {
        if (artUrl == null || artUrl.isBlank()) {
            throw new IllegalArgumentException("art_url obrigatório");
        }
        PapelariaOrder current = orderRepository.findById(companyId, id)
            .orElseThrow(OrderNotFoundException::new);
        PapelariaOrderStatus from = PapelariaOrderStatus.fromId(current.status())
            .orElseThrow(InvalidStatusException::new);
        if (!from.canTransitionTo(PapelariaOrderStatus.ARTE_APROVACAO)) {
            throw new InvalidStatusTransitionException();
        }
        orderRepository.setArtUrl(companyId, id, artUrl.strip());

        String text = PapelariaOrderStatus.ARTE_APROVACAO.notificationText();
        if (text != null) {
            notifier.notifyStatus(companyId, current.conversationId(), text);
        }
        return orderRepository.findById(companyId, id).orElseThrow(OrderNotFoundException::new);
    }

    /**
     * ESCAPADA — aprova a arte: seta {@code art_approved=true} e move arte_aprovacao→em_producao na
     * MESMA operação. Usado pelo PATCH /art do painel E pelo {@code AprovacaoArteHandler} (tag da IA).
     * SÓ vale quando o pedido está em 'arte_aprovacao' (senão {@link InvalidStatusTransitionException}).
     * Notifica o cliente do em_producao ("arte aprovada, vamos imprimir").
     */
    @Transactional
    public PapelariaOrder approveArt(UUID companyId, UUID id) {
        PapelariaOrder current = orderRepository.findById(companyId, id)
            .orElseThrow(OrderNotFoundException::new);
        PapelariaOrderStatus from = PapelariaOrderStatus.fromId(current.status())
            .orElseThrow(InvalidStatusException::new);
        if (from != PapelariaOrderStatus.ARTE_APROVACAO) {
            throw new InvalidStatusTransitionException();
        }
        orderRepository.setArtApproved(companyId, id, true);

        // Onda #1: sinal registrado e NÃO pago → a arte fica aprovada mas o pedido AGUARDA o sinal
        // em arte_aprovacao (marcar o sinal como pago move automaticamente — ver setDeposit).
        if (current.depositCents() != null && current.depositCents() > 0 && !current.depositPaid()) {
            notifier.notifyStatus(companyId, current.conversationId(),
                "Arte aprovada! 🎉 Para iniciarmos a produção, falta a confirmação do sinal de R$ "
                    + brl(current.depositCents()) + ". Assim que recebermos, sua encomenda entra na fila.");
            return orderRepository.findById(companyId, id).orElseThrow(OrderNotFoundException::new);
        }

        orderRepository.updateStatus(companyId, id, PapelariaOrderStatus.EM_PRODUCAO.id(), null);

        String text = PapelariaOrderStatus.EM_PRODUCAO.notificationText();
        if (text != null) {
            notifier.notifyStatus(companyId, current.conversationId(), text);
        }
        return orderRepository.findById(companyId, id).orElseThrow(OrderNotFoundException::new);
    }

    /**
     * Registra o sinal e/ou marca como recebido (onda #1 — manual até o gateway #50). Marcar pago
     * exige valor &gt; 0 (400 invalid_deposit). Se a ARTE JÁ estava aprovada e o pedido aguardava o
     * sinal em arte_aprovacao, o pagamento MOVE automaticamente pra em_producao (fecha o loop).
     */
    @Transactional
    public PapelariaOrder setDeposit(UUID companyId, UUID id, Integer depositCents, boolean depositPaid) {
        if (depositCents != null && depositCents < 0) {
            throw new InvalidDepositException();
        }
        if (depositPaid && (depositCents == null || depositCents <= 0)) {
            throw new InvalidDepositException();
        }
        PapelariaOrder updated = orderRepository.updateDeposit(companyId, id, depositCents, depositPaid)
            .orElseThrow(OrderNotFoundException::new);
        if (depositPaid && updated.artApproved()
                && PapelariaOrderStatus.ARTE_APROVACAO.id().equals(updated.status())) {
            orderRepository.updateStatus(companyId, id, PapelariaOrderStatus.EM_PRODUCAO.id(), null);
            String text = PapelariaOrderStatus.EM_PRODUCAO.notificationText();
            if (text != null) {
                notifier.notifyStatus(companyId, updated.conversationId(), text);
            }
            return orderRepository.findById(companyId, id).orElseThrow(OrderNotFoundException::new);
        }
        return updated;
    }

    private static String brl(int cents) {
        return String.format("%d,%02d", cents / 100, cents % 100);
    }
}
