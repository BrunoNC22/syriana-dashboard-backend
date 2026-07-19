package com.firebird_data_extractor.www.tests;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.firebird_data_extractor.www.controllers.RelatoriosController;
import com.firebird_data_extractor.www.dto.FeriadoDTO;
import com.firebird_data_extractor.www.dto.LinhaCompraDTO;
import com.firebird_data_extractor.www.dto.PanoramaCompraDTO;
import com.firebird_data_extractor.www.dto.RelatorioComprasDTO;
import com.firebird_data_extractor.www.dto.ValorPrevistoDTO;
import com.firebird_data_extractor.www.services.RelatorioComprasService;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Testes de contrato do endpoint /relatorios/previsao-compras. Ver nota sobre o
 * perfil `agent-test` em DashboardControllerTest — estes exercitam a
 * serialização Jackson dos DTOs do relatório de compras (RelatorioComprasDTO,
 * PanoramaCompraDTO, ValorPrevistoDTO, LinhaCompraDTO, FeriadoDTO).
 */
@WebMvcTest(RelatoriosController.class)
class RelatoriosControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RelatorioComprasService service;

    @Test
    void relatorioSerializaDTO() throws Exception {
        when(service.gerar(any())).thenReturn(new RelatorioComprasDTO(
            LocalDate.of(2026, 7, 19),
            LocalDate.of(2026, 7, 19),
            LocalDate.of(2026, 8, 17),
            30,
            "Consumo dos últimos 90 dias; método: HOLT_WINTERS (MAPE 12.3%).",
            new PanoramaCompraDTO(
                new ValorPrevistoDTO(90000.0, 82000.0, 98000.0, "HOLT_WINTERS", 12.3),
                new ValorPrevistoDTO(1200.0, 1100.0, 1300.0, "HOLT_WINTERS", 9.8),
                List.of(new FeriadoDTO(LocalDate.of(2026, 8, 9), "Dia dos Pais", "COMEMORATIVA"))
            ),
            List.of(new LinhaCompraDTO(
                "42", "Grão de bico", "kg",
                120.0, 900.0,
                15.0, true,
                8.0, "normal",
                113.0, 847.5,
                "alto", 3.7, false,
                "Consumo recente: 360 kg em 90 dias. → Comprar 113 kg."
            )),
            List.of("Custos estimados pelo preço de custo atual — o sistema legado não registra o valor de compra.")
        ));

        mockMvc.perform(get("/relatorios/previsao-compras?horizonte=30d"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.geradoEm").value("2026-07-19"))
            .andExpect(jsonPath("$.horizonteFim").value("2026-08-17"))
            .andExpect(jsonPath("$.horizonteDias").value(30))
            .andExpect(jsonPath("$.panorama.faturamento.total").value(90000.0))
            .andExpect(jsonPath("$.panorama.faturamento.metodo").value("HOLT_WINTERS"))
            .andExpect(jsonPath("$.panorama.pedidos.total").value(1200.0))
            .andExpect(jsonPath("$.panorama.datasEspeciais[0].nome").value("Dia dos Pais"))
            .andExpect(jsonPath("$.compras[0].id").value("42"))
            .andExpect(jsonPath("$.compras[0].siglaUnidadeDeMedida").value("kg"))
            .andExpect(jsonPath("$.compras[0].estoqueConfiavel").value(true))
            .andExpect(jsonPath("$.compras[0].compraRecomendada").value(113.0))
            .andExpect(jsonPath("$.compras[0].riscoRuptura").value("alto"))
            .andExpect(jsonPath("$.compras[0].diasCobertura").value(3.7))
            .andExpect(jsonPath("$.compras[0].excesso").value(false))
            .andExpect(jsonPath("$.avisos[0]").exists());
    }

    @Test
    void relatorioVazioSerializaComListasVazias() throws Exception {
        when(service.gerar(any())).thenReturn(new RelatorioComprasDTO(
            LocalDate.of(2026, 7, 19),
            LocalDate.of(2026, 7, 19),
            LocalDate.of(2026, 8, 17),
            30,
            "Sem histórico de consumo recente.",
            new PanoramaCompraDTO(
                new ValorPrevistoDTO(0.0, 0.0, 0.0, "NAIVE_SAZONAL", null),
                new ValorPrevistoDTO(0.0, 0.0, 0.0, "NAIVE_SAZONAL", null),
                List.of()
            ),
            List.of(),
            List.of("Sem histórico de consumo de insumos nos últimos 90 dias — não foi possível projetar compras.")
        ));

        mockMvc.perform(get("/relatorios/previsao-compras"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.compras").isEmpty())
            .andExpect(jsonPath("$.panorama.datasEspeciais").isEmpty())
            .andExpect(jsonPath("$.panorama.faturamento.mape").value(nullValue()))
            .andExpect(jsonPath("$.avisos[0]").exists());
    }

    @Test
    void horizonteInvalidoRetorna400ComMensagem() throws Exception {
        when(service.gerar(any()))
            .thenThrow(new IllegalArgumentException("Parâmetro 'horizonte' inválido."));

        mockMvc.perform(get("/relatorios/previsao-compras?horizonte=abc"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.mensagem").exists());
    }
}
