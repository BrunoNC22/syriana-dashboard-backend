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

import com.firebird_data_extractor.www.dto.FaturamentoPorDiaDTO;
import com.firebird_data_extractor.www.dto.FaturamentoPorHoraDTO;
import com.firebird_data_extractor.www.dto.FaturamentoPorProduto;
import com.firebird_data_extractor.www.dto.InsumoDetalhesDTO;
import com.firebird_data_extractor.www.dto.InsumoDoProdutoDTO;
import com.firebird_data_extractor.www.dto.InsumoQueMaisCustouDTO;
import com.firebird_data_extractor.www.dto.ProdutoDetalhesDTO;
import com.firebird_data_extractor.www.dto.ProdutoMaisVendidoDTO;
import com.firebird_data_extractor.www.dto.ProdutoQueUsaInsumoDTO;
import com.firebird_data_extractor.www.repositories.DashboardRepository;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Testes de contrato dos endpoints /dashboard/**.
 *
 * Além de validar o contrato JSON, estes testes exercitam a serialização
 * Jackson dos DTOs — quando executados com o perfil Maven `agent-test`
 * (mvnw test -Pagent-test), o native-image-agent grava as reflections
 * necessárias em META-INF/native-image sem precisar subir a aplicação.
 */
@WebMvcTest(DashboardController.class)
class DashboardControllerTest {

    private static final String INTERVALO = "dataInicio=2025-05-01&dataFim=2025-05-31";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DashboardRepository repository;

    @Test
    void totalVendidoRetornaEscalar() throws Exception {
        when(repository.getTotalVendido(any(LocalDate.class), any(LocalDate.class)))
            .thenReturn(1234.56);

        mockMvc.perform(get("/dashboard/total-vendido?" + INTERVALO))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").value(1234.56));
    }

    @Test
    void quantidadeDePedidosRetornaEscalar() throws Exception {
        when(repository.getQuantidadeDePedidos(any(LocalDate.class), any(LocalDate.class)))
            .thenReturn(42);

        mockMvc.perform(get("/dashboard/quantidade-de-pedidos?" + INTERVALO))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").value(42));
    }

