package com.firebird_data_extractor.www.dto;

public record InsumoDoProdutoDTO(
    String nome,
    String siglaUnidadeDeMedida,
    Double quantidadeTotal,
    Double custoTotal
) {}
