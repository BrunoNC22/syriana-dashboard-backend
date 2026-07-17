package com.firebird_data_extractor.www.dto;

import java.util.List;

/**
 * Resposta do endpoint /analytics/insumos-baixo-uso.
 *
 * Espelho, para insumos, do /analytics/produtos-baixo-desempenho. Agrupa quatro
 * classificações sobre o consumo de insumos (ESTOQUEMOVIMENTACAO TIPO='S'), ver
 * AnalyticsService para os critérios:
 *
 * - poucoUtilizados → insumos ativos com menor consumo no período (inclui os
 *                     sem nenhuma saída — o "sem uso").
 * - capitalParado   → têm estoque em mãos (ESTOQUEATUAL > 0) e não giraram no
 *                     período — capital imobilizado (o insight mais acionável).
 * - quedaConsumo    → maiores quedas percentuais de consumo vs. o período anterior.
 * - foraDoPadrao    → consumo semanal recente desviando do histórico do próprio
 *                     insumo (|z| ≥ 2, para cima ou para baixo).
 */
public record InsumosBaixoUsoDTO(
    List<InsumoPoucoUtilizadoDTO> poucoUtilizados,
    List<InsumoCapitalParadoDTO> capitalParado,
    List<InsumoQuedaConsumoDTO> quedaConsumo,
    List<InsumoForaDoPadraoDTO> foraDoPadrao
) {}
