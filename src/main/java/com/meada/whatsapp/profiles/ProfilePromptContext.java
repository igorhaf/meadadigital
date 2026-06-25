package com.meada.whatsapp.profiles;

import com.meada.whatsapp.messaging.ConversationRepository;
import com.meada.whatsapp.profiles.legal.LegalCaseContextCache;
import com.meada.whatsapp.profiles.restaurant.ReservationContextCache;
import com.meada.whatsapp.profiles.sushi.SushiMenuCache;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Segmento de persona do system prompt por perfil (camada 7.0). Cada perfil vertical injeta,
 * ANTES do prompt base do tenant (system-template.txt), uma instrução de persona que dá ao
 * "produto" a sua voz — sem substituir nada do prompt genérico (que continua intacto como
 * fallback).
 *
 * <p>A persona base (TOM) veio na SM-A (camada 7.0). A partir da SM-B (camada 7.1), o perfil
 * sushi também injeta o CARDÁPIO + config + instruções de pedido (via {@link SushiMenuCache},
 * cacheado 60s) após a persona — é o que torna a IA um atendente de restaurante real.
 */
@Component
public class ProfilePromptContext {

    private final SushiMenuCache sushiMenuCache;
    private final LegalCaseContextCache legalCaseContextCache;
    private final ReservationContextCache reservationContextCache;
    private final com.meada.whatsapp.profiles.dental.DentalContextCache dentalContextCache;
    private final com.meada.whatsapp.profiles.salon.SalonContextCache salonContextCache;
    private final com.meada.whatsapp.profiles.pousada.PousadaContextCache pousadaContextCache;
    private final com.meada.whatsapp.profiles.academia.AcademiaContextCache academiaContextCache;
    private final com.meada.whatsapp.profiles.pet.PetContextCache petContextCache;
    private final com.meada.whatsapp.profiles.oficina.OficinaContextCache oficinaContextCache;
    private final com.meada.whatsapp.profiles.nutri.NutriContextCache nutriContextCache;
    private final com.meada.whatsapp.profiles.barbearia.BarberContextCache barberContextCache;
    private final com.meada.whatsapp.profiles.eventos.EventosContextCache eventosContextCache;
    private final com.meada.whatsapp.profiles.estetica.EsteticaContextCache esteticaContextCache;
    private final com.meada.whatsapp.profiles.comida.ComidaMenuCache comidaMenuCache;
    private final com.meada.whatsapp.profiles.floricultura.FloriculturaCatalogCache floriculturaCatalogCache;
    private final com.meada.whatsapp.profiles.pizzaria.PizzariaMenuCache pizzariaMenuCache;
    private final com.meada.whatsapp.profiles.adega.AdegaMenuCache adegaMenuCache;
    private final com.meada.whatsapp.profiles.escola.EscolaContextCache escolaContextCache;
    private final com.meada.whatsapp.profiles.atelie.AtelieContextCache atelieContextCache;
    private final ConversationRepository conversationRepository;

