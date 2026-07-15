package com.firebird_data_extractor.www.dto;

public record GarcomResumoDTO(
    Integer id,
    String nome,
    Double totalTaxaServico,
    Double faturamento,
    Double custo,
    Double lucro,
    Integer comandas,
    Integer itensLancados
) {}
