package com.meada.profiles.lingerie.alerts;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * Avise-me quando voltar (onda Moda Infantil 1): registra o interesse do contato numa variante
 * ESGOTADA e, quando o tenant repõe o estoque no painel (0 → N), dispara "voltou!" pra fila
 * pendente (best-effort, idempotente por notified_at). Demanda reprimida também é insight de
 * reposição pro tenant.
 */
@Service
public class LingerieStockAlertService {

    private static final Logger log = LoggerFactory.getLogger(LingerieStockAlertService.class);

    private final JdbcTemplate jdbcTemplate;
    private final com.meada.profiles.lingerie.orders.LingerieOrderNotifier notifier;

    public LingerieStockAlertService(JdbcTemplate jdbcTemplate,
                                         com.meada.profiles.lingerie.orders.LingerieOrderNotifier notifier) {
        this.jdbcTemplate = jdbcTemplate;
        this.notifier = notifier;
    }

    /** Registra o interesse (1 alerta pendente por contato+variante — duplicata é no-op). */
    public boolean register(UUID companyId, UUID contactId, UUID variantId) {
        Integer ok = jdbcTemplate.query(
                "select 1 from lingerie_variants v join lingerie_products p on p.id = v.product_id "
                    + "where v.id = ? and p.company_id = ?",
                (rs, rn) -> 1, variantId, companyId)
            .stream().findFirst().orElse(null);
        if (ok == null) {
            return false;
        }
        int n = jdbcTemplate.update(
            "insert into lingerie_stock_alerts (company_id, contact_id, variant_id) "
                + "values (?, ?, ?) on conflict do nothing",
            companyId, contactId, variantId);
        return n > 0;
    }

    /**
     * Dispara "voltou ao estoque" pra fila pendente da variante (chamado pelo painel quando o
     * estoque sobe de 0 pra N). Best-effort: falha de envio não desfaz a reposição; marca
     * notified_at mesmo sem canal (não revarre eternamente).
     */
    public int notifyBackInStock(UUID companyId, UUID variantId) {
        record Pending(UUID alertId, UUID conversationId, String contactName,
                       String productName, String size, String color) {}
        List<Pending> pendings = jdbcTemplate.query(
            "select a.id as alert_id, "
                + "(select conv.id from conversations conv where conv.company_id = a.company_id "
                + "  and conv.contact_id = a.contact_id order by conv.created_at desc limit 1) as conversation_id, "
                + "ct.name as contact_name, p.name as product_name, v.size, v.color "
                + "from lingerie_stock_alerts a "
                + "join contacts ct on ct.id = a.contact_id "
                + "join lingerie_variants v on v.id = a.variant_id "
                + "join lingerie_products p on p.id = v.product_id "
                + "where a.company_id = ? and a.variant_id = ? and a.notified_at is null",
            (rs, rn) -> new Pending((UUID) rs.getObject("alert_id"), (UUID) rs.getObject("conversation_id"),
                rs.getString("contact_name"), rs.getString("product_name"),
                rs.getString("size"), rs.getString("color")),
            companyId, variantId);
        int sent = 0;
        for (Pending p : pendings) {
            try {
                if (p.conversationId() != null) {
                    notifier.notifyStatus(companyId, p.conversationId(),
                        "Oi, " + p.contactName() + "! Boa notícia: o " + p.productName()
                            + " (" + p.size() + (p.color() != null ? ", " + p.color() : "") + ") "
                            + "que você queria VOLTOU ao estoque. Quer que eu já monte seu pedido? ✨");
                }
                jdbcTemplate.update(
                    "update lingerie_stock_alerts set notified_at = now() where id = ?", p.alertId());
                sent++;
            } catch (Exception e) {
                log.warn("lingerie-alert: failed alert {} ({})", p.alertId(), e.getMessage());
            }
        }
        return sent;
    }
}