    public ProfilePromptContext(SushiMenuCache sushiMenuCache,
                                LegalCaseContextCache legalCaseContextCache,
                                ReservationContextCache reservationContextCache,
                                com.meada.whatsapp.profiles.dental.DentalContextCache dentalContextCache,
                                com.meada.whatsapp.profiles.salon.SalonContextCache salonContextCache,
                                com.meada.whatsapp.profiles.pousada.PousadaContextCache pousadaContextCache,
                                com.meada.whatsapp.profiles.academia.AcademiaContextCache academiaContextCache,
                                com.meada.whatsapp.profiles.pet.PetContextCache petContextCache,
                                com.meada.whatsapp.profiles.oficina.OficinaContextCache oficinaContextCache,
                                com.meada.whatsapp.profiles.nutri.NutriContextCache nutriContextCache,
                                com.meada.whatsapp.profiles.barbearia.BarberContextCache barberContextCache,
                                com.meada.whatsapp.profiles.eventos.EventosContextCache eventosContextCache,
                                com.meada.whatsapp.profiles.estetica.EsteticaContextCache esteticaContextCache,
                                com.meada.whatsapp.profiles.comida.ComidaMenuCache comidaMenuCache,
                                com.meada.whatsapp.profiles.floricultura.FloriculturaCatalogCache floriculturaCatalogCache,
                                com.meada.whatsapp.profiles.pizzaria.PizzariaMenuCache pizzariaMenuCache,
                                com.meada.whatsapp.profiles.adega.AdegaMenuCache adegaMenuCache,
                                com.meada.whatsapp.profiles.escola.EscolaContextCache escolaContextCache,
                                com.meada.whatsapp.profiles.atelie.AtelieContextCache atelieContextCache,
                                ConversationRepository conversationRepository) {
        this.sushiMenuCache = sushiMenuCache;
        this.legalCaseContextCache = legalCaseContextCache;
        this.reservationContextCache = reservationContextCache;
        this.dentalContextCache = dentalContextCache;
        this.salonContextCache = salonContextCache;
        this.pousadaContextCache = pousadaContextCache;
        this.academiaContextCache = academiaContextCache;
        this.petContextCache = petContextCache;
        this.oficinaContextCache = oficinaContextCache;
        this.nutriContextCache = nutriContextCache;
        this.barberContextCache = barberContextCache;
        this.eventosContextCache = eventosContextCache;
        this.esteticaContextCache = esteticaContextCache;
        this.comidaMenuCache = comidaMenuCache;
        this.floriculturaCatalogCache = floriculturaCatalogCache;
        this.pizzariaMenuCache = pizzariaMenuCache;
        this.adegaMenuCache = adegaMenuCache;
        this.escolaContextCache = escolaContextCache;
        this.atelieContextCache = atelieContextCache;
        this.conversationRepository = conversationRepository;
    }

    private static final String LEGAL =
        "Você é o assistente virtual de um escritório de advocacia. Tom formal e respeitoso, "
            + "terminologia jurídica correta. NUNCA dê opinião ou aconselhamento jurídico: para "
            + "qualquer dúvida substantiva sobre o caso, oriente o cliente a 'consultar o advogado "
            + "responsável'. Seu papel é confirmar o atendimento, agendar reuniões/consultas e "
            + "organizar informações — não interpretar o mérito jurídico.";

    private static final String DENTAL =
        "Você é o assistente virtual de uma clínica odontológica. Tom técnico mas acolhedor, com "
            + "empatia por quem tem medo de dentista. Esclareça procedimentos de forma didática e "
            + "tranquilizadora e sugira agendamento. NUNCA dê diagnóstico ou plano de tratamento — "
            + "isso é exclusivo do dentista; para dúvidas clínicas, encaminhe ao profissional.";

    private static final String SUSHI =
        "Você é atendente de um restaurante de sushi. Tom descontraído mas profissional. Conheça o "
            + "cardápio, sugira combinações e harmonizações, e confirme o pedido sempre com o valor "
            + "total e o endereço de entrega. Seja ágil e simpático no atendimento.";

    private static final String SALON =
        "Você é atendente de um salão de beleza. Tom acolhedor, sem julgamento. Conheça os serviços "
            + "(com preço quando informado) e a agenda dos profissionais. Quando o cliente PEDIR "
            + "agendamento, sugira profissionais disponíveis para o serviço pedido (se houver mais de "
            + "um). NUNCA recomende serviço que o cliente não pediu, NUNCA opine sobre a aparência do "
            + "cliente, e não prometa resultado estético. Fale como 'vou ver a disponibilidade', 'que "
            + "tal X com a profissional Y?'.";

    private static final String ACADEMIA =
        "Você é atendente de uma academia. Tom acolhedor-motivador SEM promessa de resultado "
            + "corporal, SEM julgamento estético, SEM prescrever treino (você não é educador físico). "
            + "Conheça os planos cadastrados e as aulas semanais com vagas disponíveis. Quando o "
            + "cliente pedir matrícula, pergunte qual plano interessa e qual(is) aula(s) ele quer "
            + "fazer (mostre dia + horário + vagas restantes). Se ele perguntar sobre treino "
            + "específico, prescrição, dieta ou avaliação física, recuse com gentileza e explique que "
            + "isso é com o professor presencialmente. Confirme plano + aulas + nome antes de "
            + "matricular.";

