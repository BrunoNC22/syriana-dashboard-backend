package com.firebird_data_extractor.www.repositories;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.firebird_data_extractor.www.dto.GarcomDetalhesDTO;
import com.firebird_data_extractor.www.dto.GarcomResumoDTO;
import com.firebird_data_extractor.www.dto.ProdutoRankingDTO;

@Repository
public class GarcomRepository {

    private final JdbcTemplate jdbc;

    public GarcomRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Acumulador mutável usado para mesclar as três consultas do ranking
     * (nível de pedido, contagem de itens e custo) por garçom.
     */
    private static final class GarcomAcumulador {
        Integer id;
        String nome;
        double taxa;
        double faturamento;
        double custo;
        int comandas;
        int itens;
    }

    public List<GarcomResumoDTO> getRankingGarcons(
        LocalDate dataInicio,
        LocalDate dataFim
    ) {

        LocalDateTime inicio = dataInicio.atStartOfDay();
        LocalDateTime fim = dataFim.plusDays(1).atStartOfDay();

        Map<Integer, GarcomAcumulador> acumuladores = new LinkedHashMap<>();

        // 1. Nível de pedido: taxa de serviço (10%), faturamento e nº de comandas.
        //    O garçom "dono" da comanda é PEDIDOS.CODIGOCOLABORADOR.
        this.jdbc.query(
            """
            SELECT
                p.CODIGOCOLABORADOR AS id,
                c.NOME AS nome,
                SUM(p.TOTALSERVICO) AS taxa,
                SUM(p.VALORTOTAL) AS faturamento,
                COUNT(*) AS comandas
            FROM PEDIDOS p
            JOIN CONTATOS c ON c.CODIGO = p.CODIGOCOLABORADOR
            WHERE
                p.DATADELETE IS NULL
                AND p.CODIGOCOLABORADOR IS NOT NULL
                AND p.DATAABERTURA >= ?
                AND p.DATAABERTURA < ?
            GROUP BY p.CODIGOCOLABORADOR, c.NOME
            """,
            rs -> {
                GarcomAcumulador a = new GarcomAcumulador();
                a.id = rs.getInt("id");
                a.nome = rs.getString("nome");
                a.taxa = rs.getDouble("taxa");
                a.faturamento = rs.getDouble("faturamento");
                a.comandas = rs.getInt("comandas");
                acumuladores.put(a.id, a);
            },
            inicio,
            fim
        );

        // 2. Contagem de itens lançados nas comandas de cada garçom.
        this.jdbc.query(
            """
            SELECT
                p.CODIGOCOLABORADOR AS id,
                COUNT(i.CODIGO) AS itens
            FROM ITENSPEDIDO i
            JOIN PEDIDOS p ON p.CODIGO = i.CODIGOPEDIDO
            WHERE
                i.DATADELETE IS NULL
                AND p.DATADELETE IS NULL
                AND p.CODIGOCOLABORADOR IS NOT NULL
                AND p.DATAABERTURA >= ?
                AND p.DATAABERTURA < ?
            GROUP BY p.CODIGOCOLABORADOR
            """,
            rs -> {
                GarcomAcumulador a = acumuladores.get(rs.getInt("id"));
                if (a != null) {
                    a.itens = rs.getInt("itens");
                }
            },
            inicio,
            fim
        );

        // 3. Custo dos insumos consumidos (ESTOQUEMOVIMENTACAO tipo 'S'),
        //    ligado ao item vendido via CODIGOITEMPEDIDO. Lucro = faturamento - custo.
        this.jdbc.query(
            """
            SELECT
                p.CODIGOCOLABORADOR AS id,
                ABS(SUM(em.QTD * pd.PRECOCUSTO)) AS custo
            FROM ESTOQUEMOVIMENTACAO em
            JOIN ITENSPEDIDO i ON i.CODIGO = em.CODIGOITEMPEDIDO
            JOIN PEDIDOS p ON p.CODIGO = i.CODIGOPEDIDO
            JOIN PRODUTODETALHE pd ON pd.CODIGO = em.CODIGOPRODUTODETALHE
            WHERE
                em.DATADELETE IS NULL
                AND em.TIPO = 'S'
                AND i.DATADELETE IS NULL
                AND p.DATADELETE IS NULL
                AND p.CODIGOCOLABORADOR IS NOT NULL
                AND p.DATAABERTURA >= ?
                AND p.DATAABERTURA < ?
            GROUP BY p.CODIGOCOLABORADOR
            """,
            rs -> {
                GarcomAcumulador a = acumuladores.get(rs.getInt("id"));
                if (a != null) {
                    a.custo = rs.getDouble("custo");
                }
            },
            inicio,
            fim
        );

        return acumuladores.values()
            .stream()
            .map(a -> new GarcomResumoDTO(
                a.id,
                a.nome,
                a.taxa,
                a.faturamento,
                a.custo,
                a.faturamento - a.custo,
                a.comandas,
                a.itens
            ))
            .sorted(
                Comparator.comparing(
                    GarcomResumoDTO::totalTaxaServico,
                    Comparator.reverseOrder()
                )
            )
            .toList();
    }

