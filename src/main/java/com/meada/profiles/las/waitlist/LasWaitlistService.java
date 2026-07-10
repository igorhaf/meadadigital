package com.meada.profiles.las.waitlist;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * Lista de espera de dye lot (onda Lãs 1, backlog #1). A escapada do nicho: o interesse pode ser
 * no LOTE exato ou em QUALQUER lote da cor ({@code dye_lot} null). A reposição no painel (0 → N)
 * notifica a fila da variante exata E a fila "qualquer lote" da mesma cor (best-effort,
 * idempotente por notified_at — marca mesmo sem canal, não revarre eternamente).
 */
@Service
public class LasWaitlistService {

    private static final Logger log = LoggerFactory.getLogger(LasWaitlistService.class);

    private final JdbcTemplate jdbcTemplate;
    private final com.meada.profiles.las.orders.LasOrderNotifier notifier;

    public LasWaitlistService(JdbcTemplate jdbcTemplate,
                              com.meada.profiles.las.orders.LasOrderNotifier notifier) {
        this.jdbcTemplate = jdbcTemplate;
        this.notifier = notifier;
    }

    /**
     * Registra o interesse a partir da VARIANTE (a IA conhece o UUID do catálogo). Quando
     * {@code anyLot}, o registro vale pra QUALQUER lote da cor (dye_lot null). Duplicata pendente
     * é no-op (unique parcial).
     */
    public boolean register(UUID companyId, UUID contactId, UUID variantId, boolean anyLot,
                            Integer qtyDesired) {
        record Var(UUID productId, String color, String dyeLot) {}
        Var v = jdbcTemplate.query(
                "select v.product_id, v.color, v.dye_lot from las_variants v "
                    + "join las_products p on p.id = v.product_id "
                    + "where v.id = ? and p.company_id = ?",
                (rs, rn) -> new Var((UUID) rs.getObject("product_id"), rs.getString("color"),
                    rs.getString("dye_lot")),
                variantId, companyId)
            .stream().findFirst().orElse(null);
        if (v == null) {
            return false;
        }
        int n = jdbcTemplate.update(
            "insert into las_waitlist (company_id, contact_id, product_id, color, dye_lot, qty_desired) "
                + "values (?, ?, ?, ?, ?, ?) on conflict do nothing",
            companyId, contactId, v.productId(), v.color(), anyLot ? null : v.dyeLot(), qtyDesired);
        return n > 0;
    }

    /**
     * Dispara "chegou!" pra fila pendente da variante reposta: match pelo LOTE exato OU pela cor
     * com dye_lot null (qualquer lote serve). Chamado pelo painel quando o estoque sobe de 0 pra N.
     */
    public int notifyBackInStock(UUID companyId, UUID variantId) {
        record Pending(UUID waitId, UUID conversationId, String contactName, String productName,
                       String color, String dyeLot, Integer qtyDesired) {}
        List<Pending> pendings = jdbcTemplate.query(
            "select w.id as wait_id, "
                + "(select conv.id from conversations conv where conv.company_id = w.company_id "
                + "  and conv.contact_id = w.contact_id order by conv.created_at desc limit 1) as conversation_id, "
                + "ct.name as contact_name, p.name as product_name, v.color, v.dye_lot, w.qty_desired "
                + "from las_variants v "
                + "join las_products p on p.id = v.product_id "
                + "join las_waitlist w on w.company_id = ? and w.product_id = v.product_id "
                + "  and w.color = v.color "
                + "  and (w.dye_lot is null or w.dye_lot = v.dye_lot) "
                + "  and w.notified_at is null "
                + "join contacts ct on ct.id = w.contact_id "
                + "where v.id = ? and p.company_id = ?",
            (rs, rn) -> new Pending((UUID) rs.getObject("wait_id"), (UUID) rs.getObject("conversation_id"),
                rs.getString("contact_name"), rs.getString("product_name"),
                rs.getString("color"), rs.getString("dye_lot"),
                rs.getObject("qty_desired") == null ? null : rs.getInt("qty_desired")),
            companyId, variantId, companyId);
        int sent = 0;
        for (Pending p : pendings) {
            try {
                if (p.conversationId() != null) {
                    StringBuilder sb = new StringBuilder("Oi, ").append(p.contactName())
                        .append("! Boa notícia: chegou ").append(p.productName())
                        .append(" na cor ").append(p.color());
                    if (p.dyeLot() != null) {
                        sb.append(" (lote ").append(p.dyeLot()).append(")");
                    }
                    sb.append(" que você esperava");
                    if (p.qtyDesired() != null) {
                        sb.append(" — você queria ").append(p.qtyDesired()).append(" novelos");
                    }
                    sb.append(". Quer que eu já monte seu pedido? 🧶");
                    notifier.notifyStatus(companyId, p.conversationId(), sb.toString());
                }
                jdbcTemplate.update("update las_waitlist set notified_at = now() where id = ?", p.waitId());
                sent++;
            } catch (Exception e) {
                log.warn("las-waitlist: failed {} ({})", p.waitId(), e.getMessage());
            }
        }
        return sent;
    }
}
