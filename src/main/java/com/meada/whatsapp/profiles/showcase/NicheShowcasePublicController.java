package com.meada.whatsapp.profiles.showcase;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Endpoint PÚBLICO da vitrine de nichos (sem auth — rota /public/). O bloco niches_grid do CMS
 * consome aqui: a home pede {@code ?featured=true} (só destaques), a página /produtos pede todos.
 * Devolve os dados do nicho pra montar o card (o frontend resolve a cor pelo paletteId).
 */
@RestController
public class NicheShowcasePublicController {

    private final NicheShowcaseService service;

    public NicheShowcasePublicController(NicheShowcaseService service) {
        this.service = service;
    }

    @GetMapping("/public/niches")
    public List<NicheShowcaseService.PublicCard> niches(
            @RequestParam(name = "featured", defaultValue = "false") boolean featured) {
        return service.publicList(featured);
    }
}
