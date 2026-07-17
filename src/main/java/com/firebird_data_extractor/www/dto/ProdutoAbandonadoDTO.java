package com.firebird_data_extractor.www.dto;

import java.time.LocalDate;

/**
 * Produto "abandonado": vendia com regularidade nos últimos 6 meses (em pelo
 * menos 3 meses distintos) e não vendeu nada nos últimos 30 dias.
 *
 * id                     → CODIGOPRODUTODETALHE.
 * ultimaVenda            → data da última venda registrada.
 * mesesComVenda          → nº de meses distintos com venda na janela de 6 meses.
 * quantidadeUltimos6Meses→ total vendido na janela de 6 meses.
 */
public record ProdutoAbandonadoDTO(
    String id,
    String nome,
    String etiqueta,
    LocalDate ultimaVenda,
    int mesesComVenda,
    double quantidadeUltimos6Meses
) {}
