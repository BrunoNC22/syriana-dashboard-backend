package com.firebird_data_extractor.www.dto;

import java.util.List;

/**
 * Panorama do período previsto no relatório de compras: quanto se espera
 * faturar e quantos pedidos, e as datas comemorativas do horizonte — que puxam
 * a venda (e, por consequência, a compra de insumos) para cima.
 *
 * @param faturamento   faturamento previsto no horizonte (com banda)
 * @param pedidos       nº de pedidos previsto no horizonte (com banda)
 * @param datasEspeciais feriados/datas comemorativas dentro do horizonte
 */
public record PanoramaCompraDTO(
    ValorPrevistoDTO faturamento,
    ValorPrevistoDTO pedidos,
    List<FeriadoDTO> datasEspeciais
) {}
