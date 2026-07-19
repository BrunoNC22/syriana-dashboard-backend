package com.firebird_data_extractor.www.repositories;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.firebird_data_extractor.www.dto.ConsumoInsumoBucketDTO;
import com.firebird_data_extractor.www.dto.SaldoInsumoDTO;

/**
 * Acesso aos dados de estoque para o relatório de compras: saldo atual por
 * insumo (com flag de confiabilidade) e consumo histórico agregado por bucket.
 *
 * <p>Camada puramente de dados — não filtra por tipo de produto nem aplica
 * regras de negócio. Quem consome (o serviço do relatório) decide quais
 * insumos considerar e como tratar saldos não confiáveis.
 *
 * <p>Identidade do insumo = {@code PRODUTOS.CODIGO} (consistente com os demais
 * endpoints de insumo). Um insumo pode ter mais de um {@code PRODUTODETALHE}
 * (tamanhos/variações), então o saldo é agregado por produto.
 */
@Repository
public class EstoqueRepository {

    /** Granularidade do bucket temporal do consumo. */
    public enum Granularidade {
        DIA,
        SEMANA
    }

    private final JdbcTemplate jdbc;

    public EstoqueRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Saldo atual de cada insumo ativo, com a flag {@code confiavel}.
     *
     * <p>A confiabilidade é decidida no SQL: se qualquer {@code PRODUTODETALHE}
     * do insumo tem {@code ESTOQUEATUAL} NULL ou negativo, o saldo agregado é
     * marcado como não confiável (o {@code SUM} mascararia um NULL isolado, mas
     * a flag captura o problema).
     */
    public List<SaldoInsumoDTO> getSaldosPorInsumo() {

        return this.jdbc.query(
            """
            SELECT
                pdt.CODIGO AS insumo_id,
                pdt.NOME AS nome,
                u.SIGLA AS sigla,
                SUM(pd.ESTOQUEATUAL) AS estoque_atual,
                SUM(pd.ESTOQUEMINIMO) AS estoque_minimo,
                MAX(pd.PRECOCUSTO) AS preco_custo,
                MIN(CASE WHEN pd.ESTOQUEATUAL IS NULL OR pd.ESTOQUEATUAL < 0 THEN 0 ELSE 1 END) AS confiavel
            FROM PRODUTODETALHE pd
            JOIN PRODUTOS pdt ON pdt.CODIGO = pd.CODIGOPRODUTO
            LEFT JOIN UNIDADECOMERCIALIZACAO u ON u.CODIGO = pdt.CODIGOUNIDADECOMERCIAL
            WHERE pd.DATADELETE IS NULL
            GROUP BY pdt.CODIGO, pdt.NOME, u.SIGLA
            ORDER BY pdt.NOME
            """,
            (rs, rowNum) -> {

                double ea = rs.getDouble("estoque_atual");
                Double estoqueAtual = rs.wasNull() ? null : ea;

                double em = rs.getDouble("estoque_minimo");
                Double estoqueMinimo = rs.wasNull() ? null : em;

                double pc = rs.getDouble("preco_custo");
                Double precoCusto = rs.wasNull() ? null : pc;

                boolean confiavel = estoqueAtual != null && rs.getInt("confiavel") == 1;

                return new SaldoInsumoDTO(
                    String.valueOf(rs.getInt("insumo_id")),
                    rs.getString("nome"),
                    rs.getString("sigla"),
                    estoqueAtual,
                    estoqueMinimo,
                    precoCusto,
                    confiavel
                );
            }
        );
    }

    /**
     * Consumo de cada insumo no intervalo, agregado por bucket (dia ou semana),
     * a partir das saídas de estoque ({@code TIPO='S'}). Uma linha por
     * (insumo, bucket); série histórica para a previsão de consumo.
     *
     * <p>No bucket semanal o {@code bucket} é o domingo que inicia a semana
     * (convenção do app). O custo usa o {@code PRECOCUSTO} atual do detalhe.
     */
    public List<ConsumoInsumoBucketDTO> getConsumoPorInsumo(
        LocalDate dataInicio,
        LocalDate dataFim,
        Granularidade granularidade
    ) {

        LocalDateTime inicio = dataInicio.atStartOfDay();
        LocalDateTime fim = dataFim.plusDays(1).atStartOfDay();

        // Expressão de bucket montada com strings fixas (sem entrada do usuário).
        String bucketExpr = switch (granularidade) {
            case DIA -> "CAST(em.DATAINSERT AS DATE)";
            case SEMANA ->
                "DATEADD(DAY, -EXTRACT(WEEKDAY FROM CAST(em.DATAINSERT AS DATE)), CAST(em.DATAINSERT AS DATE))";
        };

        // GROUP BY / ORDER BY posicionais evitam repetir a expressão de bucket.
        String sql = """
            SELECT
                pd.CODIGOPRODUTO AS insumo_id,
                %s AS bucket,
                ABS(SUM(em.QTD)) AS quantidade,
                ABS(SUM(em.QTD * pd.PRECOCUSTO)) AS custo
            FROM ESTOQUEMOVIMENTACAO em
            JOIN PRODUTODETALHE pd ON pd.CODIGO = em.CODIGOPRODUTODETALHE
            WHERE
                em.DATADELETE IS NULL
                AND em.TIPO = 'S'
                AND em.DATAINSERT >= ?
                AND em.DATAINSERT < ?
            GROUP BY 1, 2
            ORDER BY 1, 2
            """.formatted(bucketExpr);

        return this.jdbc.query(
            sql,
            (rs, rowNum) -> new ConsumoInsumoBucketDTO(
                String.valueOf(rs.getInt("insumo_id")),
                rs.getDate("bucket").toLocalDate(),
                rs.getDouble("quantidade"),
                rs.getDouble("custo")
            ),
            inicio,
            fim
        );
    }
}
