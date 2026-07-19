package com.firebird_data_extractor.www.dto;

/**
 * Uma linha da tabela de compras: tudo o que o gerente precisa para decidir
 * quanto comprar de um insumo para o próximo período.
 *
 * <p>Todos os valores de quantidade estão na unidade do insumo
 * ({@code sigla}); os de dinheiro em R$. O consumo previsto vem do top-down
 * (previsão do custo total de consumo × participação recente do insumo). O
 * estoque assumido é 0 quando {@code estoqueConfiavel} é falso (saldo negativo
 * ou não cadastrado) — nesse caso a compra é calculada por precaução.
 *
 * @param id                     PRODUTOS.CODIGO do insumo
 * @param nome                   nome do insumo
 * @param siglaUnidadeDeMedida   unidade (kg, un, L…)
 * @param consumoPrevisto        consumo previsto no horizonte, na unidade do insumo
 * @param custoPrevisto          custo estimado desse consumo (R$)
 * @param estoqueAtual           saldo atual (pode ser {@code null} ou negativo — ver flag)
 * @param estoqueConfiavel       false quando o saldo é negativo/NULL (assume-se 0)
 * @param estoqueSeguranca       estoque de segurança sugerido (z×σ, piso em ESTOQUEMINIMO)
 * @param margemSeguranca        rótulo da margem: "baixa" | "normal" | "alta"
 * @param compraRecomendada      quantidade a comprar (0 se o estoque já cobre)
 * @param custoCompraRecomendada custo estimado da compra recomendada (R$)
 * @param riscoRuptura           semáforo: "alto" (🔴) | "medio" (🟡) | "baixo" (🟢)
 * @param diasCobertura          dias que o estoque atual cobre; {@code null} se não há consumo previsto
 * @param excesso                true quando o estoque é muito maior que o consumo (capital parado)
 * @param justificativa          explicação gerada pela fórmula, em linguagem simples
 */
public record LinhaCompraDTO(
    String id,
    String nome,
    String siglaUnidadeDeMedida,
    double consumoPrevisto,
    double custoPrevisto,
    Double estoqueAtual,
    boolean estoqueConfiavel,
    double estoqueSeguranca,
    String margemSeguranca,
    double compraRecomendada,
    double custoCompraRecomendada,
    String riscoRuptura,
    Double diasCobertura,
    boolean excesso,
    String justificativa
) {}