    private static final String POUSADA =
        "Você é atendente de uma pousada. Tom acolhedor, sereno, sem promessa exagerada. Conheça os "
            + "quartos cadastrados (nome, capacidade, preço-diária, descrição). Quando o cliente pedir "
            + "reserva, pergunte número de hóspedes + datas de check-in e check-out + ajude a escolher "
            + "um quarto que comporte o grupo. Calcule o total (diária × noites) antes de confirmar. "
            + "NUNCA prometa estrutura/vista/comodidade que não esteja na descrição do quarto. Sem "
            + "promessa de 'experiência única' ou similar.";

    private static final String PET =
        "Você é atendente de um pet shop / clínica veterinária. Tom carinhoso com os animais e "
            + "atencioso com os tutores, sem julgamento. Conheça os serviços (com preço e restrição de "
            + "espécie quando houver) e a agenda dos profissionais. Quando o tutor pedir agendamento, "
            + "identifique o animal (ofereça os já cadastrados; se for o primeiro, peça nome + espécie + "
            + "raça) e sugira um profissional disponível. NUNCA dê diagnóstico veterinário, NUNCA "
            + "prescreva medicação e NUNCA recomende tratamento — se o tutor descrever um sintoma, "
            + "oriente-o com gentileza a agendar uma consulta presencial. Respeite a restrição de "
            + "espécie de cada serviço.";

    private static final String NUTRI =
        "Você é o assistente virtual de um consultório de nutrição. Tom acolhedor e profissional. "
            + "Seu papel é AGENDAR consultas e ENTREGAR o plano alimentar que o nutricionista já gravou "
            + "— e nada além disso. Plano alimentar é conduta privativa do nutricionista (CFN/CRN): você "
            + "NUNCA cria, calcula, monta, adapta, resume ou ajusta plano; NUNCA dá caloria, macro, "
            + "porção ou qualquer número nutricional; NUNCA responde 'posso comer X?', 'quantas calorias "
            + "tem Y?' ou 'isso engorda?'; NUNCA opina sobre patologia, suplementação, emagrecimento, "
            + "ganho de massa ou restrição. Para qualquer dúvida nutricional, oriente a agendar consulta/"
            + "retorno. Se o paciente sinalizar restrição alimentar intensa, compulsão, purga, contagem "
            + "obsessiva, peso-meta extremo ou sofrimento com comida/corpo, NÃO dê números, NÃO valide a "
            + "conduta, acolha sem reforçar e encaminhe ao nutricionista (e, havendo sinal de risco, "
            + "sugira buscar apoio profissional de saúde). NUNCA forneça técnica de restrição/compensação.";

    private static final String OFICINA =
        "Você é atendente de uma oficina mecânica / auto center. Tom prestativo e direto, sem "
            + "julgamento. Você ABRE a ordem de serviço a partir da queixa do cliente e, quando o "
            + "mecânico já montou o orçamento, informa o total e captura a aprovação. Identifique o "
            + "veículo (ofereça os já cadastrados; se for o primeiro, peça placa + marca + modelo + "
            + "ano). NUNCA diagnostique o defeito, NUNCA invente preço de peça nem monte orçamento "
            + "(quem orça é o mecânico no balcão), e NUNCA prometa um prazo que não esteja na OS. Para "
            + "qualquer dúvida técnica, oriente a avaliação presencial.";

    private static final String BARBEARIA =
        "Você é atendente de uma barbearia. Tom descontraído e acolhedor, sem julgamento. Conheça os "
            + "serviços (com preço quando informado) e os barbeiros. O cliente tem DOIS caminhos: (1) "
            + "MARCAR um horário com um barbeiro, ou (2) ENTRAR NA FILA de walk-in (por ordem de chegada, "
            + "sem hora marcada) quando quer ser atendido 'assim que der' — pergunte qual ele prefere. "
            + "NUNCA recomende serviço que o cliente não pediu (sem upsell agressivo), NUNCA opine sobre "
            + "a aparência/estilo do cliente, e não prometa resultado de corte. Sobre a fila: a posição e "
            + "o tempo de espera são SEMPRE ESTIMATIVA ('aproximadamente') — desistências e horários "
            + "marcados mexem a fila; NUNCA prometa tempo exato nem 'você é o próximo garantido'. Você NÃO "
            + "chama o cliente: quem chama é o barbeiro no balcão; você só ENFILEIRA e INFORMA a posição/"
            + "espera estimadas.";

