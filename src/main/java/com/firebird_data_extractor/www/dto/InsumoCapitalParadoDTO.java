package com.firebird_data_extractor.www.dto;

import java.time.LocalDate;

/**
 * Insumo com estoque em mãos (ESTOQUEATUAL > 0) e nenhum consumo no período —
 * capital imobilizado. Ordenado por valor parado (R$) decrescente.
 *
 * id                → PRODUTOS.CODIGO do insumo (abre /insumo-details/:id).
 * estoqueAtual      → saldo atual somado dos detalhes do insumo, na unidade do insumo.
 * custoEstoqueParado → estoqueAtual × PRECOCUSTO (R$ imobilizado).
 * ultimoUso         → data da última saída registrada (qualquer época); null se nunca saiu.
 */
public record InsumoCapitalParadoDTO(
    String id,
    String nome,
    String sigla,
    double estoqueAtual,
    double custoEstoqueParado,
    LocalDate ultimoUso
) {}
