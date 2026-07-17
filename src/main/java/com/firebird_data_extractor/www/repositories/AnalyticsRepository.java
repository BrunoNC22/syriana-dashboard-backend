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
}
