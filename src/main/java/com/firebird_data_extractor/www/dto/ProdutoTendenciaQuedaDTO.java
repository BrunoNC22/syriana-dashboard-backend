package com.firebird_data_extractor.www.dto;

/**
 * Produto em tendência de queda dentro do período: o slope da regressão linear
 * sobre os totais semanais é negativo.
 *
 * id             → CODIGOPRODUTODETALHE.
 * slopePorSemana → inclinação (unidades vendidas por semana); negativo = caindo.
 * quantidadeTotal→ total vendido no período (usado como contexto e piso).
 * semanas        → nº de buckets semanais considerados na regressão.
 */
public record ProdutoTendenciaQuedaDTO(
    String id,
    String nome,
    String etiqueta,
    double slopePorSemana,
    double quantidadeTotal,
    int semanas
) {}
