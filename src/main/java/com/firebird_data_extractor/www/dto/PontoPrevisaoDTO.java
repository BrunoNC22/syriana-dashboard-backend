package com.firebird_data_extractor.www.dto;

import java.time.LocalDate;

/**
 * Um dia previsto com a banda de confiança (~80%).
 *
 * @param data           dia previsto (ISO yyyy-MM-dd)
 * @param valor          previsão central (com uplift de feriado já reaplicado)
 * @param limiteInferior piso da banda de confiança
 * @param limiteSuperior teto da banda de confiança
 * @param feriado        true se o dia é feriado/data comemorativa conhecida
 */
public record PontoPrevisaoDTO(
    LocalDate data,
    double valor,
    double limiteInferior,
    double limiteSuperior,
    boolean feriado
) {}
