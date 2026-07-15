package com.firebird_data_extractor.www.dto;

public record InsumoQueMaisCustouDTO(
    String id,
    String nome,
    String siglaUnidadeDeMedida,
    Double quantidade,
    Double precoPorUnidadeDeMedida,
    Double valorTotal
) {}
