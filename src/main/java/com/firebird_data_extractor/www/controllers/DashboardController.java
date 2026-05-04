package com.firebird_data_extractor.www.controllers;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.firebird_data_extractor.www.repositories.DashboardRepository;
import com.firebird_data_extractor.www.dto.FaturamentoPorDiaDTO;
import com.firebird_data_extractor.www.dto.FaturamentoPorHoraDTO;
import com.firebird_data_extractor.www.dto.FaturamentoPorProduto;
import com.firebird_data_extractor.www.dto.ProdutoMaisVendidoDTO;


@RestController
@RequestMapping("/dashboard")
public class DashboardController {

    private final DashboardRepository repository;

    public DashboardController(DashboardRepository repo) {
        this.repository = repo;
    }
    
    @GetMapping("/total-vendido")
    public double getTotalVendido() {
        return this.repository.getTotalVendido();
    }
    
    @GetMapping("/quantidade-de-pedidos")
    public int quantidadeDePedidos() {
        return this.repository.getQuantidadeDePedidos();
    }

    @GetMapping("/produtos-mais-vendidos")
    public List<ProdutoMaisVendidoDTO> getProdutosMaisVendidos() {
        return this.repository.getProdutosMaisVendidos();
    }

    @GetMapping("/faturamento-por-dia")
    public List<FaturamentoPorDiaDTO> getFaturamentoPorDia() {
        return this.repository.getFaturamentoPorDia();
    }

    @GetMapping("/faturamento-por-produto")
    public List<FaturamentoPorProduto> getFaturamentoPorProduto() {
        return this.repository.getFaturamentoPorProduto();
    }

    @GetMapping("/horario-de-pico")
    public List<FaturamentoPorHoraDTO> getHorarioDePico(@RequestParam int diaSemana) {
        if (diaSemana < 0 || diaSemana > 6) {
            throw new IllegalArgumentException("diaSemana deve estar entre 0 (domingo) e 6 (sábado)");
        }

        return this.repository.getMediaFaturamentoPorHoraPorDiaSemana(diaSemana);
    }
}
