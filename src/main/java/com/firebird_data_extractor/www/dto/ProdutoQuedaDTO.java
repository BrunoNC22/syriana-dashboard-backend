package com.firebird_data_extractor.www.dto;

/**
 * Produto com queda de vendas do período atual em relação ao período
 * imediatamente anterior de mesma duração.
 *
 * id                  → CODIGOPRODUTODETALHE.
 * variacaoPercentual  → (atual - anterior) / anterior * 100 (sempre < 0 aqui).
 *
 * Só entram itens cuja quantidade no período anterior atingiu o piso mínimo
 * (ver AnalyticsService.PISO_QUANTIDADE) — evita "queda de 100%" de itens que
 * venderam 1 unidade.
 */
public record ProdutoQuedaDTO(
    String id,
    String nome,
    String etiqueta,
    double quantidadeAtual,
    double quantidadeAnterior,
    double variacaoPercentual
) {}
