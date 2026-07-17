package com.firebird_data_extractor.www.dto;

import java.time.LocalDate;

/**
 * Produto ativo do cardápio sem nenhuma venda no período selecionado.
 *
 * id          → CODIGOPRODUTODETALHE (abre /product-details/:id no frontend).
 * ultimaVenda → data da última venda registrada (qualquer época); null se o
 *               produto nunca vendeu.
 * novo        → primeira venda há menos de 30 dias (item recém-lançado — não
 *               deve ser tratado como "encalhado").
 */
public record ProdutoSemVendaDTO(
    String id,
    String nome,
    String etiqueta,
    LocalDate ultimaVenda,
    boolean novo
) {}
