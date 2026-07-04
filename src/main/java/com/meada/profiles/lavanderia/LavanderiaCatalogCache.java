package com.meada.profiles.lavanderia;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.meada.profiles.lavanderia.config.LavanderiaConfig;
import com.meada.profiles.lavanderia.config.LavanderiaConfigRepository;
import com.meada.profiles.lavanderia.services.LavanderiaService;
import com.meada.profiles.lavanderia.services.LavanderiaServiceOption;
import com.meada.profiles.lavanderia.services.LavanderiaServiceRepository;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Cache do bloco de catálogo+config injetado no prompt do LavanderiaBot (camada 8.10). Clone do
 * {@link com.meada.profiles.floricultura.FloriculturaCatalogCache} (Caffeine TTL 60s) —
 * {@link com.meada.profiles.lavanderia.services.LavanderiaServiceCatalogService} e
 * {@link com.meada.profiles.lavanderia.config.LavanderiaConfigService} chamam {@link
 * #invalidate} ao mutar serviço/opção/config, então a IA vê a mudança na hora.
 *
 * <p>ESCAPADA: cada serviço marca o {@code turnaround_days} (prazo de processamento) — a IA precisa
 * dele para calcular a entrega (collect + MAX(turnaround dos itens)). E a tag carrega DUAS datas
 * (collect_date + delivery_date opcional) + período + endereço + itens com qty.
 */
@Component
public class LavanderiaCatalogCache {

    private final LavanderiaServiceRepository serviceRepository;
    private final LavanderiaConfigRepository configRepository;
    private final Cache<UUID, String> cache;

    public LavanderiaCatalogCache(LavanderiaServiceRepository serviceRepository,
                                  LavanderiaConfigRepository configRepository) {
        this.serviceRepository = serviceRepository;
        this.configRepository = configRepository;
        this.cache = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofSeconds(60))
            .maximumSize(500)
            .build();
    }

    /** Bloco de catálogo+config+instruções para o prompt, cacheado por company (TTL 60s). */
    public String catalogSegment(UUID companyId) {
        return cache.get(companyId, this::buildSegment);
    }

    /** Alias de {@link #catalogSegment} — paridade com o naming do ProfilePromptContext. */
    public String menuSegment(UUID companyId) {
        return catalogSegment(companyId);
    }

    /** Invalida o cache de uma empresa (chamado ao mutar serviço/opção/config). */
    public void invalidate(UUID companyId) {
        cache.invalidate(companyId);
    }

    private String buildSegment(UUID companyId) {
        List<LavanderiaService> items = serviceRepository.listByCompany(companyId, null, true);
        LavanderiaConfig config = configRepository.findByCompany(companyId);

        StringBuilder sb = new StringBuilder();
        if (items.isEmpty()) {
            sb.append("SERVIÇOS DISPONÍVEIS HOJE: (nenhum serviço disponível no momento — informe o "
                + "cliente que o catálogo está indisponível e ofereça avisá-lo quando voltar.)\n\n");
        } else {
            sb.append("SERVIÇOS DISPONÍVEIS HOJE (preço POR PEÇA · prazo em dias):\n");
            String currentCategory = null;
            for (LavanderiaService it : items) {
                if (!it.category().equals(currentCategory)) {
                    currentCategory = it.category();
                    sb.append("[").append(LavanderiaServiceCategory.fromId(currentCategory)
                        .map(LavanderiaServiceCategory::label).orElse(currentCategory)).append("]\n");
                }
                sb.append("- ").append(it.id()).append(" · ").append(it.name())
                    .append(" · R$ ").append(formatBrl(it.priceCents()))
                    .append(" · prazo ").append(it.turnaroundDays()).append(" dia(s)");
                if (it.description() != null && !it.description().isBlank()) {
                    sb.append(" · ").append(it.description().strip());
                }
                if (it.careInstructions() != null && !it.careInstructions().isBlank()) {
                    sb.append(" · cuidado: ").append(it.careInstructions().strip());
                }
                sb.append("\n");
                appendOptions(sb, it.options());
            }
            sb.append("\n");
        }

        sb.append("INSTRUÇÕES DE PEDIDO:\n")
            .append("Lavanderia faz COLETA e ENTREGA agendadas. ANTES de fechar, você PRECISA ter: os "
                + "serviços (com a quantidade de PEÇAS de cada), o ENDEREÇO de coleta/entrega, a DATA de "
                + "COLETA (formato YYYY-MM-DD, hoje ou futura) e o PERÍODO da coleta ('manha' ou 'tarde'). "
                + "A DATA DE ENTREGA é OPCIONAL: se o cliente não pedir uma data específica, OMITA o campo "
                + "(o sistema calcula = coleta + maior prazo entre os serviços). Se o cliente pedir uma "
                + "data de entrega, ela NÃO pode ser antes de coleta + o maior prazo dos serviços. Quando "
                + "o cliente CONFIRMAR E você tiver TODOS esses dados, sua ÚLTIMA mensagem deve TERMINAR "
                + "com a tag (em uma linha própria, sem markdown):\n")
            .append("<pedido_lavanderia>{\"collect_date\":\"YYYY-MM-DD\",\"period\":\"manha\","
                + "\"delivery_address\":\"...\",\"delivery_date\":null,"
                + "\"items\":[{\"service_id\":\"UUID_EXATO_DO_CATÁLOGO\",\"options\":[\"UUID_DA_OPCAO\"],"
                + "\"qty\":N}],\"notes\":\"observações ou vazio\"}</pedido_lavanderia>\n")
            .append("Cada item pode ter \"options\" (UUIDs das opções de acabamento/cuidado escolhidas); "
                + "item sem opção → omita \"options\". \"qty\" é a quantidade de peças daquele serviço. Use "
                + "os service_id e option_id EXATOS do catálogo acima. \"delivery_date\" pode ser null (o "
                + "sistema calcula). ANTES da tag, escreva a confirmação humana normal. NÃO emita a tag sem "
                + "TODOS os dados (serviços+endereço+collect_date+período). A data de coleta NÃO pode ser "
                + "no passado.\n");

        if (config.deliveryFeeCents() > 0) {
            sb.append("Taxa de entrega: R$ ").append(formatBrl(config.deliveryFeeCents()))
                .append(" (some ao total).\n");
        }
        if (config.minOrderCents() > 0) {
            sb.append("Pedido mínimo: R$ ").append(formatBrl(config.minOrderCents()))
                .append(" (avise o cliente se o pedido ficar abaixo).\n");
        }
        // Onda 1 (backlog #2): serviço EXPRESS — a IA informa a sobretaxa DA CONFIG, nunca inventa.
        if (config.expressEnabled()) {
            sb.append("SERVIÇO EXPRESS: quando o cliente tiver PRESSA, ofereça o express — entrega em ")
                .append(config.expressTurnaroundDays())
                .append(" dia(s) com sobretaxa de ").append(config.expressSurchargePct())
                .append("% sobre o subtotal. Se o cliente aceitar, CONFIRME a sobretaxa e acrescente "
                    + "\"express\":true na tag (quem calcula o valor é o sistema). Sem aceite explícito, "
                    + "NÃO marque express.\n");
        }
        // Onda 1 (backlog #6): cupom — validação e recálculo são do sistema.
        sb.append("Se o cliente informar um CUPOM de desconto, registre o código no campo \"cupom\" "
            + "da tag (omita se não houver) — quem VALIDA o cupom, calcula a fidelidade e recalcula o "
            + "total é o sistema; NUNCA invente desconto nem prometa que o cupom vale antes da "
            + "validação.\n");
        sb.append("Prazo padrão (turnaround): ").append(config.turnaroundDaysDefault()).append(" dia(s).\n");
        sb.append("CONFIG: delivery_fee_cents=").append(config.deliveryFeeCents())
            .append(", min_order_cents=").append(config.minOrderCents())
            .append(", turnaround_days_default=").append(config.turnaroundDaysDefault()).append("\n");
        sb.append("Avise o cliente que o pedido será enviado para confirmação da lavanderia.\n\n");

        return sb.toString();
    }

    private void appendOptions(StringBuilder sb, List<LavanderiaServiceOption> options) {
        if (options == null || options.isEmpty()) {
            return;
        }
        Map<String, StringBuilder> byGroup = new LinkedHashMap<>();
        for (LavanderiaServiceOption opt : options) {
            if (!opt.available()) {
                continue;
            }
            StringBuilder line = byGroup.computeIfAbsent(opt.groupLabel(), g -> new StringBuilder());
            if (line.length() > 0) {
                line.append(" | ");
            }
            line.append(opt.id()).append(" ").append(opt.optionLabel())
                .append(" (+R$ ").append(formatBrl(opt.priceDeltaCents())).append(")");
        }
        for (Map.Entry<String, StringBuilder> e : byGroup.entrySet()) {
            sb.append("    [").append(e.getKey()).append("] ").append(e.getValue()).append("\n");
        }
    }

    private static String formatBrl(int cents) {
        return String.format("%d,%02d", cents / 100, cents % 100);
    }
}
