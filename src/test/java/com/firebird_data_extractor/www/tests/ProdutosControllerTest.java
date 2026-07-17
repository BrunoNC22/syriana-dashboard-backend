package com.firebird_data_extractor.www.tests;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.firebird_data_extractor.www.controllers.ProdutosController;
import com.firebird_data_extractor.www.dto.ProdutoCatalogoDTO;
import com.firebird_data_extractor.www.repositories.ProdutoRepository;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Testes de contrato do endpoint /produtos (catálogo para busca global).
 * Ver nota sobre o perfil `agent-test` em DashboardControllerTest.
 */
@WebMvcTest(ProdutosController.class)
class ProdutosControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProdutoRepository repository;

    @Test
    void catalogoSerializaDTO() throws Exception {
        when(repository.getCatalogo())
            .thenReturn(List.of(
                new ProdutoCatalogoDTO("7", 9, "Homus", "Entradas", 1),
                new ProdutoCatalogoDTO("31", 40, "Grão de bico", "Insumos", 2)
            ));

        mockMvc.perform(get("/produtos"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].id").value("7"))
            .andExpect(jsonPath("$[0].idProduto").value(9))
            .andExpect(jsonPath("$[0].nome").value("Homus"))
            .andExpect(jsonPath("$[0].etiqueta").value("Entradas"))
            .andExpect(jsonPath("$[0].tipo").value(1))
            .andExpect(jsonPath("$[1].tipo").value(2));
    }

    @Test
    void catalogoVazioRetornaListaVazia() throws Exception {
        when(repository.getCatalogo()).thenReturn(List.of());

        mockMvc.perform(get("/produtos"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isEmpty());
    }
}
