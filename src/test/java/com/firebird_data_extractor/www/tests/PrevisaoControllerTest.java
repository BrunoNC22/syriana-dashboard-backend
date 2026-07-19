package com.firebird_data_extractor.www.tests;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.firebird_data_extractor.www.controllers.PrevisaoController;
import com.firebird_data_extractor.www.dto.FeriadoDTO;
import com.firebird_data_extractor.www.dto.PontoPrevisaoDTO;
import com.firebird_data_extractor.www.dto.PrevisaoDTO;
import com.firebird_data_extractor.www.services.PrevisaoService;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Testes de contrato do endpoint /analytics/previsao. Ver nota sobre o perfil
 * `agent-test` em DashboardControllerTest — estes exercitam a serialização
 * Jackson dos DTOs de previsão (PrevisaoDTO, PontoPrevisaoDTO, FeriadoDTO).
 */
@WebMvcTest(PrevisaoController.class)
class PrevisaoControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PrevisaoService service;

    @Test
    void previsaoSerializaDTO() throws Exception {
        when(service.prever(any(), any())).thenReturn(new PrevisaoDTO(
            "faturamento",
            "HOLT_WINTERS",
            12.34,
            30,
            728,
            null,
            List.of(
                new PontoPrevisaoDTO(LocalDate.of(2026, 5, 10), 2500.0, 2000.0, 3000.0, true),
                new PontoPrevisaoDTO(LocalDate.of(2026, 5, 11), 900.0, 700.0, 1100.0, false)
            ),
            List.of(new FeriadoDTO(LocalDate.of(2026, 5, 10), "Dia das Mães", "COMEMORATIVA"))
        ));

        mockMvc.perform(get("/analytics/previsao?metrica=faturamento&horizonte=30d"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.metrica").value("faturamento"))
            .andExpect(jsonPath("$.metodo").value("HOLT_WINTERS"))
            .andExpect(jsonPath("$.mape").value(12.34))
            .andExpect(jsonPath("$.horizonteDias").value(30))
            .andExpect(jsonPath("$.diasHistorico").value(728))
            .andExpect(jsonPath("$.avisoBaixaConfianca").value(nullValue()))
            .andExpect(jsonPath("$.pontos[0].data").value("2026-05-10"))
            .andExpect(jsonPath("$.pontos[0].valor").value(2500.0))
            .andExpect(jsonPath("$.pontos[0].limiteInferior").value(2000.0))
            .andExpect(jsonPath("$.pontos[0].feriado").value(true))
            .andExpect(jsonPath("$.pontos[1].feriado").value(false))
            .andExpect(jsonPath("$.feriadosNoHorizonte[0].nome").value("Dia das Mães"))
            .andExpect(jsonPath("$.feriadosNoHorizonte[0].categoria").value("COMEMORATIVA"));
    }

    @Test
    void mapeNuloOmitidoOuNulo() throws Exception {
        when(service.prever(any(), any())).thenReturn(new PrevisaoDTO(
            "pedidos", "NAIVE_SAZONAL", null, 5, 5,
            "Histórico curto (5 dias): padrão sazonal pouco estabelecido.",
            List.of(new PontoPrevisaoDTO(LocalDate.of(2026, 7, 20), 3.0, 0.0, 6.0, false)),
            List.of()
        ));

        mockMvc.perform(get("/analytics/previsao?metrica=pedidos&horizonte=5d"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.metodo").value("NAIVE_SAZONAL"))
            .andExpect(jsonPath("$.mape").value(nullValue()))
            .andExpect(jsonPath("$.avisoBaixaConfianca").exists())
            .andExpect(jsonPath("$.feriadosNoHorizonte").isEmpty());
    }

    @Test
    void metricaInvalidaRetorna400ComMensagem() throws Exception {
        when(service.prever(any(), any()))
            .thenThrow(new IllegalArgumentException("Parâmetro 'metrica' inválido."));

        mockMvc.perform(get("/analytics/previsao?metrica=lucro&horizonte=30d"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.mensagem").exists());
    }

    @Test
    void metricaAusenteRetorna400() throws Exception {
        mockMvc.perform(get("/analytics/previsao?horizonte=30d"))
            .andExpect(status().isBadRequest());
    }
}
