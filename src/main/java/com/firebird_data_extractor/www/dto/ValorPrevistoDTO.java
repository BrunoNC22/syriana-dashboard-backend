package com.firebird_data_extractor.www.dto;

/**
 * Um valor previsto (faturamento ou nº de pedidos) agregado sobre todo o
 * horizonte do relatório, com a banda de confiança e a proveniência do modelo.
 *
 * @param total          soma prevista no horizonte (central da banda)
 * @param limiteInferior soma do piso da banda de confiança
 * @param limiteSuperior soma do teto da banda de confiança
 * @param metodo         modelo usado ("HOLT_WINTERS" ou "NAIVE_SAZONAL")
 * @param mape           erro % médio do backtest; {@code null} quando não houve
 *                       histórico para validar
 */
public record ValorPrevistoDTO(
    double total,
    double limiteInferior,
    double limiteSuperior,
    String metodo,
    Double mape
) {}
