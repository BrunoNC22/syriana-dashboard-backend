package com.firebird_data_extractor.www.dto;

import java.util.List;

public record ProdutoDetalhesDTO(
    String nomeProduto,
    Double quantidadeVendida,
    Double faturamento,
    Double custoTotal,
    Double lucro,
    Double margemLucro,
    List<InsumoDoProdutoDTO> insumos
) {}
