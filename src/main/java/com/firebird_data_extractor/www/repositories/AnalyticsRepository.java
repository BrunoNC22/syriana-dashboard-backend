package com.firebird_data_extractor.www.repositories;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Consultas analíticas agregadas para as telas de "Saúde do cardápio".
 *
 * Segue o padrão do projeto: SQL puro via JdbcTemplate, agregação feita no
 * banco (GROUP BY) e classificação/estatística em Java sobre o resultado
 * agregado (ver AnalyticsService). Nunca puxa linhas cruas de ITENSPEDIDO.
 *
 * Os records aqui são tipos internos de transporte repositório → service; não
 * são serializados na API (os DTOs da resposta ficam em dto/).
 */
@Repository
public class AnalyticsRepository {

    private final JdbcTemplate jdbc;

    public AnalyticsRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Produto vendável ativo do cardápio (universo das análises). */
    public record ProdutoUniverso(String id, String nome, String etiqueta) {}

    /** Agregado histórico + por período de um produto-detalhe. */
    public record VendaAgregada(
        String id,
        LocalDate primeiraVenda,
        LocalDate ultimaVenda,
        double qtdAtual,
        double qtdAnterior
    ) {}

    /** Total vendido de um produto-detalhe num dia. */
    public record VendaDiaria(String id, LocalDate dia, double qtd) {}

    /** Total vendido de um produto-detalhe num mês (índice ano*12+mês). */
    public record VendaMensal(String id, int mesIndice, double qtd) {}

    /** Insumo ativo (universo das análises de baixo uso); id = PRODUTOS.CODIGO. */
    public record InsumoUniverso(
        String id,
        String nome,
        String sigla,
        double estoqueAtual,
        double custoEstoque
    ) {}

    /** Consumo agregado (histórico + por período) de um insumo. */
    public record ConsumoAgregado(
        String id,
        LocalDate primeiroUso,
        LocalDate ultimoUso,
        double qtdAtual,
        double qtdAnterior,
        double custoAtual,
        double custoAnterior
    ) {}

    /** Consumo de um insumo (PRODUTOS.CODIGO) num dia. */
    public record ConsumoDiario(String id, LocalDate dia, double qtd) {}

    private static LocalDate toLocalDate(Timestamp ts) {
        return ts != null ? ts.toLocalDateTime().toLocalDate() : null;
    }

    /**
     * Cardápio ativo vendável: só produtos vendáveis (CODIGOPRODUTOTIPO em
     * 1=Produto, 4=Combo, 5=Produto por Tamanho — exclui Insumo e Complemento),
     * ativos (não deletados, não pausados, não descontinuados) e sem o prefixo
     * "* Excluído *" no nome. O id é o CODIGOPRODUTODETALHE.
     */
    public List<ProdutoUniverso> getCatalogoVendavelAtivo() {
        return this.jdbc.query(
            """
            SELECT
                pd.CODIGO AS id,
                p.NOME AS nome,
                pt.DESCRICAO AS tamanho,
                e.DESCRICAO AS etiqueta
            FROM PRODUTODETALHE pd
            JOIN PRODUTOS p ON p.CODIGO = pd.CODIGOPRODUTO
            LEFT JOIN PRODUTOTAMANHO pt ON pt.CODIGO = pd.CODIGOPRODUTOTAMANHO
            LEFT JOIN ETIQUETAS e ON e.CODIGO = p.CODIGOETIQUETA
            WHERE
                pd.DATADELETE IS NULL
                AND pd.DATAPAUSADO IS NULL
                AND p.DESCONTINUADO = 'N'
                AND p.NOME NOT LIKE '%Excluído%'
                AND p.CODIGOPRODUTOTIPO IN (1, 4, 5)
            ORDER BY p.NOME
            """,
            (rs, rowNum) -> {
                String nome = rs.getString("nome");
                String tamanho = rs.getString("tamanho");
                if (tamanho != null && !tamanho.isBlank()) {
                    nome = nome + " " + tamanho;
                }
                return new ProdutoUniverso(
                    String.valueOf(rs.getInt("id")),
                    nome,
                    rs.getString("etiqueta")
                );
            }
        );
    }

