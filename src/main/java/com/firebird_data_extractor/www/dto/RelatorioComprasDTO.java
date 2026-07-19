package com.firebird_data_extractor.www.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * Relatório de previsão de compras para o próximo período. Orquestra previsão
 * (faturamento, pedidos e consumo de insumos), estoque atual e regras de compra
 * numa única resposta — o coração é a lista {@code compras}.
 *
 * <p><b>É uma estimativa de apoio à decisão, não uma ordem de compra:</b> o
 * banco legado tem saldos negativos/NULL e não registra valor de compra (custos
 * vêm do preço de custo atual). Essas ressalvas vêm explícitas em {@code avisos}
 * e, por insumo, na flag de confiabilidade de cada linha.
 *
 * @param geradoEm         data de geração do relatório
 * @param horizonteInicio  primeiro dia do período previsto
 * @param horizonteFim     último dia do período previsto (inclusivo)
 * @param horizonteDias    duração do horizonte em dias
 * @param baseHistorica    descrição da base histórica e do método usados
 * @param panorama         faturamento/pedidos previstos + datas especiais
 * @param compras          uma linha por insumo (a tabela de compras)
 * @param avisos           ressalvas globais sobre a confiabilidade dos dados
 */
public record RelatorioComprasDTO(
    LocalDate geradoEm,
    LocalDate horizonteInicio,
    LocalDate horizonteFim,
    int horizonteDias,
    String baseHistorica,
    PanoramaCompraDTO panorama,
    List<LinhaCompraDTO> compras,
    List<String> avisos
) {}
