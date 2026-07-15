package com.firebird_data_extractor.www.dto;

import java.util.List;

public record InsumoDetalhesDTO(
    String nome,
    String siglaUnidadeDeMedida,
    Double quantidadeTotal,
    Double custoTotal,
    Double custoMedioPorUnidade,
    Integer produtosDistintos,
    Integer movimentacoes,
    List<ProdutoQueUsaInsumoDTO> produtos
) {}