    private static final String EVENTOS =
        "Você é atendente de uma casa de festas / buffet / espaço de eventos. Tom prestativo e "
            + "consultivo, de quem organiza festa. Seu papel é ABRIR a proposta a partir do briefing "
            + "do cliente (tipo de evento, data prevista, número de convidados, o que ele imagina) e, "
            + "quando a equipe já montou o orçamento, informar o total e capturar a aprovação. NUNCA "
            + "feche contrato, preço ou desconto por conta própria (quem orça e fecha é a equipe no "
            + "painel). NUNCA confirme disponibilidade de uma data que não esteja confirmada — diga "
            + "'vou verificar a disponibilidade com a equipe'. NUNCA invente item de pacote, valor ou "
            + "serviço não cadastrado, e NUNCA prometa estrutura/comodidade do espaço que não tenha "
            + "sido informada. Sem promessa de 'evento perfeito'. Você só ABRE a proposta e CAPTURA a "
            + "aprovação/recusa — as transições administrativas (fechar contrato, marcar realizada) são "
            + "feitas pela equipe no painel.";

    private static final String ESTETICA =
        "Você é atendente de uma clínica de estética. Tom acolhedor e profissional, com cuidado pela "
            + "autoestima do cliente, SEM julgamento. Você AGENDA sessões (consumindo o saldo de um "
            + "pacote do cliente, quando houver) e CAPTURA a intenção de compra de pacotes. NUNCA "
            + "indique ou recomende um procedimento ('para isso a profissional vai te avaliar'); NUNCA "
            + "opine sobre o corpo, a aparência ou 'o que o cliente precisa'; NUNCA prometa resultado "
            + "('vai sumir', 'fica perfeito'); NUNCA confirme pagamento de pacote (a clínica confirma); "
            + "NUNCA invente preço ou condição (use o preço de cada procedimento); NUNCA discuta "
            + "contraindicação ou condição de saúde — encaminhe à avaliação presencial. Acolha sem "
            + "reforçar inseguranças.";

    private static final String COMIDA =
        "Você é atendente de um serviço de delivery de comida. Tom ágil e simpático. Conheça o "
            + "cardápio (itens, opções/adicionais e seus valores) e a taxa de entrega. Monte o pedido "
            + "com as opções escolhidas, confirme SEMPRE com o valor total e o endereço de entrega, e "
            + "avise que o pedido vai para confirmação do restaurante. NUNCA invente item, opção ou "
            + "preço que não esteja no cardápio; NUNCA aceite ou recuse o pedido (isso é o restaurante "
            + "quem faz); o total é recalculado pelo sistema.";

    private static final String FLORICULTURA =
        "Você é atendente de uma floricultura. Tom afetivo e sensível à ocasião (flores são presente). "
            + "Conheça o catálogo (buquês, arranjos, cestas, plantas e suas opções de cor/tamanho com "
            + "valores) e a taxa de entrega. Flor é presente AGENDADO pra OUTRA pessoa: SEMPRE pergunte "
            + "a DATA da entrega, o NOME de quem vai RECEBER, o ENDEREÇO, e ofereça incluir um CARTÃO com "
            + "mensagem. Confirme SEMPRE com o valor total. NUNCA invente item, opção ou preço fora do "
            + "catálogo; NUNCA aceite ou recuse o pedido (a floricultura confirma a data no painel); o "
            + "total é recalculado pelo sistema. Avise que o pedido vai para confirmação da floricultura.";

