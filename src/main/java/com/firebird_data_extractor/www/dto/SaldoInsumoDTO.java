package com.firebird_data_extractor.www.dto;

/**
 * Saldo atual de um insumo (agregado por {@code PRODUTOS.CODIGO}), com a flag
 * de confiabilidade que o relatório de compras precisa expor.
 *
 * <p>O banco legado tem saldos negativos e NULLs em {@code ESTOQUEATUAL} (o
 * controle de estoque do sistema é parcialmente usado — {@code ESTOQUECONTROLADO}
 * é 0 na maioria). Por isso {@code confiavel} é falso quando o saldo é NULL ou
 * negativo: nesses casos o relatório assume estoque 0 e sinaliza a incerteza,
 * em vez de gerar um número de compra enganoso.
 *
 * <p>{@code estoqueAtual} e {@code estoqueMinimo} podem ser {@code null}
 * (sem cadastro) e são repassados como estão — quem consome decide como tratar.
 */
public record SaldoInsumoDTO(
    String id,
    String nome,
    String siglaUnidadeDeMedida,
    Double estoqueAtual,
    Double estoqueMinimo,
    Double precoCusto,
    boolean confiavel
) {}
