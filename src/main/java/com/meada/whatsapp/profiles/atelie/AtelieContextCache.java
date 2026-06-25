package com.meada.whatsapp.profiles.atelie;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.meada.whatsapp.profiles.atelie.artisans.AtelieArtisan;
import com.meada.whatsapp.profiles.atelie.artisans.AtelieArtisanRepository;
import com.meada.whatsapp.profiles.atelie.proposals.AtelieProposal;
import com.meada.whatsapp.profiles.atelie.proposals.AtelieProposalRepository;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

/**
 * Cache do bloco de contexto dinâmico injetado no prompt do AtelieBot (camada 8.14). TTL 20s
 * (espelho eventos — a proposta não muda a cada segundo). Keyed por {@code (companyId, contactId)}.
 * Conteúdo:
 * <ul>
 *   <li>artesãos ativos (id + nome) — pra IA referenciar artisan_id ao abrir proposta;
 *   <li>PROPOSTAS do cliente em aberto (rascunho/orcada) com id + tipo de projeto + ocasião + data +
 *       status + total — pra IA capturar a APROVAÇÃO referenciando a proposta ORÇADA certa (gate de
 *       2 fases).
 * </ul>
 * + instruções e as 2 tags ({@code <proposta_atelie>} e {@code <aprovacao_atelie>}). NÃO injeta as
 * provas/ajustes (organizacionais do painel). Espelho do EventosContextCache.
 */
@Component
public class AtelieContextCache {

    private final AtelieArtisanRepository artisanRepository;
    private final AtelieProposalRepository proposalRepository;
    private final Cache<String, String> cache;

    public AtelieContextCache(AtelieArtisanRepository artisanRepository,
                              AtelieProposalRepository proposalRepository) {
        this.artisanRepository = artisanRepository;
        this.proposalRepository = proposalRepository;
        this.cache = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofSeconds(20))
            .maximumSize(1000)
            .build();
    }

    public String contextSegment(UUID companyId, UUID contactId) {
        String key = companyId + ":" + (contactId == null ? "none" : contactId.toString());
        return cache.get(key, k -> buildSegment(companyId, contactId));
    }

    /** Invalida todas as entradas de uma empresa (mutação de artesão/proposta/item/prova/config). */
    public void invalidate(UUID companyId) {
        String prefix = companyId + ":";
        cache.asMap().keySet().removeIf(k -> k.startsWith(prefix));
    }

    private static String brl(int cents) {
        return "R$ " + String.format("%d,%02d", cents / 100, cents % 100);
    }

    private static String projectLabel(String projectType) {
        if (projectType == null) {
            return "projeto";
        }
        return AtelieProjectType.fromId(projectType).map(AtelieProjectType::label).orElse(projectType);
    }

    private String buildSegment(UUID companyId, UUID contactId) {
        StringBuilder sb = new StringBuilder();

        // --- ARTESÃOS ---
        List<AtelieArtisan> artisans = artisanRepository.listByCompany(companyId, true);
        if (artisans.isEmpty()) {
            sb.append("ARTESÃOS: (nenhum ativo no momento.)\n\n");
        } else {
            sb.append("ARTESÃOS (use o artisan_id EXATO; atribuição é OPCIONAL):\n");
            for (AtelieArtisan a : artisans) {
                sb.append("- ").append(a.id()).append(" · ").append(a.name());
                if (a.specialty() != null && !a.specialty().isBlank()) {
                    sb.append(" (").append(a.specialty()).append(")");
                }
                sb.append("\n");
            }
            sb.append("\n");
        }

        // --- PROPOSTAS DO CLIENTE EM ABERTO (pra capturar aprovação) ---
        if (contactId != null) {
            List<AtelieProposal> openProposals = proposalRepository.listByCompany(companyId, null, null,
                contactId, null, 20, 0);
            StringBuilder block = new StringBuilder();
            for (AtelieProposal p : openProposals) {
                if ("orcada".equals(p.status())) {
                    block.append("- ").append(p.id())
                        .append(" · ").append(projectLabel(p.projectType()))
                        .append(p.occasion() == null ? "" : " (" + p.occasion() + ")")
                        .append(p.estimatedDate() == null ? "" : " · previsão " + p.estimatedDate())
                        .append(" · ORÇADA · total ").append(brl(p.totalCents()))
                        .append(" (aguardando aprovação do cliente)\n");
                } else if ("rascunho".equals(p.status())) {
                    block.append("- ").append(p.id())
                        .append(" · ").append(projectLabel(p.projectType()))
                        .append(p.occasion() == null ? "" : " (" + p.occasion() + ")")
                        .append(p.estimatedDate() == null ? "" : " · previsão " + p.estimatedDate())
                        .append(" · RASCUNHO (ainda sem orçamento)\n");
                }
            }
            if (block.length() > 0) {
                sb.append("PROPOSTAS DO CLIENTE EM ABERTO:\n").append(block)
                    .append("Quando o cliente responder se aprova/recusa um ORÇAMENTO, use a tag "
                        + "<aprovacao_atelie> com o proposal_id da proposta ORÇADA correspondente.\n\n");
            }
        } else {
            sb.append("CLIENTE NÃO IDENTIFICADO pelo telefone. Peça os dados do projeto (tipo: costura sob "
                + "medida, arte ou design; ocasião; o que ele imagina) para abrir a proposta.\n\n");
        }

        // --- INSTRUÇÕES + TAGS ---
        sb.append("INSTRUÇÕES:\n")
            .append("Você ABRE a proposta a partir do briefing do cliente (tipo de projeto: costura sob medida, "
                + "arte ou design; ocasião; previsão de entrega; o que ele imagina, medidas/dimensões aproximadas, "
                + "referência descrita). NÃO fecha contrato, preço ou desconto — quem orça e fecha é a equipe no "
                + "painel. NÃO confirma prazo não confirmado ('vou alinhar a previsão com a equipe'). NUNCA invente "
                + "item, valor ou material, nem prometa resultado da peça.\n")
            .append("Para ABRIR uma proposta, sua ÚLTIMA mensagem deve TERMINAR com a tag (linha própria, "
                + "sem markdown):\n")
            .append("<proposta_atelie>{\"project_type\":\"costura|arte|design\",\"occasion\":\"texto|null\","
                + "\"estimated_date\":\"YYYY-MM-DD|null\",\"briefing\":\"...\",\"artisan_id\":\"UUID|null\","
                + "\"notes\":\"...\"}</proposta_atelie>\n")
            .append("Para CAPTURAR a resposta do cliente a um ORÇAMENTO (proposta já orçada), termine com:\n")
            .append("<aprovacao_atelie>{\"proposal_id\":\"UUID\",\"decisao\":\"aprovada|recusada\"}"
                + "</aprovacao_atelie>\n")
            .append("Use ids EXATOS. Só emita a tag de aprovação se houver uma proposta ORÇADA do cliente.\n\n");

        return sb.toString();
    }
}
