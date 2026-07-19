package com.firebird_data_extractor.www.controllers;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.firebird_data_extractor.www.dto.PrevisaoDTO;
import com.firebird_data_extractor.www.services.PrevisaoService;

/**
 * Previsão de faturamento/pedidos: {@code GET /analytics/previsao}. Fino —
 * apenas repassa os parâmetros ao {@link PrevisaoService}, que valida, computa
 * e cacheia. Parâmetros inválidos viram 400 via {@link ApiExceptionHandler}.
 */
@RestController
@RequestMapping("/analytics")
public class PrevisaoController {

    private final PrevisaoService service;

    public PrevisaoController(PrevisaoService service) {
        this.service = service;
    }

    @GetMapping("/previsao")
    public PrevisaoDTO getPrevisao(
        @RequestParam String metrica,
        @RequestParam(defaultValue = "30d") String horizonte
    ) {
        return this.service.prever(metrica, horizonte);
    }
}
