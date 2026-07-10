package com.meada.profiles.oficina.orders;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.meada.profiles.oficina.vehicles.OsVehicle;
import com.meada.profiles.oficina.vehicles.OsVehicleService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extrai a tag {@code <ordem_servico>{...}</ordem_servico>} da resposta da IA e ABRE a OS (camada
 * 7.9). Espelho do AgendamentoPetConfirmHandler — 2 MODOS:
 * <ul>
 *   <li><b>vehicle_id</b> existente: abre a OS para um veículo já cadastrado do cliente.</li>
 *   <li><b>new_vehicle</b> {plate, brand, model, year}: cadastra o veículo (sub-entidade do cliente
 *       da conversa) e ABRE a OS — tudo no mesmo turno.</li>
 * </ul>
 *
 * <p>NÃO usa tool calling / responseSchema. O cliente vem do contato da conversa; os snapshots de
 * cliente/veículo são resolvidos pelo {@link ServiceOrderService} a partir do veículo. Qualquer
 * falha → {@link Optional#empty()} + warn (a mensagem da IA segue sem efeito colateral).
 */
@Component
public class AberturaOsConfirmHandler {

    private static final Logger log = LoggerFactory.getLogger(AberturaOsConfirmHandler.class);

    private static final Pattern TAG = Pattern.compile("<ordem_servico>\\s*(\\{.*?\\})\\s*</ordem_servico>",
        Pattern.DOTALL);

    private final ObjectMapper objectMapper;
    private final OsVehicleService vehicleService;
    private final ServiceOrderService orderService;

    public AberturaOsConfirmHandler(ObjectMapper objectMapper, OsVehicleService vehicleService,
                                    ServiceOrderService orderService) {
        this.objectMapper = objectMapper;
        this.vehicleService = vehicleService;
        this.orderService = orderService;
    }

    public boolean hasTag(String text) {
        return text != null && TAG.matcher(text).find();
    }

    public String stripTag(String text) {
        if (text == null) {
            return null;
        }
        return TAG.matcher(text).replaceAll("").stripTrailing();
    }

    /**
     * Extrai a tag e abre a OS. Resolve o veículo por um dos 2 modos. {@link Optional#empty()}
     * quando: não há tag, JSON inválido, complaint faltando, veículo inválido/cadastro inválido, ou a
     * abertura falha. O {@code contactId} (cliente) vem da conversa.
     */
    public Optional<ServiceOrder> parseAndCreate(UUID companyId, UUID conversationId, UUID contactId,
                                                 String aiResponseText) {
        if (aiResponseText == null) {
            return Optional.empty();
        }
        Matcher m = TAG.matcher(aiResponseText);
        if (!m.find()) {
            return Optional.empty();
        }

        JsonNode root;
        try {
            root = objectMapper.readTree(m.group(1));
        } catch (Exception e) {
            log.warn("oficina: tag <ordem_servico> com JSON inválido p/ conversa {} ({}) — OS não aberta",
                conversationId, e.getMessage());
            return Optional.empty();
        }

        String complaint = root.path("complaint").asText(null);
        String notes = root.path("notes").asText(null);
        if (complaint == null || complaint.isBlank()) {
            log.warn("oficina: tag <ordem_servico> sem complaint p/ conversa {} — OS não aberta", conversationId);
            return Optional.empty();
        }
        UUID mechanicId = parseUuid(root.path("mechanic_id").asText(null));

        UUID vehicleId;
        try {
            vehicleId = resolveVehicle(companyId, contactId, conversationId, root);
        } catch (ResolveVehicleException e) {
            return Optional.empty();
        }
        if (vehicleId == null) {
            return Optional.empty();
        }

        try {
            // Onda 1 (backlog #1): serviços TABELADOS opcionais — só ids do catálogo do tenant
            // viajam na tag; o preço vem do catálogo (a IA segue sem inventar preço).
            java.util.List<ServiceOrderService.CatalogLine> catalogLines = new java.util.ArrayList<>();
            JsonNode servicos = root.path("servicos");
            if (servicos.isArray()) {
                for (JsonNode sv : servicos) {
                    UUID catalogItemId = parseUuid(sv.path("id").asText(null));
                    int qtd = sv.path("qtd").asInt(1);
                    if (catalogItemId != null) {
                        catalogLines.add(new ServiceOrderService.CatalogLine(catalogItemId, qtd));
                    }
                }
            }

            ServiceOrder created = orderService.openWithCatalogItems(companyId, vehicleId, mechanicId,
                conversationId, complaint, null, null, notes, catalogLines);
            log.info("oficina: OS {} aberta p/ conversa {} (veículo {})", created.id(), conversationId, vehicleId);
            return Optional.of(created);
        } catch (ServiceOrderService.VehicleNotFoundException | ServiceOrderService.InactiveVehicleException
                 | ServiceOrderService.MechanicNotFoundException | ServiceOrderService.InactiveMechanicException e) {
            log.warn("oficina: <ordem_servico> com veículo/mecânico inválido ou inativo p/ conversa {} — não aberta",
                conversationId);
            return Optional.empty();
        } catch (RuntimeException e) {
            log.warn("oficina: falha ao abrir OS p/ conversa {} ({}) — mensagem segue sem OS",
                conversationId, e.getMessage());
            return Optional.empty();
        }
    }

    private static class ResolveVehicleException extends RuntimeException {}

    private static UUID parseUuid(String raw) {
        if (raw == null || raw.isBlank() || "null".equalsIgnoreCase(raw)) {
            return null;
        }
        try {
            return UUID.fromString(raw);
        } catch (RuntimeException e) {
            return null;
        }
    }

    /**
     * Modo vehicle_id: valida UUID e usa direto (a abertura revalida que é do tenant + ativo).
     * Modo new_vehicle: cadastra o veículo como sub-entidade do cliente (contato da conversa) e
     * retorna o id criado. Sem contato resolvido → não dá pra cadastrar. plate faltando/dup → empty.
     */
    private UUID resolveVehicle(UUID companyId, UUID contactId, UUID conversationId, JsonNode root) {
        String rawVehicle = root.path("vehicle_id").asText(null);
        if (rawVehicle != null && !rawVehicle.isBlank()) {
            UUID id = parseUuid(rawVehicle);
            if (id == null) {
                log.warn("oficina: <ordem_servico> com vehicle_id inválido p/ conversa {} — não aberta", conversationId);
                throw new ResolveVehicleException();
            }
            return id;
        }

        JsonNode newVehicle = root.path("new_vehicle");
        if (newVehicle.isMissingNode() || !newVehicle.isObject()) {
            log.warn("oficina: <ordem_servico> sem vehicle_id nem new_vehicle p/ conversa {} — não aberta", conversationId);
            throw new ResolveVehicleException();
        }
        if (contactId == null) {
            log.warn("oficina: <ordem_servico> new_vehicle sem cliente resolvido p/ conversa {} — não aberta", conversationId);
            throw new ResolveVehicleException();
        }
        String plate = newVehicle.path("plate").asText(null);
        if (plate == null || plate.isBlank()) {
            log.warn("oficina: <ordem_servico> new_vehicle sem placa p/ conversa {} — não aberta", conversationId);
            throw new ResolveVehicleException();
        }
        String brand = newVehicle.path("brand").asText(null);
        String model = newVehicle.path("model").asText(null);
        Integer year = newVehicle.hasNonNull("year") ? newVehicle.get("year").asInt() : null;
        try {
            OsVehicle created = vehicleService.create(companyId, null, contactId, plate, brand, model,
                year, null, null, null);
            log.info("oficina: veículo {} cadastrado pela IA p/ conversa {} (cliente {})",
                created.id(), conversationId, contactId);
            return created.id();
        } catch (OsVehicleService.PlateTakenException e) {
            log.warn("oficina: <ordem_servico> new_vehicle com placa já cadastrada p/ conversa {} — não aberta", conversationId);
            throw new ResolveVehicleException();
        } catch (OsVehicleService.ContactNotFoundException e) {
            log.warn("oficina: <ordem_servico> new_vehicle com cliente inexistente p/ conversa {} — não aberta", conversationId);
            throw new ResolveVehicleException();
        } catch (RuntimeException e) {
            log.warn("oficina: falha ao cadastrar new_vehicle p/ conversa {} ({}) — não aberta", conversationId, e.getMessage());
            throw new ResolveVehicleException();
        }
    }
}
