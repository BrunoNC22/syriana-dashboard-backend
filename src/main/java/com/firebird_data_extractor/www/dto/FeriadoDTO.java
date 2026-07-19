package com.firebird_data_extractor.www.dto;

import java.time.LocalDate;

/**
 * Feriado/data comemorativa que cai dentro do horizonte previsto — o front usa
 * para destacar os dias que puxam a venda para cima.
 *
 * @param data      dia do feriado (ISO yyyy-MM-dd)
 * @param nome      nome legível (ex.: "Dia das Mães")
 * @param categoria categoria (COMEMORATIVA, NACIONAL, MOVEL, PERSONALIZADO)
 */
public record FeriadoDTO(
    LocalDate data,
    String nome,
    String categoria
) {}
