package com.firebird_data_extractor.www.controllers;

import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.firebird_data_extractor.www.dto.GarcomDetalhesDTO;
import com.firebird_data_extractor.www.dto.GarcomResumoDTO;
import com.firebird_data_extractor.www.repositories.GarcomRepository;

@RestController
@RequestMapping("/garcons")
public class GarconsController {

    private final GarcomRepository repository;

    public GarconsController(GarcomRepository repo) {
        this.repository = repo;
    }

    private void validarIntervalo(LocalDate dataInicio, LocalDate dataFim) {

        if (dataInicio == null || dataFim == null) {

            throw new IllegalArgumentException(
                """
                Intervalo inválido.

                Os parâmetros 'dataInicio' e 'dataFim' são obrigatórios
                e devem estar no formato ISO (yyyy-MM-dd).

                Exemplo correto:
                /garcons?dataInicio=2025-05-01&dataFim=2025-05-31
                """
            );
        }

        if (dataInicio.isAfter(dataFim)) {

            throw new IllegalArgumentException(
                """
                Intervalo inválido.

                'dataInicio' não pode ser posterior a 'dataFim'.

                Exemplo correto:
                /garcons?dataInicio=2025-05-01&dataFim=2025-05-31
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

    @GetMapping
    public List<GarcomResumoDTO> getRanking(
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataInicio,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataFim
    ) {

        validarIntervalo(dataInicio, dataFim);

        return this.repository.getRankingGarcons(dataInicio, dataFim);
    }

    @GetMapping("/{id}")
    public GarcomDetalhesDTO getGarcomDetalhes(
        @PathVariable Integer id,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataInicio,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataFim
    ) {

        validarIntervalo(dataInicio, dataFim);

        return this.repository.getGarcomDetalhes(id, dataInicio, dataFim);
    }
}
