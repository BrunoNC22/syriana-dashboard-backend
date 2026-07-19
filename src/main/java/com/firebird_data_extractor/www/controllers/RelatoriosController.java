package com.firebird_data_extractor.www.controllers;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.firebird_data_extractor.www.dto.RelatorioComprasDTO;
import com.firebird_data_extractor.www.services.RelatorioComprasService;

/**
 * Relatório de previsão de compras: {@code GET /relatorios/previsao-compras}.
 * Fino — apenas repassa o horizonte ao {@link RelatorioComprasService}, que
 * valida, compõe e cacheia. Horizonte inválido vira 400 via
 * {@link ApiExceptionHandler}.
 */
@RestController
@RequestMapping("/relatorios")
public class RelatoriosController {

    private final RelatorioComprasService service;

    public RelatoriosController(RelatorioComprasService service) {
        this.service = service;
    }

    @GetMapping("/previsao-compras")
    public RelatorioComprasDTO getPrevisaoCompras(
        @RequestParam(defaultValue = "30d") String horizonte
    ) {
        return this.service.gerar(horizonte);
    }
}
