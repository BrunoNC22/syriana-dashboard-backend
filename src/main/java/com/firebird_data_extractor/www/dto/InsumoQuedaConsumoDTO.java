package com.firebird_data_extractor.www.dto;

/**
 * Insumo cujo consumo caiu vs. o período anterior de mesma duração.
 *
 * id                  → PRODUTOS.CODIGO do insumo (abre /insumo-details/:id).
 * quantidadeAtual     → consumo no período selecionado (unidade do insumo).
 * quantidadeAnterior  → consumo no período anterior de mesma duração.
 * variacaoPercentual  → (atual − anterior) / anterior × 100 (negativa = queda).
 */
public record InsumoQuedaConsumoDTO(
    String id,
    String nome,
    String sigla,
    double quantidadeAtual,
    double quantidadeAnterior,
    double variacaoPercentual
) {}
