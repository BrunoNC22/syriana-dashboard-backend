package com.firebird_data_extractor.www.tests;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.firebird_data_extractor.www.controllers.AnalyticsController;
import com.firebird_data_extractor.www.dto.InsumoCapitalParadoDTO;
import com.firebird_data_extractor.www.dto.InsumoForaDoPadraoDTO;
import com.firebird_data_extractor.www.dto.InsumoPoucoUtilizadoDTO;
import com.firebird_data_extractor.www.dto.InsumoQuedaConsumoDTO;
import com.firebird_data_extractor.www.dto.InsumosBaixoUsoDTO;
import com.firebird_data_extractor.www.dto.ProdutoAbandonadoDTO;
import com.firebird_data_extractor.www.dto.ProdutoQuedaDTO;
import com.firebird_data_extractor.www.dto.ProdutoSemVendaDTO;
import com.firebird_data_extractor.www.dto.ProdutoTendenciaQuedaDTO;
import com.firebird_data_extractor.www.dto.ProdutosBaixoDesempenhoDTO;
import com.firebird_data_extractor.www.services.AnalyticsService;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Testes de contrato do endpoint /analytics/produtos-baixo-desempenho.
 * Ver nota sobre o perfil `agent-test` em DashboardControllerTest — estes
 * testes exercitam a serialização Jackson dos DTOs de baixo desempenho.
 */
@WebMvcTest(AnalyticsController.class)
class AnalyticsControllerTest {

    private static final String INTERVALO = "dataInicio=2025-05-01&dataFim=2025-05-31";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AnalyticsService service;

    @Test
    void produtosBaixoDesempenhoSerializaAsQuatroListas() throws Exception {
        when(service.analisarProdutos(any(LocalDate.class), any(LocalDate.class)))
            .thenReturn(new ProdutosBaixoDesempenhoDTO(
                List.of(new ProdutoSemVendaDTO(
                    "7", "Homus", "Entradas", LocalDate.of(2025, 3, 10), false)),
                List.of(new ProdutoQuedaDTO(
                    "8", "Kibe cru", "Entradas", 4.0, 40.0, -90.0)),
                List.of(new ProdutoTendenciaQuedaDTO(
                    "9", "Baba Ganoush", "Entradas", -2.5, 30.0, 4)),
                List.of(new ProdutoAbandonadoDTO(
                    "10", "Muhamara", "Entradas", LocalDate.of(2025, 1, 5), 4, 120.0))
            ));

        mockMvc.perform(get("/analytics/produtos-baixo-desempenho?" + INTERVALO))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.semVendas[0].id").value("7"))
            .andExpect(jsonPath("$.semVendas[0].ultimaVenda").value("2025-03-10"))
            .andExpect(jsonPath("$.semVendas[0].novo").value(false))
            .andExpect(jsonPath("$.maioresQuedas[0].id").value("8"))
            .andExpect(jsonPath("$.maioresQuedas[0].variacaoPercentual").value(-90.0))
            .andExpect(jsonPath("$.tendenciaQueda[0].slopePorSemana").value(-2.5))
            .andExpect(jsonPath("$.tendenciaQueda[0].semanas").value(4))
            .andExpect(jsonPath("$.abandonados[0].id").value("10"))
            .andExpect(jsonPath("$.abandonados[0].mesesComVenda").value(4))
            .andExpect(jsonPath("$.abandonados[0].quantidadeUltimos6Meses").value(120.0));
    }

    @Test
    void listasVaziasSerializamComoArraysVazios() throws Exception {
        when(service.analisarProdutos(any(LocalDate.class), any(LocalDate.class)))
            .thenReturn(new ProdutosBaixoDesempenhoDTO(
                List.of(), List.of(), List.of(), List.of()));

        mockMvc.perform(get("/analytics/produtos-baixo-desempenho?" + INTERVALO))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.semVendas").isEmpty())
            .andExpect(jsonPath("$.maioresQuedas").isEmpty())
            .andExpect(jsonPath("$.tendenciaQueda").isEmpty())
            .andExpect(jsonPath("$.abandonados").isEmpty());
    }

    @Test
    void intervaloInvertidoRetorna400ComMensagem() throws Exception {
        mockMvc.perform(get(
                "/analytics/produtos-baixo-desempenho?dataInicio=2025-05-31&dataFim=2025-05-01"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.mensagem").exists());
    }

    @Test
    void parametroAusenteRetorna400() throws Exception {
        mockMvc.perform(get("/analytics/produtos-baixo-desempenho?dataInicio=2025-05-01"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void insumosBaixoUsoSerializaAsQuatroListas() throws Exception {
        when(service.analisarInsumos(any(LocalDate.class), any(LocalDate.class)))
            .thenReturn(new InsumosBaixoUsoDTO(
                List.of(new InsumoPoucoUtilizadoDTO(
                    "3", "Grão de bico", "kg", 0.0, 0.0, LocalDate.of(2025, 2, 1), false)),
                List.of(new InsumoCapitalParadoDTO(
                    "4", "Tahine", "kg", 12.5, 340.0, LocalDate.of(2025, 1, 20))),
                List.of(new InsumoQuedaConsumoDTO(
                    "5", "Hortelã", "mç", 3.0, 30.0, -90.0)),
                List.of(new InsumoForaDoPadraoDTO(
                    "6", "Azeite", "L", 20.0, 8.0, 4.0, 3.0))
            ));

        mockMvc.perform(get("/analytics/insumos-baixo-uso?" + INTERVALO))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.poucoUtilizados[0].id").value("3"))
            .andExpect(jsonPath("$.poucoUtilizados[0].quantidadeConsumida").value(0.0))
            .andExpect(jsonPath("$.poucoUtilizados[0].ultimoUso").value("2025-02-01"))
            .andExpect(jsonPath("$.capitalParado[0].id").value("4"))
            .andExpect(jsonPath("$.capitalParado[0].custoEstoqueParado").value(340.0))
            .andExpect(jsonPath("$.quedaConsumo[0].id").value("5"))
            .andExpect(jsonPath("$.quedaConsumo[0].variacaoPercentual").value(-90.0))
            .andExpect(jsonPath("$.foraDoPadrao[0].id").value("6"))
            .andExpect(jsonPath("$.foraDoPadrao[0].zScore").value(3.0));
    }

    @Test
    void insumosBaixoUsoListasVaziasSerializamComoArraysVazios() throws Exception {
        when(service.analisarInsumos(any(LocalDate.class), any(LocalDate.class)))
            .thenReturn(new InsumosBaixoUsoDTO(
                List.of(), List.of(), List.of(), List.of()));

        mockMvc.perform(get("/analytics/insumos-baixo-uso?" + INTERVALO))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.poucoUtilizados").isEmpty())
            .andExpect(jsonPath("$.capitalParado").isEmpty())
            .andExpect(jsonPath("$.quedaConsumo").isEmpty())
            .andExpect(jsonPath("$.foraDoPadrao").isEmpty());
    }

    @Test
    void insumosBaixoUsoIntervaloInvertidoRetorna400() throws Exception {
        mockMvc.perform(get(
                "/analytics/insumos-baixo-uso?dataInicio=2025-05-31&dataFim=2025-05-01"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.mensagem").exists());
    }
}