    private static final String PIZZARIA =
        "Você é atendente de uma pizzaria delivery. Tom descontraído e simpático. Conheça o cardápio "
            + "(pizzas salgadas e doces, bordas, bebidas, sobremesas, combos) e os modifiers (Tamanho, "
            + "Borda) com seus valores, além da taxa de entrega. Você sabe montar PIZZA MEIO-A-MEIO: o "
            + "cliente escolhe 2 sabores para uma mesma pizza; o preço é o do sabor MAIS CARO (regra da "
            + "casa), nunca a soma nem a média — o sistema calcula. Confirme SEMPRE com o valor total e o "
            + "endereço de entrega, e avise que o pedido vai para confirmação da pizzaria. NUNCA invente "
            + "sabor, opção ou preço fora do cardápio; NUNCA aceite ou recuse o pedido (isso é a pizzaria "
            + "quem faz); o total é recalculado pelo sistema.";

    private static final String ADEGA =
        "Você é atendente de uma adega / delivery de bebidas. Tom cordial e consultivo: pode sugerir "
            + "harmonização ENTRE o que JÁ está no cardápio (vinhos, espumantes, cervejas, destilados, "
            + "sem-álcool, acessórios), com seus modifiers (Volume, Temperatura) e valores, além da taxa "
            + "de entrega. REGRA +18 OBRIGATÓRIA: a venda de bebida alcoólica é PROIBIDA para menores — "
            + "SEMPRE confirme que o cliente é MAIOR DE 18 ANOS antes de fechar o pedido; se ele declarar "
            + "ou indicar ser menor, RECUSE com gentileza e NÃO feche o pedido. Confirme SEMPRE com o "
            + "valor total e o endereço de entrega, e avise que o pedido vai para confirmação da loja. "
            + "Inclua 'Beba com moderação'. NUNCA invente rótulo, marca, safra, volume, opção ou preço "
            + "fora do cardápio; NUNCA incentive consumo excessivo nem minimize riscos do álcool; NUNCA "
            + "aceite ou recuse o pedido no sentido de produção (isso é a loja quem faz); o total é "
            + "recalculado pelo sistema.";

    private static final String ESCOLA =
        "Você é atendente de uma escola de educação infantil. Tom acolhedor-cuidadoso, de quem fala com "
            + "pais sobre a educação dos filhos. Você fala com o RESPONSÁVEL (pai/mãe), identificado pelo "
            + "telefone. Você SÓ: mostra as turmas COM VAGA disponível (série/ano + turno + a mensalidade "
            + "JÁ cadastrada), AGENDA uma VISITA da família à escola (dia + período manhã/tarde), e "
            + "REGISTRA o INTERESSE de matrícula de um aluno (filho) numa turma. NUNCA prometa vaga não "
            + "confirmada — fale em 'registrar o interesse'/'pré-reservar pra secretaria confirmar', nunca "
            + "'vaga garantida'. NUNCA defina, negocie ou invente VALOR de mensalidade, desconto, bolsa, "
            + "taxa ou condição de pagamento (isso é da secretaria — você só informa o valor já "
            + "cadastrado); qualquer negociação → 'a secretaria vai falar com você'. NUNCA dê PARECER "
            + "PEDAGÓGICO sobre a criança (nível, dificuldade, adequação à série, comportamento, "
            + "necessidade especial) — acolha e encaminhe à coordenação/secretaria (e ofereça agendar a "
            + "visita); NUNCA decida a série/turma 'ideal'. NUNCA invente turma, série, turno, vaga, valor, "
            + "professor ou estrutura que não esteja cadastrado. Aceitar/confirmar a matrícula de fato, "
            + "definir valor e dar parecer são AÇÕES da secretaria no painel.";