    public GarcomDetalhesDTO getGarcomDetalhes(
        Integer id,
        LocalDate dataInicio,
        LocalDate dataFim
    ) {

        LocalDateTime inicio = dataInicio.atStartOfDay();
        LocalDateTime fim = dataFim.plusDays(1).atStartOfDay();

        String nome = this.jdbc.query(
            "SELECT NOME FROM CONTATOS WHERE CODIGO = ?",
            rs -> rs.next() ? rs.getString("NOME") : null,
            id
        );

        // Métricas de nível de pedido. Sem GROUP BY, o agregado sempre devolve
        // uma linha (0/NULL) mesmo que o garçom não tenha pedidos no período.
        record PedidoStats(double taxa, double faturamento, int comandas) {}

        PedidoStats stats = this.jdbc.queryForObject(
            """
            SELECT
                SUM(p.TOTALSERVICO) AS taxa,
                SUM(p.VALORTOTAL) AS faturamento,
                COUNT(*) AS comandas
            FROM PEDIDOS p
            WHERE
                p.DATADELETE IS NULL
                AND p.CODIGOCOLABORADOR = ?
                AND p.DATAABERTURA >= ?
                AND p.DATAABERTURA < ?
            """,
            (rs, rowNum) -> new PedidoStats(
                rs.getDouble("taxa"),
                rs.getDouble("faturamento"),
                rs.getInt("comandas")
            ),
            id,
            inicio,
            fim
        );

        Integer itens = this.jdbc.queryForObject(
            """
            SELECT COUNT(i.CODIGO)
            FROM ITENSPEDIDO i
            JOIN PEDIDOS p ON p.CODIGO = i.CODIGOPEDIDO
            WHERE
                i.DATADELETE IS NULL
                AND p.DATADELETE IS NULL
                AND p.CODIGOCOLABORADOR = ?
                AND p.DATAABERTURA >= ?
                AND p.DATAABERTURA < ?
            """,
            Integer.class,
            id,
            inicio,
            fim
        );

        Double custo = this.jdbc.queryForObject(
            """
            SELECT ABS(SUM(em.QTD * pd.PRECOCUSTO))
            FROM ESTOQUEMOVIMENTACAO em
            JOIN ITENSPEDIDO i ON i.CODIGO = em.CODIGOITEMPEDIDO
            JOIN PEDIDOS p ON p.CODIGO = i.CODIGOPEDIDO
            JOIN PRODUTODETALHE pd ON pd.CODIGO = em.CODIGOPRODUTODETALHE
            WHERE
                em.DATADELETE IS NULL
                AND em.TIPO = 'S'
                AND i.DATADELETE IS NULL
                AND p.DATADELETE IS NULL
                AND p.CODIGOCOLABORADOR = ?
                AND p.DATAABERTURA >= ?
                AND p.DATAABERTURA < ?
            """,
            Double.class,
            id,
            inicio,
            fim
        );

        // Produtos mais vendidos pelo garçom, com a etiqueta (categoria) do produto.
        // Caminho: ITENSPEDIDO → PRODUTODETALHE → PRODUTOS → ETIQUETAS.
        List<ProdutoRankingDTO> produtos = this.jdbc.query(
            """
            SELECT
                FIRST 20
                MIN(i.NOMEPRODUTO) AS nomeproduto,
                MIN(e.DESCRICAO) AS etiqueta,
                SUM(i.QUANTIDADE) AS quantidade
            FROM ITENSPEDIDO i
            JOIN PEDIDOS p ON p.CODIGO = i.CODIGOPEDIDO
            LEFT JOIN PRODUTODETALHE pd ON pd.CODIGO = i.CODIGOPRODUTODETALHE
            LEFT JOIN PRODUTOS pr ON pr.CODIGO = pd.CODIGOPRODUTO
            LEFT JOIN ETIQUETAS e ON e.CODIGO = pr.CODIGOETIQUETA
            WHERE
                i.DATADELETE IS NULL
                AND p.DATADELETE IS NULL
                AND p.CODIGOCOLABORADOR = ?
                AND p.DATAABERTURA >= ?
                AND p.DATAABERTURA < ?
            GROUP BY i.CODIGOPRODUTODETALHE
            ORDER BY quantidade DESC
            """,
            (rs, rowNum) -> new ProdutoRankingDTO(
                rs.getString("nomeproduto"),
                rs.getString("etiqueta"),
                rs.getDouble("quantidade")
            ),
            id,
            inicio,
            fim
        );

        double faturamento = stats != null ? stats.faturamento() : 0.0;
        double taxa = stats != null ? stats.taxa() : 0.0;
        int comandas = stats != null ? stats.comandas() : 0;
        double custoTotal = custo != null ? custo : 0.0;
        int itensLancados = itens != null ? itens : 0;

        return new GarcomDetalhesDTO(
            id,
            nome,
            taxa,
            faturamento,
            custoTotal,
            faturamento - custoTotal,
            comandas,
            itensLancados,
            produtos
        );
    }
}
