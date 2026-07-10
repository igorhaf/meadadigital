package com.meada.profiles.oficina;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.meada.profiles.oficina.mechanics.OsMechanic;
import com.meada.profiles.oficina.mechanics.OsMechanicRepository;
import com.meada.profiles.oficina.orders.ServiceOrder;
import com.meada.profiles.oficina.orders.ServiceOrderRepository;
import com.meada.profiles.oficina.vehicles.OsVehicle;
import com.meada.profiles.oficina.vehicles.OsVehicleRepository;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

/**
 * Cache do bloco de contexto dinâmico injetado no prompt do OficinaBot (camada 7.9). TTL 20s
 * (espelho salon/pet). Keyed por {@code (companyId, contactId)}. Conteúdo:
 * <ul>
 *   <li>mecânicos ativos (id + nome) — pra IA referenciar mechanic_id ao abrir OS;
 *   <li>veículos do cliente (id + placa/modelo) — modo vehicle_id da tag de abertura;
 *   <li>OS abertas/orçadas do cliente (id + status + total) — pra IA capturar a APROVAÇÃO
 *       referenciando a OS certa (gate de 2 fases).
 * </ul>
 * + instruções e as 2 tags ({@code <ordem_servico>} 2 modos e {@code <aprovacao_os>}).
 */
@Component
public class OficinaContextCache {

    private static final int MAX_VEHICLES = 10;

    private final OsMechanicRepository mechanicRepository;
    private final OsVehicleRepository vehicleRepository;
    private final ServiceOrderRepository orderRepository;
    private final com.meada.profiles.oficina.catalog.OficinaCatalogRepository catalogRepository;
    private final Cache<String, String> cache;

