package com.firebird_data_extractor.www.dto;

/**
 * Insumo cujo consumo da última semana desvia do seu próprio histórico recente.
 *
 * O z-score compara a última semana com a distribuição do consumo semanal do
 * insumo numa janela de referência (~12 semanas). |z| ≥ 2 indica desvio forte,
 * para cima (z > 0, consumo acima do padrão) ou para baixo (z < 0).
 *
 * id                  → PRODUTOS.CODIGO do insumo (abre /insumo-details/:id).
 * consumoUltimaSemana → consumo da última semana da janela (unidade do insumo).
 * mediaSemanal        → média do consumo semanal na janela.
 * desvioPadrao        → desvio padrão do consumo semanal na janela.
 * zScore              → (consumoUltimaSemana − mediaSemanal) / desvioPadrao (com sinal).
 */
public record InsumoForaDoPadraoDTO(
    String id,
    String nome,
    String sigla,
    double consumoUltimaSemana,
    double mediaSemanal,
    double desvioPadrao,
    double zScore
) {}
