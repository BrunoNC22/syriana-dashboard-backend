package com.firebird_data_extractor.www.dto;

import java.time.LocalDate;

/**
 * Insumo ativo com baixo (ou nenhum) consumo no período selecionado.
 *
 * id                  → PRODUTOS.CODIGO do insumo (abre /insumo-details/:id).
 * quantidadeConsumida → total consumido no período, na unidade do insumo (0 = sem uso).
 * custoConsumido      → custo do consumo no período (R$), via PRECOCUSTO atual.
 * ultimoUso           → data da última saída registrada (qualquer época); null se nunca saiu.
 * novo                → primeira saída há menos de 30 dias (recém-cadastrado — não é "encalhado").
 */
public record InsumoPoucoUtilizadoDTO(
    String id,
    String nome,
    String sigla,
    double quantidadeConsumida,
    double custoConsumido,
    LocalDate ultimoUso,
    boolean novo
) {}
