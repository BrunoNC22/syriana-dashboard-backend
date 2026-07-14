package com.firebird_data_extractor.www.controllers;

import java.time.LocalDate;
import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.firebird_data_extractor.www.dto.FaturamentoPorDiaDTO;
import com.firebird_data_extractor.www.dto.FaturamentoPorHoraDTO;
import com.firebird_data_extractor.www.dto.FaturamentoPorProduto;
import com.firebird_data_extractor.www.dto.InsumoQueMaisCustouDTO;
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

    private void validarAnoEMes(int ano, Integer mes) {

        int anoAtual = LocalDate.now().getYear();

        if (ano < 2000 || ano > anoAtual + 1) {

            throw new IllegalArgumentException(
                """
                Ano inválido.

                O parâmetro 'ano' deve estar entre 2000 e %d.

                Exemplos corretos:
                /dashboard/total-vendido?ano=2025
                /dashboard/total-vendido?ano=2025&mes=5
                """
                .formatted(anoAtual + 1)
            );
        }

        if (mes != null && (mes < 1 || mes > 12)) {

            throw new IllegalArgumentException(
                """
                Mês inválido.

                O parâmetro 'mes' deve estar entre 1 e 12.

                Exemplos corretos:
                /dashboard/total-vendido?ano=2025&mes=5
                /dashboard/faturamento-por-dia?ano=2024&mes=12
                """
            );
        }
    }

    @GetMapping("/total-vendido")
    public double getTotalVendido(
        @RequestParam int ano,
        @RequestParam(required = false) Integer mes
    ) {

        validarAnoEMes(ano, mes);

        return this.repository.getTotalVendido(ano, mes);
    }

    @GetMapping("/quantidade-de-pedidos")
    public int quantidadeDePedidos(
        @RequestParam int ano,
        @RequestParam(required = false) Integer mes
    ) {

        validarAnoEMes(ano, mes);

        return this.repository.getQuantidadeDePedidos(
            ano,
            mes
        );
    }

    @GetMapping("/produtos-mais-vendidos")
    public List<ProdutoMaisVendidoDTO> getProdutosMaisVendidos(
        @RequestParam int ano,
        @RequestParam(required = false) Integer mes
    ) {

        validarAnoEMes(ano, mes);

        return this.repository.getProdutosMaisVendidos(
            ano,
            mes
        );
    }

    @GetMapping("/faturamento-por-dia")
    public List<FaturamentoPorDiaDTO> getFaturamentoPorDia(
        @RequestParam int ano,
        @RequestParam(required = false) Integer mes
    ) {

        validarAnoEMes(ano, mes);

        return this.repository.getFaturamentoPorDia(
            ano,
            mes
        );
    }

    @GetMapping("/faturamento-por-produto")
    public List<FaturamentoPorProduto> getFaturamentoPorProduto(
        @RequestParam int ano,
        @RequestParam(required = false) Integer mes
    ) {

        validarAnoEMes(ano, mes);

        return this.repository.getFaturamentoPorProduto(
            ano,
            mes
        );
    }

    @GetMapping("/horario-de-pico")
    public List<FaturamentoPorHoraDTO> getHorarioDePico(
        @RequestParam int diaSemana,
        @RequestParam int ano,
        @RequestParam(required = false) Integer mes
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
                /dashboard/horario-de-pico?diaSemana=1&ano=2025&mes=5
                """
            );
        }

        validarAnoEMes(ano, mes);

        return this.repository
            .getMediaFaturamentoPorHoraPorDiaSemana(
                diaSemana,
                ano,
                mes
            );
    }

    @GetMapping("/total-gasto-ingredientes")
    public Double getTotalGastoIngredientes(
        @RequestParam int ano,
        @RequestParam(required = false) Integer mes
    ) {
        return repository.getTotalGastoIngredientes(
            ano,
            mes
        );
    }

    @GetMapping("/produto-detalhes")
    public ProdutoDetalhesDTO getProdutoDetalhes(
        @RequestParam Integer id,
        @RequestParam int ano,
        @RequestParam(required = false) Integer mes
    ) {
        validarAnoEMes(ano, mes);
        return this.repository.getProdutoDetalhes(id, ano, mes);
    }

    @GetMapping("/insumos-que-mais-custaram")
    public List<InsumoQueMaisCustouDTO> getInsumosQueMaisCustaram(
        @RequestParam int ano,
        @RequestParam(required = false) Integer mes
    ) {
        validarAnoEMes(ano, mes);

        return this.repository.getInsumosQueMaisCustaram(ano, mes);
    }
}