package com.firebird_data_extractor.www.dto;

import java.util.List;

public record GarcomDetalhesDTO(
    Integer id,
    String nome,
    Double totalTaxaServico,
    Double faturamento,
    Double custo,
    Double lucro,
    Integer comandas,
    Integer itensLancados,
    List<ProdutoRankingDTO> produtosMaisVendidos
) {}