    /**
     * Por produto-detalhe: primeira e última venda (qualquer época) e a
     * quantidade vendida no período atual e no período anterior (mesma
     * duração). Uma varredura só de ITENSPEDIDO com somas condicionais.
     */
    public List<VendaAgregada> getAgregadoVendasPorProduto(
        LocalDate inicioAtual,
        LocalDate fimAtual,
        LocalDate inicioAnterior,
        LocalDate fimAnterior
    ) {
        LocalDateTime atualInicio = inicioAtual.atStartOfDay();
        LocalDateTime atualFim = fimAtual.plusDays(1).atStartOfDay();
        LocalDateTime anteriorInicio = inicioAnterior.atStartOfDay();
        LocalDateTime anteriorFim = fimAnterior.plusDays(1).atStartOfDay();

        return this.jdbc.query(
            """
            SELECT
                i.CODIGOPRODUTODETALHE AS id,
                MIN(i.DATAHORACADASTRO) AS primeira_venda,
                MAX(i.DATAHORACADASTRO) AS ultima_venda,
                SUM(CASE WHEN i.DATAHORACADASTRO >= ? AND i.DATAHORACADASTRO < ?
                         THEN i.QUANTIDADE ELSE 0 END) AS qtd_atual,
                SUM(CASE WHEN i.DATAHORACADASTRO >= ? AND i.DATAHORACADASTRO < ?
                         THEN i.QUANTIDADE ELSE 0 END) AS qtd_anterior
            FROM ITENSPEDIDO i
            WHERE
                i.DATADELETE IS NULL
                AND i.CODIGOPRODUTODETALHE IS NOT NULL
            GROUP BY i.CODIGOPRODUTODETALHE
            """,
            (rs, rowNum) -> new VendaAgregada(
                String.valueOf(rs.getInt("id")),
                toLocalDate(rs.getTimestamp("primeira_venda")),
                toLocalDate(rs.getTimestamp("ultima_venda")),
                rs.getDouble("qtd_atual"),
                rs.getDouble("qtd_anterior")
            ),
            atualInicio, atualFim, anteriorInicio, anteriorFim
        );
    }

    /** Total vendido por produto-detalhe e por dia dentro do período. */
    public List<VendaDiaria> getVendasDiariasPorProduto(
        LocalDate dataInicio,
        LocalDate dataFim
    ) {
        LocalDateTime inicio = dataInicio.atStartOfDay();
        LocalDateTime fim = dataFim.plusDays(1).atStartOfDay();

        return this.jdbc.query(
            """
            SELECT
                i.CODIGOPRODUTODETALHE AS id,
                CAST(i.DATAHORACADASTRO AS DATE) AS dia,
                SUM(i.QUANTIDADE) AS qtd
            FROM ITENSPEDIDO i
            WHERE
                i.DATADELETE IS NULL
                AND i.CODIGOPRODUTODETALHE IS NOT NULL
                AND i.DATAHORACADASTRO >= ?
                AND i.DATAHORACADASTRO < ?
            GROUP BY i.CODIGOPRODUTODETALHE, CAST(i.DATAHORACADASTRO AS DATE)
            """,
            (rs, rowNum) -> new VendaDiaria(
                String.valueOf(rs.getInt("id")),
                rs.getDate("dia").toLocalDate(),
                rs.getDouble("qtd")
            ),
            inicio, fim
        );
    }

    /** Total vendido por produto-detalhe e por mês dentro do intervalo. */
    public List<VendaMensal> getVendasMensaisPorProduto(
        LocalDate dataInicio,
        LocalDate dataFim
    ) {
        LocalDateTime inicio = dataInicio.atStartOfDay();
        LocalDateTime fim = dataFim.plusDays(1).atStartOfDay();

        return this.jdbc.query(
            """
            SELECT
                i.CODIGOPRODUTODETALHE AS id,
                EXTRACT(YEAR FROM i.DATAHORACADASTRO) * 12
                    + EXTRACT(MONTH FROM i.DATAHORACADASTRO) AS mes_indice,
                SUM(i.QUANTIDADE) AS qtd
            FROM ITENSPEDIDO i
            WHERE
                i.DATADELETE IS NULL
                AND i.CODIGOPRODUTODETALHE IS NOT NULL
                AND i.DATAHORACADASTRO >= ?
                AND i.DATAHORACADASTRO < ?
            GROUP BY
                i.CODIGOPRODUTODETALHE,
                EXTRACT(YEAR FROM i.DATAHORACADASTRO) * 12
                    + EXTRACT(MONTH FROM i.DATAHORACADASTRO)
            """,
            (rs, rowNum) -> new VendaMensal(
                String.valueOf(rs.getInt("id")),
                rs.getInt("mes_indice"),
                rs.getDouble("qtd")
            ),
            inicio, fim
        );
    }

    // ------------------------------------------------------------------
    // Insumos — consumo via ESTOQUEMOVIMENTACAO TIPO='S' (saída).
    // O insumo é identificado por PRODUTODETALHE.CODIGOPRODUTO (= PRODUTOS.CODIGO);
    // a data da movimentação é DATAINSERT e a QTD é negativa (ABS = magnitude).
    // ------------------------------------------------------------------

