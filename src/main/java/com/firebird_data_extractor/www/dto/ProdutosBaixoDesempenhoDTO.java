package com.firebird_data_extractor.www.dto;

import java.util.List;

/**
 * Resposta do endpoint /analytics/produtos-baixo-desempenho.
 *
 * Agrupa quatro classificações complementares de "baixo desempenho" sobre o
 * cardápio ativo vendável (ver AnalyticsService para os critérios):
 *
 * - semVendas      → itens ativos sem nenhuma venda no período selecionado.
 * - maioresQuedas  → maiores quedas percentuais vs. o período anterior.
 * - tendenciaQueda → queda consistente na série semanal do período (slope < 0).
 * - abandonados    → vendiam com regularidade e pararam nos últimos 30 dias.
 */
public record ProdutosBaixoDesempenhoDTO(
    List<ProdutoSemVendaDTO> semVendas,
    List<ProdutoQuedaDTO> maioresQuedas,
    List<ProdutoTendenciaQuedaDTO> tendenciaQueda,
    List<ProdutoAbandonadoDTO> abandonados
) {}