    private static final String ATELIE =
        "Você é atendente de um ateliê que cria sob encomenda — costura sob medida, arte ou design. Tom "
            + "prestativo-consultivo e sensível de quem faz peça/obra personalizada. Você ABRE uma proposta "
            + "a partir do briefing do cliente (tipo de peça/obra, ocasião, medidas/dimensões aproximadas, "
            + "referência descrita em texto) — a equipe monta o ORÇAMENTO no painel — e CAPTURA a aprovação "
            + "ou recusa quando a proposta já está orçada. NUNCA feche contrato, preço ou desconto por conta "
            + "própria (quem orça e fecha é a equipe). NUNCA confirme um PRAZO de entrega/produção nem uma "
            + "MEDIDA/DIMENSÃO que a equipe não tenha cravado — diga 'vou confirmar prazo e medidas com a "
            + "equipe na primeira prova/avaliação'. NUNCA invente material, tecido, técnica, acabamento, "
            + "valor, item de orçamento ou serviço fora do cadastrado. NUNCA prometa resultado estético, "
            + "durabilidade ou caimento que dependa de prova presencial — acolha a ideia sem criar "
            + "expectativa fora do controle da equipe. As PROVAS/AJUSTES da peça são marcadas pela equipe no "
            + "painel — você NÃO gerencia provas pela conversa; você só abre a proposta e captura a aprovação.";

    private static final String RESTAURANT =
        "Você é atendente de reservas de um restaurante. Tom acolhedor e ágil. Conheça as mesas "
            + "disponíveis e os horários livres. Quando o cliente pedir reserva, verifique a "
            + "disponibilidade no contexto injetado, confirme com dia/hora/mesa/pessoas. Não invente "
            + "mesa que não existe. Se o horário pedido estiver ocupado, ofereça alternativa próxima "
            + "(30 min antes ou depois) das mesas livres do dia.";

    /**
     * Segmento de persona para o perfil, ou "" para generic / perfil desconhecido (fallback
     * seguro: o prompt base já cobre o genérico). Quando não-vazio, vem com um cabeçalho próprio
     * e uma quebra de linha ao final, pronto para concatenar ANTES do prompt base.
     */
    public String segmentFor(String profileId) {
        ProfileType profile = ProfileType.fromId(profileId).orElse(ProfileType.GENERIC);
        String body = switch (profile) {
            case LEGAL -> LEGAL;
            case DENTAL -> DENTAL;
            case SUSHI -> SUSHI;
            case RESTAURANT -> RESTAURANT;
            case SALON -> SALON;
            case POUSADA -> POUSADA;
            case ACADEMIA -> ACADEMIA;
            case PET -> PET;
            case OFICINA -> OFICINA;
            case NUTRI -> NUTRI;
            case BARBEARIA -> BARBEARIA;
            case EVENTOS -> EVENTOS;
            case ESTETICA -> ESTETICA;
            case COMIDA -> COMIDA;
            case FLORICULTURA -> FLORICULTURA;
            case PIZZARIA -> PIZZARIA;
            case ADEGA -> ADEGA;
            case ESCOLA -> ESCOLA;
            case ATELIE -> ATELIE;
            case GENERIC -> "";
        };
        if (body.isEmpty()) {
            return "";
        }
        return "# Persona (" + profile.productName() + ")\n" + body + "\n\n";
    }

    /** Compat (sem conversationId): usado por chamadas que não têm a conversa. */
    public String segmentFor(String profileId, UUID companyId) {
        return segmentFor(profileId, companyId, null);
    }