    /**
     * Insumos ativos (universo das análises): só do tipo Insumo
     * (CODIGOPRODUTOTIPO = 2), ativos (não descontinuados, sem prefixo
     * "* Excluído *") e com pelo menos um PRODUTODETALHE ativo (não deletado,
     * não pausado). Traz o saldo em mãos somado dos detalhes e o custo desse
     * saldo (para "capital parado"). O id é o PRODUTOS.CODIGO do insumo.
     */
    public List<InsumoUniverso> getCatalogoInsumoAtivo() {
        return this.jdbc.query(
            """
            SELECT
                p.CODIGO AS id,
                MIN(p.NOME) AS nome,
                MIN(u.SIGLA) AS sigla,
                COALESCE(SUM(pd.ESTOQUEATUAL), 0) AS estoque_atual,
                COALESCE(SUM(pd.ESTOQUEATUAL * pd.PRECOCUSTO), 0) AS custo_estoque
            FROM PRODUTOS p
            JOIN PRODUTODETALHE pd ON pd.CODIGOPRODUTO = p.CODIGO
            LEFT JOIN UNIDADECOMERCIALIZACAO u ON u.CODIGO = p.CODIGOUNIDADECOMERCIAL
            WHERE
                p.CODIGOPRODUTOTIPO = 2
                AND p.DESCONTINUADO = 'N'
                AND p.NOME NOT LIKE '%Excluído%'
                AND pd.DATADELETE IS NULL
                AND pd.DATAPAUSADO IS NULL
            GROUP BY p.CODIGO
            ORDER BY MIN(p.NOME)
            """,
            (rs, rowNum) -> new InsumoUniverso(
                String.valueOf(rs.getInt("id")),
                rs.getString("nome"),
                rs.getString("sigla"),
                rs.getDouble("estoque_atual"),
                rs.getDouble("custo_estoque")
            )
        );
    }

    /**
     * Por insumo (PRODUTOS.CODIGO): primeira e última saída (qualquer época) e
     * o consumo (quantidade e custo) no período atual e no período anterior de
     * mesma duração. Uma varredura só de ESTOQUEMOVIMENTACAO 'S' com somas
     * condicionais; QTD negativa → ABS para a magnitude.
     */
    public List<ConsumoAgregado> getAgregadoConsumoPorInsumo(
        LocalDate inicioAtual,
        LocalDate fimAtual,
        LocalDate inicioAnterior,
        LocalDate fimAnterior
    ) {
        LocalDateTime atualInicio = inicioAtual.atStartOfDay();
        LocalDateTime atualFim = fimAtual.plusDays(1).atStartOfDay();
        LocalDateTime anteriorInicio = inicioAnterior.atStartOfDay();
        LocalDateTime anteriorFim = fimAnterior.plusDays(1).atStartOfDay();

        return this.jdbc.query(
            """
            SELECT
                pd.CODIGOPRODUTO AS id,
                MIN(em.DATAINSERT) AS primeiro_uso,
                MAX(em.DATAINSERT) AS ultimo_uso,
                ABS(SUM(CASE WHEN em.DATAINSERT >= ? AND em.DATAINSERT < ?
                             THEN em.QTD ELSE 0 END)) AS qtd_atual,
                ABS(SUM(CASE WHEN em.DATAINSERT >= ? AND em.DATAINSERT < ?
                             THEN em.QTD ELSE 0 END)) AS qtd_anterior,
                ABS(SUM(CASE WHEN em.DATAINSERT >= ? AND em.DATAINSERT < ?
                             THEN em.QTD * pd.PRECOCUSTO ELSE 0 END)) AS custo_atual,
                ABS(SUM(CASE WHEN em.DATAINSERT >= ? AND em.DATAINSERT < ?
                             THEN em.QTD * pd.PRECOCUSTO ELSE 0 END)) AS custo_anterior
            FROM ESTOQUEMOVIMENTACAO em
            JOIN PRODUTODETALHE pd ON pd.CODIGO = em.CODIGOPRODUTODETALHE
            WHERE
                em.DATADELETE IS NULL
                AND em.TIPO = 'S'
            GROUP BY pd.CODIGOPRODUTO
            """,
            (rs, rowNum) -> new ConsumoAgregado(
                String.valueOf(rs.getInt("id")),
                toLocalDate(rs.getTimestamp("primeiro_uso")),
                toLocalDate(rs.getTimestamp("ultimo_uso")),
                rs.getDouble("qtd_atual"),
                rs.getDouble("qtd_anterior"),
                rs.getDouble("custo_atual"),
                rs.getDouble("custo_anterior")
            ),
            atualInicio, atualFim, anteriorInicio, anteriorFim,
            atualInicio, atualFim, anteriorInicio, anteriorFim
        );
    }

    /** Consumo por insumo (PRODUTOS.CODIGO) e por dia dentro do intervalo. */
    public List<ConsumoDiario> getConsumoDiarioPorInsumo(
        LocalDate dataInicio,
        LocalDate dataFim
    ) {
        LocalDateTime inicio = dataInicio.atStartOfDay();
        LocalDateTime fim = dataFim.plusDays(1).atStartOfDay();

        return this.jdbc.query(
            """
            SELECT
                pd.CODIGOPRODUTO AS id,
                CAST(em.DATAINSERT AS DATE) AS dia,
                ABS(SUM(em.QTD)) AS qtd
            FROM ESTOQUEMOVIMENTACAO em
            JOIN PRODUTODETALHE pd ON pd.CODIGO = em.CODIGOPRODUTODETALHE
            WHERE
                em.DATADELETE IS NULL
                AND em.TIPO = 'S'
                AND em.DATAINSERT >= ?
                AND em.DATAINSERT < ?
            GROUP BY pd.CODIGOPRODUTO, CAST(em.DATAINSERT AS DATE)
            """,
            (rs, rowNum) -> new ConsumoDiario(
                String.valueOf(rs.getInt("id")),
                rs.getDate("dia").toLocalDate(),
                rs.getDouble("qtd")
            ),
            inicio, fim
        );
    }
}
