package com.firebird_data_extractor.www.dto;

public record ProdutoMaisVendidoDTO(
    String id,
    String nomeProduto,
    Double totalVendido
) {}
