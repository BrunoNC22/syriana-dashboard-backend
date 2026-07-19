package com.firebird_data_extractor.www.dto;

import java.util.List;

/**
 * Resposta do endpoint {@code /analytics/previsao}.
 *
 * @param metrica              "faturamento" ou "pedidos"
 * @param metodo               modelo usado ("HOLT_WINTERS" ou "NAIVE_SAZONAL")
 * @param mape                 erro % médio do backtest; {@code null} quando não
 *                             houve histórico para validar
 * @param horizonteDias        nº de dias previstos
 * @param diasHistorico        nº de dias de série usados no treino
 * @param avisoBaixaConfianca  aviso legível quando a previsão é pouco confiável
 *                             (horizonte longo / histórico curto); {@code null}
 *                             quando não há ressalva
 * @param pontos               previsão dia a dia com banda de confiança
 * @param feriadosNoHorizonte  datas comemorativas dentro do horizonte
 */
public record PrevisaoDTO(
    String metrica,
    String metodo,
    Double mape,
    int horizonteDias,
    int diasHistorico,
    String avisoBaixaConfianca,
    List<PontoPrevisaoDTO> pontos,
    List<FeriadoDTO> feriadosNoHorizonte
) {}
