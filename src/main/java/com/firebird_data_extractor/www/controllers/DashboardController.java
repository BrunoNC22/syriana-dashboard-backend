package com.firebird_data_extractor.www.controllers;

import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.firebird_data_extractor.www.dto.DiaSerieDTO;
import com.firebird_data_extractor.www.dto.FaturamentoPorDiaDTO;
import com.firebird_data_extractor.www.dto.FaturamentoPorHoraDTO;
import com.firebird_data_extractor.www.dto.FaturamentoPorProduto;
import com.firebird_data_extractor.www.dto.InsumoQueMaisCustouDTO;
import com.firebird_data_extractor.www.dto.InsumoDetalhesDTO;
import com.firebird_data_extractor.www.dto.ProdutoDetalhesDTO;
import com.firebird_data_extractor.www.dto.ProdutoMaisVendidoDTO;
import com.firebird_data_extractor.www.repositories.DashboardRepository;

@RestController
@RequestMapping("/dashboard")
public class DashboardController {

    private final DashboardRepository repository;

    public DashboardController(DashboardRepository repo) {
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
                /dashboard/total-vendido?dataInicio=2025-05-01&dataFim=2025-05-31
                """
            );
        }

        if (dataInicio.isAfter(dataFim)) {

            throw new IllegalArgumentException(
                """
                Intervalo inválido.

                'dataInicio' não pode ser posterior a 'dataFim'.

                Exemplo correto:
                /dashboard/total-vendido?dataInicio=2025-05-01&dataFim=2025-05-31
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

    @GetMapping("/total-vendido")
    public double getTotalVendido(
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataInicio,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataFim
    ) {

        validarIntervalo(dataInicio, dataFim);

        return this.repository.getTotalVendido(dataInicio, dataFim);
    }

    @GetMapping("/quantidade-de-pedidos")
    public int quantidadeDePedidos(
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataInicio,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataFim
    ) {

        validarIntervalo(dataInicio, dataFim);

        return this.repository.getQuantidadeDePedidos(dataInicio, dataFim);
    }

    @GetMapping("/produtos-mais-vendidos")
    public List<ProdutoMaisVendidoDTO> getProdutosMaisVendidos(
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataInicio,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataFim
    ) {

        validarIntervalo(dataInicio, dataFim);

        return this.repository.getProdutosMaisVendidos(dataInicio, dataFim);
    }

    @GetMapping("/faturamento-por-dia")
    public List<FaturamentoPorDiaDTO> getFaturamentoPorDia(
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataInicio,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataFim
    ) {

        validarIntervalo(dataInicio, dataFim);

        return this.repository.getFaturamentoPorDia(dataInicio, dataFim);
    }

    @GetMapping("/series-diarias")
    public List<DiaSerieDTO> getSeriesDiarias(
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataInicio,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataFim
    ) {

        validarIntervalo(dataInicio, dataFim);

        return this.repository.getSeriesDiarias(dataInicio, dataFim);
    }

    @GetMapping("/faturamento-por-produto")
    public List<FaturamentoPorProduto> getFaturamentoPorProduto(
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataInicio,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataFim
    ) {

        validarIntervalo(dataInicio, dataFim);

        return this.repository.getFaturamentoPorProduto(dataInicio, dataFim);
    }

    @GetMapping("/horario-de-pico")
    public List<FaturamentoPorHoraDTO> getHorarioDePico(
        @RequestParam int diaSemana,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataInicio,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataFim
    ) {

        if (diaSemana < 0 || diaSemana > 6) {

            throw new IllegalArgumentException(
                """
                diaSemana inválido.

                O parâmetro 'diaSemana' deve estar entre:
                0 = domingo
                1 = segunda
                2 = terça
                3 = quarta
                4 = quinta
                5 = sexta
                6 = sábado

                Exemplo correto:
                /dashboard/horario-de-pico?diaSemana=1&dataInicio=2025-05-01&dataFim=2025-05-31
                """
            );
        }

        validarIntervalo(dataInicio, dataFim);

        return this.repository
            .getMediaFaturamentoPorHoraPorDiaSemana(
                diaSemana,
                dataInicio,
                dataFim
            );
    }

    @GetMapping("/total-gasto-ingredientes")
    public Double getTotalGastoIngredientes(
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataInicio,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataFim
    ) {

        validarIntervalo(dataInicio, dataFim);

        return this.repository.getTotalGastoIngredientes(dataInicio, dataFim);
    }

    @GetMapping("/lucro-total")
    public Double getLucroTotal(
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataInicio,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataFim
    ) {

        validarIntervalo(dataInicio, dataFim);

        return this.repository.getLucroTotal(dataInicio, dataFim);
    }

    @GetMapping("/produto-detalhes")
    public ProdutoDetalhesDTO getProdutoDetalhes(
        @RequestParam Integer id,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataInicio,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataFim
    ) {
        validarIntervalo(dataInicio, dataFim);

        return this.repository.getProdutoDetalhes(id, dataInicio, dataFim);
    }

    @GetMapping("/insumos-que-mais-custaram")
    public List<InsumoQueMaisCustouDTO> getInsumosQueMaisCustaram(
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataInicio,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataFim
    ) {
        validarIntervalo(dataInicio, dataFim);

        return this.repository.getInsumosQueMaisCustaram(dataInicio, dataFim);
    }

    @GetMapping("/insumo-detalhes")
    public InsumoDetalhesDTO getInsumoDetalhes(
        @RequestParam Integer id,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataInicio,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataFim
    ) {
        validarIntervalo(dataInicio, dataFim);

        return this.repository.getInsumoDetalhes(id, dataInicio, dataFim);
    }
}