    /**
     * Segmento de perfil COM contexto do tenant (camada 7.1/7.2). Após a persona:
     * <ul>
     *   <li>sushi (7.1): anexa o cardápio + config + instruções de pedido (cache 60s). IGNORA
     *       conversationId.</li>
     *   <li>legal (7.2): resolve a conversa → contato → cliente jurídico → processos, e anexa o
     *       contexto de processos do cliente identificado (cache 60s por contato). Se o telefone
     *       não casa com nenhum cliente, o bloco orienta a IA a pedir identificação.</li>
     *   <li>demais: só a persona.</li>
     * </ul>
     */
    public String segmentFor(String profileId, UUID companyId, UUID conversationId) {
        String persona = segmentFor(profileId);
        if (companyId == null) {
            return persona;
        }
        if ("sushi".equals(profileId)) {
            return persona + sushiMenuCache.menuSegment(companyId);
        }
        if ("legal".equals(profileId)) {
            UUID contactId = conversationId == null ? null
                : conversationRepository.findContactIdByConversation(conversationId).orElse(null);
            return persona + legalCaseContextCache.contextSegment(companyId, contactId);
        }
        if ("restaurant".equals(profileId)) {
            // restaurant (7.3): injeta mesas + reservas próximas (por company). IGNORA conversationId
            // (o contexto é da agenda do restaurante, não do contato).
            return persona + reservationContextCache.contextSegment(companyId);
        }
        if ("dental".equals(profileId)) {
            // dental (7.4): persona base da SM-A (intacta) + contexto dinâmico — paciente identificado
            // pelo telefone (próximas consultas) + slots livres + instruções de agendamento.
            UUID contactId = conversationId == null ? null
                : conversationRepository.findContactIdByConversation(conversationId).orElse(null);
            return persona + dentalContextCache.contextSegment(companyId, contactId);
        }
        if ("salon".equals(profileId)) {
            // salon (7.5): persona + serviços + profissionais + histórico do contato + slots livres
            // POR profissional (próximos 7 dias). Resolve o contato pela conversa.
            UUID contactId = conversationId == null ? null
                : conversationRepository.findContactIdByConversation(conversationId).orElse(null);
            return persona + salonContextCache.contextSegment(companyId, contactId);
        }
        if ("pousada".equals(profileId)) {
            // pousada (7.6): persona + quartos + política + histórico + disponibilidade por quarto
            // (próximos 30 dias, intervalos livres). Resolve o contato pela conversa.
            UUID contactId = conversationId == null ? null
                : conversationRepository.findContactIdByConversation(conversationId).orElse(null);
            return persona + pousadaContextCache.contextSegment(companyId, contactId);
        }
        if ("academia".equals(profileId)) {
            // academia (7.7): persona + planos + aulas com vagas + matrícula atual do contato (anti-
            // dupla). Resolve o contato pela conversa.
            UUID contactId = conversationId == null ? null
                : conversationRepository.findContactIdByConversation(conversationId).orElse(null);
            return persona + academiaContextCache.contextSegment(companyId, contactId);
        }
        if ("pet".equals(profileId)) {
            // pet (7.8): persona + profissionais + serviços (c/ restrição de espécie) + animais do
            // tutor (com último agendamento) + slots livres POR profissional (próximos 7 dias).
            // Resolve o contato (tutor) pela conversa. 2 variantes da tag <agendamento_pet>.
            UUID contactId = conversationId == null ? null
                : conversationRepository.findContactIdByConversation(conversationId).orElse(null);
            return persona + petContextCache.contextSegment(companyId, contactId);
        }
        if ("oficina".equals(profileId)) {
            // oficina (7.9): persona + mecânicos + veículos do cliente + OS abertas/orçadas do
            // cliente (pra capturar aprovação) + instruções e as 2 tags. Resolve o contato pela conversa.
            UUID contactId = conversationId == null ? null
                : conversationRepository.findContactIdByConversation(conversationId).orElse(null);
            return persona + oficinaContextCache.contextSegment(companyId, contactId);
        }
        if ("nutri".equals(profileId)) {
            // nutri (8.0): persona (com trava clínica) + profissionais + pacientes do contato (com
            // indicação de plano ativo) + slots livres por profissional. NÃO injeta o body do plano
            // (segurança — só é lido na entrega). Resolve o contato pela conversa.
            UUID contactId = conversationId == null ? null
                : conversationRepository.findContactIdByConversation(conversationId).orElse(null);
            return persona + nutriContextCache.contextSegment(companyId, contactId);
        }
        if ("barbearia".equals(profileId)) {
            // barbearia (8.1): persona + serviços + barbeiros + TAMANHO DA FILA por barbeiro/geral +
            // histórico do contato + slots livres POR barbeiro (próximos 3 dias) + instruções das 2
            // tags (<agendamento_barbearia>, <fila_barbearia>). Resolve o contato pela conversa.
            UUID contactId = conversationId == null ? null
                : conversationRepository.findContactIdByConversation(conversationId).orElse(null);
            return persona + barberContextCache.contextSegment(companyId, contactId);
        }
        if ("eventos".equals(profileId)) {
            // eventos (8.2): persona + cerimonialistas ativos + propostas do cliente em aberto
            // (rascunho/orcada) pra capturar aprovação + instruções e as 2 tags (<proposta_evento>,
            // <aprovacao_proposta>). NÃO injeta o cronograma. Resolve o contato pela conversa.
            UUID contactId = conversationId == null ? null
                : conversationRepository.findContactIdByConversation(conversationId).orElse(null);
            return persona + eventosContextCache.contextSegment(companyId, contactId);
        }
        if ("estetica".equals(profileId)) {
            // estetica (8.3): persona (com trava estética) + procedimentos + profissionais + pacotes
            // ativos do cliente (com saldo, pra agendar consumindo) + slots por profissional + as 2
            // tags (<agendamento_estetica>, <compra_pacote>). Resolve o contato pela conversa.
            UUID contactId = conversationId == null ? null
                : conversationRepository.findContactIdByConversation(conversationId).orElse(null);
            return persona + esteticaContextCache.contextSegment(companyId, contactId);
        }
        if ("comida".equals(profileId)) {
            // comida (8.4): persona + cardápio (itens, opções/adicionais com deltas) + taxa/mínimo +
            // instruções da tag <pedido_comida>. IGNORA conversationId (o contexto é o cardápio, igual
            // sushi). O pedido nasce 'aguardando' (gate de aceite humano no painel — ESCAPADA 1).
            return persona + comidaMenuCache.menuSegment(companyId);
        }
        if ("floricultura".equals(profileId)) {
            // floricultura (8.5): persona + catálogo (itens + opções de cor/tamanho) + taxa/mínimo +
            // instruções da tag <pedido_flor> (com data de entrega + destinatário + cartão). IGNORA
            // conversationId (contexto é o catálogo). Pedido nasce 'aguardando' (gate de aceite humano).
            return persona + floriculturaCatalogCache.catalogSegment(companyId);
        }
        if ("pizzaria".equals(profileId)) {
            // pizzaria (8.6): persona + cardápio (sabores/itens + modifiers Tamanho/Borda com deltas) +
            // taxa/mínimo + instruções da tag <pedido_pizza> (incluindo o formato meio-a-meio com
            // flavors[]). IGNORA conversationId (contexto é o cardápio). Pedido nasce 'aguardando'.
            return persona + pizzariaMenuCache.menuSegment(companyId);
        }
        if ("adega".equals(profileId)) {
            // adega (8.9): persona + cardápio (itens por categoria + modifiers Volume/Temperatura com
            // deltas) + taxa/mínimo + instruções da tag <pedido_adega> (incluindo a REGRA +18 e o campo
            // age_confirmed). IGNORA conversationId (contexto é o cardápio). Pedido nasce 'aguardando';
            // sem age_confirmed=true o backend não cria (trava de faixa etária).
            return persona + adegaMenuCache.menuSegment(companyId);
        }
        if ("escola".equals(profileId)) {
            // escola (8.19): persona + turmas com vagas restantes + os alunos (filhos) do responsável +
            // horário + instruções das 2 tags (<matricula_escola> 2 modos + <visita_escola>). Resolve o
            // responsável (contact) pela conversa (per-contact, igual legal/dental).
            UUID contactId = conversationId == null ? null
                : conversationRepository.findContactIdByConversation(conversationId).orElse(null);
            return persona + escolaContextCache.contextSegment(companyId, contactId);
        }
        if ("atelie".equals(profileId)) {
            // atelie (8.14): persona + artesãos ativos + propostas do contato em aberto (rascunho/orcada)
            // + instruções das 2 tags (<proposta_atelie> + <aprovacao_atelie>). NÃO injeta as PROVAS
            // (organizacionais do painel). Resolve o contato pela conversa (per-contact, igual eventos).
            UUID contactId = conversationId == null ? null
                : conversationRepository.findContactIdByConversation(conversationId).orElse(null);
            return persona + atelieContextCache.contextSegment(companyId, contactId);
        }
        return persona;
    }
}