    @Test
    void produtosMaisVendidosSerializaDTO() throws Exception {
        when(repository.getProdutosMaisVendidos(any(LocalDate.class), any(LocalDate.class)))
            .thenReturn(List.of(new ProdutoMaisVendidoDTO("7", "Homus", 120.0)));

        mockMvc.perform(get("/dashboard/produtos-mais-vendidos?" + INTERVALO))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].id").value("7"))
            .andExpect(jsonPath("$[0].nomeProduto").value("Homus"))
            .andExpect(jsonPath("$[0].totalVendido").value(120.0));
    }

    @Test
    void faturamentoPorDiaSerializaLocalDate() throws Exception {
        when(repository.getFaturamentoPorDia(any(LocalDate.class), any(LocalDate.class)))
            .thenReturn(List.of(new FaturamentoPorDiaDTO(LocalDate.of(2025, 5, 1), 350.0)));

        mockMvc.perform(get("/dashboard/faturamento-por-dia?" + INTERVALO))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].dia").value("2025-05-01"))
            .andExpect(jsonPath("$[0].faturamento").value(350.0));
    }

    @Test
    void faturamentoPorProdutoSerializaDTO() throws Exception {
        when(repository.getFaturamentoPorProduto(any(LocalDate.class), any(LocalDate.class)))
            .thenReturn(List.of(new FaturamentoPorProduto("7", "Homus", 990.5)));

        mockMvc.perform(get("/dashboard/faturamento-por-produto?" + INTERVALO))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].id").value("7"))
            .andExpect(jsonPath("$[0].nome").value("Homus"))
            .andExpect(jsonPath("$[0].faturamento").value(990.5));
    }

    @Test
    void horarioDePicoSerializaDTO() throws Exception {
        when(repository.getMediaFaturamentoPorHoraPorDiaSemana(
                anyInt(), any(LocalDate.class), any(LocalDate.class)))
            .thenReturn(List.of(new FaturamentoPorHoraDTO("19:00", 210.0)));

        mockMvc.perform(get("/dashboard/horario-de-pico?diaSemana=6&" + INTERVALO))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].hora").value("19:00"))
            .andExpect(jsonPath("$[0].mediaFaturamento").value(210.0));
    }

    @Test
    void horarioDePicoRejeitaDiaSemanaForaDoIntervalo() throws Exception {
        mockMvc.perform(get("/dashboard/horario-de-pico?diaSemana=7&" + INTERVALO))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.mensagem").exists());
    }

    @Test
    void intervaloInvertidoRetorna400ComMensagem() throws Exception {
        mockMvc.perform(get("/dashboard/total-vendido?dataInicio=2025-05-31&dataFim=2025-05-01"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.mensagem").exists());
    }

    @Test
    void totalGastoIngredientesRetornaEscalar() throws Exception {
        when(repository.getTotalGastoIngredientes(any(LocalDate.class), any(LocalDate.class)))
            .thenReturn(222.2);

        mockMvc.perform(get("/dashboard/total-gasto-ingredientes?" + INTERVALO))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").value(222.2));
    }

    @Test
    void lucroTotalRetornaEscalar() throws Exception {
        when(repository.getLucroTotal(any(LocalDate.class), any(LocalDate.class)))
            .thenReturn(100.0);

        mockMvc.perform(get("/dashboard/lucro-total?" + INTERVALO))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").value(100.0));
    }

    @Test
    void produtoDetalhesSerializaDTOAninhado() throws Exception {
        when(repository.getProdutoDetalhes(anyInt(), any(LocalDate.class), any(LocalDate.class)))
            .thenReturn(new ProdutoDetalhesDTO(
                "Homus", 12.0, 480.0, 120.0, 360.0, 75.0,
                List.of(new InsumoDoProdutoDTO("Grão de bico", "KG", 3.5, 42.0))
            ));

        mockMvc.perform(get("/dashboard/produto-detalhes?id=7&" + INTERVALO))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.nomeProduto").value("Homus"))
            .andExpect(jsonPath("$.margemLucro").value(75.0))
            .andExpect(jsonPath("$.insumos[0].nome").value("Grão de bico"))
            .andExpect(jsonPath("$.insumos[0].siglaUnidadeDeMedida").value("KG"));
    }

    @Test
    void insumosQueMaisCustaramSerializaDTO() throws Exception {
        when(repository.getInsumosQueMaisCustaram(any(LocalDate.class), any(LocalDate.class)))
            .thenReturn(List.of(new InsumoQueMaisCustouDTO(
                "31", "Grão de bico", "KG", 25.0, 12.0, 300.0)));

        mockMvc.perform(get("/dashboard/insumos-que-mais-custaram?" + INTERVALO))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].id").value("31"))
            .andExpect(jsonPath("$[0].nome").value("Grão de bico"))
            .andExpect(jsonPath("$[0].valorTotal").value(300.0));
    }

    @Test
    void insumoDetalhesSerializaDTOAninhado() throws Exception {
        when(repository.getInsumoDetalhes(anyInt(), any(LocalDate.class), any(LocalDate.class)))
            .thenReturn(new InsumoDetalhesDTO(
                "Grão de bico", "KG", 25.0, 300.0, 12.0, 3, 87,
                List.of(new ProdutoQueUsaInsumoDTO("7", "Homus", 18.0, 216.0))
            ));

        mockMvc.perform(get("/dashboard/insumo-detalhes?id=31&" + INTERVALO))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.nome").value("Grão de bico"))
            .andExpect(jsonPath("$.quantidadeTotal").value(25.0))
            .andExpect(jsonPath("$.produtos[0].nomeProduto").value("Homus"));
    }

    @Test
    void parametroAusenteRetorna400() throws Exception {
        mockMvc.perform(get("/dashboard/total-vendido?dataInicio=2025-05-01"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void dataMalFormadaRetorna400() throws Exception {
        mockMvc.perform(get("/dashboard/total-vendido?dataInicio=01/05/2025&dataFim=2025-05-31"))
            .andExpect(status().isBadRequest());
    }
}
