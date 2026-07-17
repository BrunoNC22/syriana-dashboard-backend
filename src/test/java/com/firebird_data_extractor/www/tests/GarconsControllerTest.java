package com.firebird_data_extractor.www.tests;

import com.firebird_data_extractor.www.controllers.DashboardController;
import com.firebird_data_extractor.www.controllers.GarconsController;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.firebird_data_extractor.www.dto.GarcomDetalhesDTO;
import com.firebird_data_extractor.www.dto.GarcomResumoDTO;
import com.firebird_data_extractor.www.dto.ProdutoRankingDTO;
import com.firebird_data_extractor.www.repositories.GarcomRepository;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Testes de contrato dos endpoints /garcons/**.
 * Ver nota sobre o perfil `agent-test` em DashboardControllerTest.
 */
@WebMvcTest(GarconsController.class)
class GarconsControllerTest {

    private static final String INTERVALO = "dataInicio=2025-05-01&dataFim=2025-05-31";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private GarcomRepository repository;

    @Test
    void rankingSerializaDTO() throws Exception {
        when(repository.getRankingGarcons(any(LocalDate.class), any(LocalDate.class)))
            .thenReturn(List.of(new GarcomResumoDTO(
                5, "João", 320.5, 3205.0, 900.0, 2305.0, 48, 210)));

        mockMvc.perform(get("/garcons?" + INTERVALO))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].id").value(5))
            .andExpect(jsonPath("$[0].nome").value("João"))
            .andExpect(jsonPath("$[0].totalTaxaServico").value(320.5))
            .andExpect(jsonPath("$[0].comandas").value(48));
    }

    @Test
    void detalhesSerializaDTOAninhado() throws Exception {
        when(repository.getGarcomDetalhes(anyInt(), any(LocalDate.class), any(LocalDate.class)))
            .thenReturn(new GarcomDetalhesDTO(
                5, "João", 320.5, 3205.0, 900.0, 2305.0, 48, 210,
                List.of(new ProdutoRankingDTO("Homus", "Entradas", 32.0))
            ));

        mockMvc.perform(get("/garcons/5?" + INTERVALO))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.nome").value("João"))
            .andExpect(jsonPath("$.produtosMaisVendidos[0].nomeProduto").value("Homus"))
            .andExpect(jsonPath("$.produtosMaisVendidos[0].etiqueta").value("Entradas"));
    }

    @Test
    void parametroAusenteRetorna400() throws Exception {
        mockMvc.perform(get("/garcons?dataInicio=2025-05-01"))
            .andExpect(status().isBadRequest());
    }
}
