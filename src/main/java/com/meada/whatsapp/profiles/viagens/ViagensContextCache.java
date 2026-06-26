package com.meada.whatsapp.profiles.viagens;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.meada.whatsapp.profiles.viagens.consultants.TravelConsultant;
import com.meada.whatsapp.profiles.viagens.consultants.TravelConsultantRepository;
import com.meada.whatsapp.profiles.viagens.proposals.TravelProposal;
import com.meada.whatsapp.profiles.viagens.proposals.TravelProposalRepository;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

/**
 * Cache do bloco de contexto dinâmico injetado no prompt do ViagensBot (camada 8.18 / perfil viagens).
 * TTL 20s (espelho EventosContextCache 8.2 — a proposta não muda a cada segundo). Keyed por
 * {@code (companyId, contactId)}. Conteúdo:
 * <ul>
 *   <li>consultores ATIVOS (id + nome) — pra IA referenciar consultant_id ao abrir proposta;
 *   <li>PROPOSTAS do cliente em aberto (rascunho/orcada) com id + destino + datas + nº de viajantes +
 *       status + total — pra IA capturar a APROVAÇÃO referenciando a proposta ORÇADA certa (gate de
 *       2 fases).
 * </ul>
 * + instruções e as 2 tags ({@code <proposta_viagem>} e {@code <aprovacao_viagem>}). NÃO injeta o
 * ITINERÁRIO (organizacional do painel — espelho do cronograma não injetado no eventos).
 */
@Component
public class ViagensContextCache {

    private final TravelConsultantRepository consultantRepository;
    private final TravelProposalRepository proposalRepository;
    private final Cache<String, String> cache;

    public ViagensContextCache(TravelConsultantRepository consultantRepository,
                               TravelProposalRepository proposalRepository) {
        this.consultantRepository = consultantRepository;
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

    /** Invalida todas as entradas de uma empresa (mutação de consultor/proposta/item/itinerário/config). */
    public void invalidate(UUID companyId) {
        String prefix = companyId + ":";
        cache.asMap().keySet().removeIf(k -> k.startsWith(prefix));
    }

    private static String brl(int cents) {
        return "R$ " + String.format("%d,%02d", cents / 100, cents % 100);
    }

    private String buildSegment(UUID companyId, UUID contactId) {
        StringBuilder sb = new StringBuilder();

        // --- CONSULTORES ---
        List<TravelConsultant> consultants = consultantRepository.listByCompany(companyId, true);
        if (consultants.isEmpty()) {
            sb.append("CONSULTORES: (nenhum ativo no momento.)\n\n");
        } else {
            sb.append("CONSULTORES (use o consultant_id EXATO; atribuição é OPCIONAL):\n");
            for (TravelConsultant c : consultants) {
                sb.append("- ").append(c.id()).append(" · ").append(c.name());
                if (c.specialty() != null && !c.specialty().isBlank()) {
                    sb.append(" (").append(c.specialty()).append(")");
                }
                sb.append("\n");
            }
            sb.append("\n");
        }

        // --- PROPOSTAS DO CLIENTE EM ABERTO (pra capturar aprovação) ---
        if (contactId != null) {
            List<TravelProposal> openProposals = proposalRepository.listByCompany(companyId, null, null,
                contactId, null, null, 20, 0);
            StringBuilder block = new StringBuilder();
            for (TravelProposal p : openProposals) {
                if ("orcada".equals(p.status())) {
                    block.append("- ").append(p.id())
                        .append(" · ").append(p.destination() == null ? "viagem" : p.destination())
                        .append(datesLabel(p))
                        .append(" · ").append(p.numTravelers()).append(" viajante(s)")
                        .append(" · ORÇADA · total ").append(brl(p.totalCents()))
                        .append(" (aguardando aprovação do cliente)\n");
                } else if ("rascunho".equals(p.status())) {
                    block.append("- ").append(p.id())
                        .append(" · ").append(p.destination() == null ? "viagem" : p.destination())
                        .append(datesLabel(p))
                        .append(" · ").append(p.numTravelers()).append(" viajante(s)")
                        .append(" · RASCUNHO (ainda sem cotação)\n");
                }
            }
            if (block.length() > 0) {
                sb.append("PROPOSTAS DO CLIENTE EM ABERTO:\n").append(block)
                    .append("Quando o cliente responder se aprova/recusa um ORÇAMENTO, use a tag "
                        + "<aprovacao_viagem> com o proposal_id da proposta ORÇADA correspondente.\n\n");
            }
        } else {
            sb.append("CLIENTE NÃO IDENTIFICADO pelo telefone. Peça os dados da viagem (destino, datas, "
                + "número de viajantes, estilo) para abrir a proposta.\n\n");
        }

        // --- INSTRUÇÕES + TAGS ---
        sb.append("INSTRUÇÕES:\n")
            .append("Você ABRE a proposta a partir do briefing do cliente (destino, datas, número de "
                + "viajantes, estilo, o que ele sonha). NÃO fecha contrato, preço ou desconto — quem cota "
                + "e fecha é a equipe no painel. NÃO confirma voo, hotel, traslado ou disponibilidade não "
                + "confirmada ('vou verificar com a equipe'). NUNCA emita passagem ou voucher, NUNCA invente "
                + "destino, item de cotação, valor ou serviço, nem prometa estrutura/comodidade não informada.\n")
            .append("Para ABRIR uma proposta, sua ÚLTIMA mensagem deve TERMINAR com a tag (linha própria, "
                + "sem markdown):\n")
            .append("<proposta_viagem>{\"destination\":\"...|null\",\"start_date\":\"YYYY-MM-DD|null\","
                + "\"end_date\":\"YYYY-MM-DD|null\",\"num_travelers\":N|null,\"travel_style\":\"...|null\","
                + "\"briefing\":\"...\",\"consultant_id\":\"UUID|null\",\"notes\":\"...\"}"
                + "</proposta_viagem>\n")
            .append("Para CAPTURAR a resposta do cliente a um ORÇAMENTO (proposta já orçada), termine com:\n")
            .append("<aprovacao_viagem>{\"proposal_id\":\"UUID\",\"decisao\":\"aprovada|recusada\"}"
                + "</aprovacao_viagem>\n")
            .append("Use ids EXATOS. Só emita a tag de aprovação se houver uma proposta ORÇADA do cliente.\n\n");

        return sb.toString();
    }

    private static String datesLabel(TravelProposal p) {
        if (p.startDate() == null && p.endDate() == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(" de ");
        sb.append(p.startDate() == null ? "?" : p.startDate());
        sb.append(" a ").append(p.endDate() == null ? "?" : p.endDate());
        return sb.toString();
    }
}
