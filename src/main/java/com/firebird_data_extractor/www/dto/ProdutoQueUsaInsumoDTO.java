package com.firebird_data_extractor.www.dto;

public record ProdutoQueUsaInsumoDTO(
    String id,
    String nomeProduto,
    Double quantidadeUsada,
    Double custoTotal
) {}
