package com.firebird_data_extractor.www.dto;

import java.time.LocalDate;

/**
 * Consumo de um insumo em um bucket temporal (dia ou semana), a partir de
 * {@code ESTOQUEMOVIMENTACAO TIPO='S'}. Uma linha por (insumo, bucket).
 *
 * <p>É a série histórica que alimenta a previsão de consumo do relatório de
 * compras: quem consome agrupa por {@code id} para montar a série de cada
 * insumo. {@code quantidade} é o valor absoluto consumido (as saídas são
 * negativas no banco); {@code custo} usa o {@code PRECOCUSTO} atual, pois
 * {@code VALORCOMPRA} é ~100% NULL.
 *
 * <p>Em bucket semanal, {@code bucket} é o domingo que inicia a semana
 * (convenção de semana do app: começa no domingo).
 */
public record ConsumoInsumoBucketDTO(
    String id,
    LocalDate bucket,
    Double quantidade,
    Double custo
) {}