    public OficinaContextCache(OsMechanicRepository mechanicRepository,
                               OsVehicleRepository vehicleRepository,
                               ServiceOrderRepository orderRepository,
                               com.meada.profiles.oficina.catalog.OficinaCatalogRepository catalogRepository) {
        this.mechanicRepository = mechanicRepository;
        this.vehicleRepository = vehicleRepository;
        this.orderRepository = orderRepository;
        this.catalogRepository = catalogRepository;
        this.cache = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofSeconds(20))
            .maximumSize(1000)
            .build();
    }

    public String contextSegment(UUID companyId, UUID contactId) {
        String key = companyId + ":" + (contactId == null ? "none" : contactId.toString());
        return cache.get(key, k -> buildSegment(companyId, contactId));
    }

    /** Invalida todas as entradas de uma empresa (mutação de mecânico/veículo/OS/item/config). */
    public void invalidate(UUID companyId) {
        String prefix = companyId + ":";
        cache.asMap().keySet().removeIf(k -> k.startsWith(prefix));
    }

    private static String brl(int cents) {
        return "R$ " + String.format("%d,%02d", cents / 100, cents % 100);
    }

    private String buildSegment(UUID companyId, UUID contactId) {
        StringBuilder sb = new StringBuilder();

        // --- MECÂNICOS ---
        List<OsMechanic> mechs = mechanicRepository.listByCompany(companyId, true);
        if (mechs.isEmpty()) {
            sb.append("MECÂNICOS: (nenhum ativo no momento.)\n\n");
        } else {
            sb.append("MECÂNICOS (use o mechanic_id EXATO; atribuição é OPCIONAL):\n");
            for (OsMechanic m : mechs) {
                sb.append("- ").append(m.id()).append(" · ").append(m.name());
                if (m.specialty() != null && !m.specialty().isBlank()) {
                    sb.append(" (").append(m.specialty()).append(")");
                }
                sb.append("\n");
            }
            sb.append("\n");
        }

        // --- SERVIÇOS TABELADOS (onda 1, backlog #1 — a IA pode pré-preencher a OS) ---
        java.util.List<com.meada.profiles.oficina.catalog.OficinaCatalogItem> tabelados =
            catalogRepository.listByCompany(companyId, true);
        if (!tabelados.isEmpty()) {
            sb.append("SERVIÇOS/PEÇAS TABELADOS (use o id EXATO no campo servicos da tag; preço do catálogo):\n");
            for (var t : tabelados) {
                sb.append("- ").append(t.id()).append(" · ").append(t.name());
                if (t.category() != null && !t.category().isBlank()) {
                    sb.append(" [").append(t.category()).append("]");
                }
                sb.append(" · R$ ").append(String.format("%d,%02d", t.unitPriceCents() / 100, t.unitPriceCents() % 100))
                    .append("\n");
            }
            sb.append("\n");
        }

        // --- VEÍCULOS DO CLIENTE ---
        if (contactId != null) {
            List<OsVehicle> vehicles = vehicleRepository.listByContact(companyId, contactId, true);
            if (!vehicles.isEmpty()) {
                sb.append("VEÍCULOS DO CLIENTE (use o vehicle_id EXATO; ofereça os já cadastrados):\n");
                int count = 0;
                for (OsVehicle v : vehicles) {
                    if (count++ >= MAX_VEHICLES) break;
                    sb.append("- ").append(v.id()).append(" · ").append(v.plate());
                    if (v.brand() != null || v.model() != null) {
                        sb.append(" (").append(v.brand() == null ? "" : v.brand());
                        if (v.model() != null) sb.append(v.brand() == null ? "" : " ").append(v.model());
                        if (v.year() != null) sb.append(" ").append(v.year());
                        sb.append(")");
                    }
                    sb.append("\n");
                }
                sb.append("\n");
            } else {
                sb.append("CLIENTE SEM VEÍCULOS cadastrados: peça placa, marca, modelo e ano para "
                    + "cadastrar o primeiro veículo junto com a abertura da OS.\n\n");
            }

            // --- OS ABERTAS/ORÇADAS DO CLIENTE (pra capturar aprovação) ---
            List<ServiceOrder> openOrders = orderRepository.listByCompany(companyId, null, null, null,
                contactId, null, null, 20, 0);
            StringBuilder osBlock = new StringBuilder();
            for (ServiceOrder o : openOrders) {
                if ("orcada".equals(o.status())) {
                    osBlock.append("- ").append(o.id()).append(" · veículo ").append(o.vehiclePlate())
                        .append(" · ORÇADA · total ").append(brl(o.totalCents()))
                        .append(" (aguardando aprovação do cliente)\n");
                } else if ("aberta".equals(o.status())) {
                    osBlock.append("- ").append(o.id()).append(" · veículo ").append(o.vehiclePlate())
                        .append(" · ABERTA (ainda sem orçamento)\n");
                }
            }
            if (osBlock.length() > 0) {
                sb.append("ORDENS DE SERVIÇO DO CLIENTE EM ABERTO:\n").append(osBlock)
                    .append("Quando o cliente responder se aprova/recusa um ORÇAMENTO, use a tag "
                        + "<aprovacao_os> com o service_order_id da OS ORÇADA correspondente.\n\n");
            }
        } else {
            sb.append("CLIENTE NÃO IDENTIFICADO pelo telefone. Peça os dados para cadastrar o veículo.\n\n");
        }

        // --- INSTRUÇÕES + TAGS ---
        sb.append("INSTRUÇÕES:\n")
            .append("Você ABRE a ordem de serviço a partir da queixa do cliente (não diagnostica nem "
                + "orça — quem orça é o mecânico no balcão). Se o cliente tem um veículo cadastrado, "
                + "ofereça-o; se é a primeira vez, peça placa + marca + modelo + ano. NUNCA invente "
                + "defeito, preço de peça ou prazo de entrega. Para qualquer dúvida técnica, oriente a "
                + "avaliação presencial.\n")
            .append("Para ABRIR uma OS, sua ÚLTIMA mensagem deve TERMINAR com UMA das tags (linha "
                + "própria, sem markdown):\n")
            .append("Cliente COM veículo cadastrado:\n")
            .append("<ordem_servico>{\"vehicle_id\":\"UUID\",\"mechanic_id\":\"UUID|null\","
                + "\"complaint\":\"...\",\"notes\":\"...\","
                + "\"servicos\":[{\"id\":\"UUID_DO_TABELADO\",\"qtd\":N}]}</ordem_servico>\n")
            .append("O campo servicos é OPCIONAL: use APENAS quando o cliente pedir um serviço "
                + "TABELADO da lista acima (o preço vem do catálogo do próprio tenant). Quando a "
                + "queixa exigir diagnóstico, NÃO inclua servicos — a OS abre sem itens e o "
                + "mecânico orça.\n")
            .append("Cliente SEM veículo (cadastra junto):\n")
            .append("<ordem_servico>{\"new_vehicle\":{\"plate\":\"...\",\"brand\":\"...\",\"model\":\"...\","
                + "\"year\":2018},\"complaint\":\"...\",\"notes\":\"...\"}</ordem_servico>\n")
            .append("Para CAPTURAR a resposta do cliente a um ORÇAMENTO (OS já orçada), termine com:\n")
            .append("<aprovacao_os>{\"service_order_id\":\"UUID\",\"decisao\":\"aprovada|recusada\"}</aprovacao_os>\n")
            .append("Use ids EXATOS. Só emita a tag de aprovação se houver uma OS ORÇADA do cliente.\n\n");

        return sb.toString();
    }
}
