package com.firebird_data_extractor.www.dto;

import java.time.LocalDate;

/**
 * Ponto diário com todas as métricas do dashboard num único registro.
 * Alimenta as sparklines dos KPIs: uma só resposta serve os quatro cards,
 * cada um lendo o campo da sua métrica.
 */
public record DiaSerieDTO(
    LocalDate dia,
    Double faturamento,
    Integer pedidos,
    Double custo,
    Double lucro
) {}
