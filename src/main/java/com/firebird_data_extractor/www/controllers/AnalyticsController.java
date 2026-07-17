package com.firebird_data_extractor.www.controllers;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.firebird_data_extractor.www.dto.InsumosBaixoUsoDTO;
import com.firebird_data_extractor.www.dto.ProdutosBaixoDesempenhoDTO;
import com.firebird_data_extractor.www.services.AnalyticsService;

@RestController
@RequestMapping("/analytics")
public class AnalyticsController {

    private final AnalyticsService service;

    public AnalyticsController(AnalyticsService service) {
        this.service = service;
    }

    private void validarIntervalo(LocalDate dataInicio, LocalDate dataFim) {

        if (dataInicio == null || dataFim == null) {
            throw new IllegalArgumentException(
                """
                Intervalo inválido.

                Os parâmetros 'dataInicio' e 'dataFim' são obrigatórios
                e devem estar no formato ISO (yyyy-MM-dd).

                Exemplo correto:
                /analytics/produtos-baixo-desempenho?dataInicio=2025-05-01&dataFim=2025-05-31
                """
            );
        }

        if (dataInicio.isAfter(dataFim)) {
            throw new IllegalArgumentException(
                """
                Intervalo inválido.

                'dataInicio' não pode ser posterior a 'dataFim'.
                """
            );
        }

        LocalDate limiteInferior = LocalDate.of(2000, 1, 1);
        LocalDate limiteSuperior = LocalDate.now().plusYears(1);

        if (dataInicio.isBefore(limiteInferior) || dataFim.isAfter(limiteSuperior)) {
            throw new IllegalArgumentException(
                """
                Intervalo fora do limite permitido.

                As datas devem estar entre %s e %s.
                """
                .formatted(limiteInferior, limiteSuperior)
            );
        }
    }

    @GetMapping("/produtos-baixo-desempenho")
    public ProdutosBaixoDesempenhoDTO getProdutosBaixoDesempenho(
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataInicio,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataFim
    ) {
        validarIntervalo(dataInicio, dataFim);

        return this.service.analisarProdutos(dataInicio, dataFim);
    }

    @GetMapping("/insumos-baixo-uso")
    public InsumosBaixoUsoDTO getInsumosBaixoUso(
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataInicio,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataFim
    ) {
        validarIntervalo(dataInicio, dataFim);

        return this.service.analisarInsumos(dataInicio, dataFim);
    }
}
