package com.firebird_data_extractor.www.repositories;

import java.sql.Date;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.firebird_data_extractor.www.dto.DiaSerieDTO;
import com.firebird_data_extractor.www.dto.FaturamentoPorDiaDTO;
import com.firebird_data_extractor.www.dto.FaturamentoPorHoraDTO;
import com.firebird_data_extractor.www.dto.FaturamentoPorProduto;
import com.firebird_data_extractor.www.dto.InsumoQueMaisCustouDTO;
import com.firebird_data_extractor.www.dto.InsumoDoProdutoDTO;
import com.firebird_data_extractor.www.dto.InsumoDetalhesDTO;
import com.firebird_data_extractor.www.dto.ProdutoDetalhesDTO;
import com.firebird_data_extractor.www.dto.ProdutoMaisVendidoDTO;
import com.firebird_data_extractor.www.dto.ProdutoQueUsaInsumoDTO;

@Repository
public class DashboardRepository {

    private final JdbcTemplate jdbc;

    public DashboardRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Double getTotalVendido(LocalDate dataInicio, LocalDate dataFim) {

        LocalDateTime inicio = dataInicio.atStartOfDay();
        LocalDateTime fim = dataFim.plusDays(1).atStartOfDay();

        Double result = this.jdbc.queryForObject(
            """
            SELECT SUM(VALORTOTAL)
            FROM PEDIDOS
            WHERE 
                DATADELETE IS NULL
                AND DATAABERTURA >= ?
                AND DATAABERTURA < ?
            """,
            Double.class,
            inicio,
            fim
        );

        return result != null ? result : 0.0;
    }

    public int getQuantidadeDePedidos(LocalDate dataInicio, LocalDate dataFim) {

        LocalDateTime inicio = dataInicio.atStartOfDay();
        LocalDateTime fim = dataFim.plusDays(1).atStartOfDay();

        Integer result = this.jdbc.queryForObject(
            """
            SELECT COUNT(*)
            FROM PEDIDOS
            WHERE 
                DATADELETE IS NULL
                AND DATAABERTURA >= ?
                AND DATAABERTURA < ?
            """,
            Integer.class,
            inicio,
            fim
        );

        return result != null ? result : 0;
    }

    public List<ProdutoMaisVendidoDTO> getProdutosMaisVendidos(LocalDate dataInicio, LocalDate dataFim) {

        LocalDateTime inicio = dataInicio.atStartOfDay();
        LocalDateTime fim = dataFim.plusDays(1).atStartOfDay();

        return this.jdbc.query(
            """
            SELECT
                i.CODIGOPRODUTODETALHE,
                MIN(i.NOMEPRODUTO) AS nomeproduto,
                SUM(i.QUANTIDADE) AS total_vendido
            FROM ITENSPEDIDO i
            WHERE
                i.DATADELETE IS NULL
                AND i.DATAHORACADASTRO >= ?
                AND i.DATAHORACADASTRO < ?
            GROUP BY i.CODIGOPRODUTODETALHE
            ORDER BY total_vendido DESC
            """,
            (rs, rowNum) -> new ProdutoMaisVendidoDTO(
                String.valueOf(rs.getInt("CODIGOPRODUTODETALHE")),
                rs.getString("nomeproduto"),
                rs.getDouble("total_vendido")
            ),
            inicio,
            fim
        );
    }

    public List<FaturamentoPorDiaDTO> getFaturamentoPorDia(LocalDate dataInicio, LocalDate dataFim) {

        Map<LocalDate, FaturamentoPorDiaDTO> faturamentos = new HashMap<>();

        for (
            LocalDate data = dataInicio;
            !data.isAfter(dataFim);
            data = data.plusDays(1)
        ) {

            faturamentos.put(
                data,
                new FaturamentoPorDiaDTO(
                    data,
                    0.0
                )
            );
        }

        LocalDateTime inicio = dataInicio.atStartOfDay();
        LocalDateTime fim = dataFim.plusDays(1).atStartOfDay();

        this.jdbc.query(
            """
            SELECT 
                CAST(DATAABERTURA AS DATE) AS dia,
                SUM(VALORTOTAL) AS faturamento
            FROM PEDIDOS
            WHERE
                DATADELETE IS NULL
                AND DATAABERTURA >= ?
                AND DATAABERTURA < ?
            GROUP BY dia
            """,
            rs -> {

                Date data = rs.getDate("dia");

                LocalDate dia = data.toLocalDate();

                Double faturamento = rs.getDouble("faturamento");

                if (rs.wasNull()) {
                    faturamento = 0.0;
                }

                faturamentos.put(
                    dia,
                    new FaturamentoPorDiaDTO(
                        dia,
                        faturamento
                    )
                );
            },
            inicio,
            fim
        );

        return faturamentos.values()
            .stream()
            .sorted(
                Comparator.comparing(FaturamentoPorDiaDTO::dia)
            )
            .toList();
    }

    /**
     * Série diária com todas as métricas (faturamento, pedidos, custo, lucro)
     * para o intervalo, com os dias vazios preenchidos com zero. Usada pelas
     * sparklines dos KPIs. Duas queries agregadas (PEDIDOS e ESTOQUEMOVIMENTACAO)
     * mescladas por dia em Java, evitando a multiplicação de linhas de um join.
     */
    public List<DiaSerieDTO> getSeriesDiarias(LocalDate dataInicio, LocalDate dataFim) {

        // acumulador por dia: [faturamento, pedidos, custo]
        Map<LocalDate, double[]> acc = new HashMap<>();

        for (
            LocalDate data = dataInicio;
            !data.isAfter(dataFim);
            data = data.plusDays(1)
        ) {
            acc.put(data, new double[] { 0.0, 0.0, 0.0 });
        }

        LocalDateTime inicio = dataInicio.atStartOfDay();
        LocalDateTime fim = dataFim.plusDays(1).atStartOfDay();

        this.jdbc.query(
            """
            SELECT
                CAST(DATAABERTURA AS DATE) AS dia,
                SUM(VALORTOTAL) AS faturamento,
                COUNT(*) AS pedidos
            FROM PEDIDOS
            WHERE
                DATADELETE IS NULL
                AND DATAABERTURA >= ?
                AND DATAABERTURA < ?
            GROUP BY dia
            """,
            rs -> {

                LocalDate dia = rs.getDate("dia").toLocalDate();

                double[] a = acc.get(dia);

                if (a == null) {
                    return;
                }

                double faturamento = rs.getDouble("faturamento");

                if (rs.wasNull()) {
                    faturamento = 0.0;
                }

                a[0] = faturamento;
                a[1] = rs.getInt("pedidos");
            },
            inicio,
            fim
        );

        this.jdbc.query(
            """
            SELECT
                CAST(em.DATAINSERT AS DATE) AS dia,
                ABS(SUM(em.QTD * pd.PRECOCUSTO)) AS custo
            FROM ESTOQUEMOVIMENTACAO em
            JOIN PRODUTODETALHE pd
                ON pd.CODIGO = em.CODIGOPRODUTODETALHE
            WHERE
                em.DATADELETE IS NULL
                AND em.TIPO = 'S'
                AND em.DATAINSERT >= ?
                AND em.DATAINSERT < ?
            GROUP BY dia
            """,
            rs -> {

                LocalDate dia = rs.getDate("dia").toLocalDate();

                double[] a = acc.get(dia);

                if (a == null) {
                    return;
                }

                double custo = rs.getDouble("custo");

                if (rs.wasNull()) {
                    custo = 0.0;
                }

                a[2] = custo;
            },
            inicio,
            fim
        );

        return acc.entrySet()
            .stream()
            .sorted(Map.Entry.comparingByKey())
            .map(entry -> {

                double faturamento = entry.getValue()[0];
                int pedidos = (int) entry.getValue()[1];
                double custo = entry.getValue()[2];

                return new DiaSerieDTO(
                    entry.getKey(),
                    faturamento,
                    pedidos,
                    custo,
                    faturamento - custo
                );
            })
            .toList();
    }

    public List<FaturamentoPorProduto> getFaturamentoPorProduto(
        LocalDate dataInicio,
        LocalDate dataFim
    ) {

        LocalDateTime inicio = dataInicio.atStartOfDay();
        LocalDateTime fim = dataFim.plusDays(1).atStartOfDay();

        return this.jdbc.query(
            """
            SELECT
                i.CODIGOPRODUTODETALHE,
                MIN(i.NOMEPRODUTO) AS nomeproduto,
                SUM(i.VALORTOTAL) AS faturamento
            FROM ITENSPEDIDO i
            WHERE
                i.DATADELETE IS NULL
                AND i.DATAHORACADASTRO >= ?
                AND i.DATAHORACADASTRO < ?
            GROUP BY i.CODIGOPRODUTODETALHE
            ORDER BY faturamento DESC
            """,
            (rs, rowNum) -> new FaturamentoPorProduto(
                String.valueOf(rs.getInt("CODIGOPRODUTODETALHE")),
                rs.getString("nomeproduto"),
                rs.getDouble("faturamento")
            ),
            inicio,
            fim
        );
    }

    public List<FaturamentoPorHoraDTO> getMediaFaturamentoPorHoraPorDiaSemana(
        int diaSemana,
        LocalDate dataInicio,
        LocalDate dataFim
    ) {

        Map<Integer, FaturamentoPorHoraDTO> faturamentos =
            new HashMap<>();

        for (int hora = 0; hora < 24; hora++) {

            faturamentos.put(
                hora,
                new FaturamentoPorHoraDTO(
                    String.format("%02d:00", hora),
                    0.0
                )
            );
        }

        LocalDateTime inicio = dataInicio.atStartOfDay();
        LocalDateTime fim = dataFim.plusDays(1).atStartOfDay();

        this.jdbc.query(
            """
            SELECT 
                hora,
                AVG(faturamento_por_dia) AS media_faturamento
            FROM (
                SELECT 
                    CAST(DATAABERTURA AS DATE) AS dia,
                    EXTRACT(HOUR FROM DATAABERTURA) AS hora,
                    SUM(VALORTOTAL) AS faturamento_por_dia
                FROM PEDIDOS
                WHERE 
                    DATADELETE IS NULL
                    AND EXTRACT(WEEKDAY FROM DATAABERTURA) = ?
                    AND DATAABERTURA >= ?
                    AND DATAABERTURA < ?
                GROUP BY dia, hora
            ) sub
            GROUP BY hora
            """,
            rs -> {

                int hora = rs.getInt("hora");

                Double media = rs.getDouble(
                    "media_faturamento"
                );

                if (rs.wasNull()) {
                    media = 0.0;
                }

                faturamentos.put(
                    hora,
                    new FaturamentoPorHoraDTO(
                        String.format("%02d:00", hora),
                        media
                    )
                );
            },
            diaSemana,
            inicio,
            fim
        );

        return faturamentos.values()
            .stream()
            .sorted(
                Comparator.comparing(
                    FaturamentoPorHoraDTO::hora
                )
            )
            .toList();
    }

    public Double getTotalGastoIngredientes(
        LocalDate dataInicio,
        LocalDate dataFim
    ) {

        LocalDateTime inicio = dataInicio.atStartOfDay();
        LocalDateTime fim = dataFim.plusDays(1).atStartOfDay();

        Double result = this.jdbc.queryForObject(
            """
            SELECT
                ABS(SUM(em.QTD * pd.PRECOCUSTO)) AS total_gasto
            FROM ESTOQUEMOVIMENTACAO em
            JOIN PRODUTODETALHE pd
                ON pd.CODIGO = em.CODIGOPRODUTODETALHE
            WHERE
                em.DATADELETE IS NULL
                AND em.TIPO = 'S'
                AND em.DATAINSERT >= ?
                AND em.DATAINSERT < ?
            """,
            Double.class,
            inicio,
            fim
        );

        return result != null ? result : 0.0;
    }

    public Double getLucroTotal(LocalDate dataInicio, LocalDate dataFim) {

        Double faturamento = this.getTotalVendido(dataInicio, dataFim);
        Double custoInsumos = this.getTotalGastoIngredientes(dataInicio, dataFim);

        return faturamento - custoInsumos;
    }

    public ProdutoDetalhesDTO getProdutoDetalhes(
        Integer idProdutoDetalhe,
        LocalDate dataInicio,
        LocalDate dataFim
    ) {
        LocalDateTime inicio = dataInicio.atStartOfDay();
        LocalDateTime fim = dataFim.plusDays(1).atStartOfDay();

        record SalesStats(String nomeProduto, Double quantidadeVendida, Double faturamento) {}

        SalesStats stats = this.jdbc.queryForObject(
            """
            SELECT
                MIN(i.NOMEPRODUTO) AS nomeproduto,
                SUM(i.QUANTIDADE) AS qtd_vendida,
                SUM(i.VALORUNITARIO * i.QUANTIDADE) AS faturamento_total
            FROM ITENSPEDIDO i
            WHERE
                i.DATADELETE IS NULL
                AND i.CODIGOPRODUTODETALHE = ?
                AND i.DATAHORACADASTRO >= ?
                AND i.DATAHORACADASTRO < ?
            """,
            (rs, rowNum) -> new SalesStats(
                rs.getString("nomeproduto"),
                rs.getDouble("qtd_vendida"),
                rs.getDouble("faturamento_total")
            ),
            idProdutoDetalhe, inicio, fim
        );

        List<InsumoDoProdutoDTO> insumos = this.jdbc.query(
            """
            SELECT
                pdt.NOME AS nome_insumo,
                u.SIGLA AS sigla_unidade,
                ABS(SUM(em.QTD)) AS qtd_total,
                ABS(SUM(em.QTD * pd.PRECOCUSTO)) AS custo_total
            FROM ITENSPEDIDO i
            JOIN ESTOQUEMOVIMENTACAO em ON em.CODIGOITEMPEDIDO = i.CODIGO
            JOIN PRODUTODETALHE pd ON pd.CODIGO = em.CODIGOPRODUTODETALHE
            JOIN PRODUTOS pdt ON pdt.CODIGO = pd.CODIGOPRODUTO
            LEFT JOIN UNIDADECOMERCIALIZACAO u ON u.CODIGO = pdt.CODIGOUNIDADECOMERCIAL
            WHERE
                i.DATADELETE IS NULL
                AND i.CODIGOPRODUTODETALHE = ?
                AND i.DATAHORACADASTRO >= ?
                AND i.DATAHORACADASTRO < ?
                AND em.DATADELETE IS NULL
                AND em.TIPO = 'S'
            GROUP BY pdt.CODIGO, pdt.NOME, u.SIGLA
            ORDER BY custo_total DESC
            """,
            (rs, rowNum) -> new InsumoDoProdutoDTO(
                rs.getString("nome_insumo"),
                rs.getString("sigla_unidade"),
                rs.getDouble("qtd_total"),
                rs.getDouble("custo_total")
            ),
            idProdutoDetalhe, inicio, fim
        );

        double quantidadeVendida = stats != null && stats.quantidadeVendida() != null
            ? stats.quantidadeVendida() : 0.0;
        double faturamento = stats != null && stats.faturamento() != null
            ? stats.faturamento() : 0.0;
        String nomeProduto = stats != null ? stats.nomeProduto() : null;

        double custoTotal = insumos.stream()
            .mapToDouble(InsumoDoProdutoDTO::custoTotal)
            .sum();
        double lucro = faturamento - custoTotal;
        double margemLucro = faturamento > 0 ? (lucro / faturamento) * 100.0 : 0.0;

        return new ProdutoDetalhesDTO(
            nomeProduto,
            quantidadeVendida,
            faturamento,
            custoTotal,
            lucro,
            margemLucro,
            insumos
        );
    }

    public List<InsumoQueMaisCustouDTO> getInsumosQueMaisCustaram(
        LocalDate dataInicio,
        LocalDate dataFim
    ) {
        LocalDateTime inicio = dataInicio.atStartOfDay();
        LocalDateTime fim = dataFim.plusDays(1).atStartOfDay();

        return this.jdbc.query(
            """
            SELECT
                pdt.CODIGO,
                pdt.NOME,
                abs(sum(em.QTD)) as quantidade,
                u.SIGLA,
                pd.PRECOCUSTO,
                abs(SUM(em.QTD * pd.PRECOCUSTO)) AS valor_total
            FROM ESTOQUEMOVIMENTACAO AS em
            left join PRODUTODETALHE pd
                on pd.CODIGO = em.CODIGOPRODUTODETALHE
            left join PRODUTOS pdt
                on pdt.CODIGO = pd.CODIGOPRODUTO
            left join UNIDADECOMERCIALIZACAO u
                on u.CODIGO = pdt.CODIGOUNIDADECOMERCIAL
            where
                em.DATADELETE is null
                and em.DATAINSERT >= ?
                and em.DATAINSERT < ?
            group by pdt.CODIGO, pdt.NOME, u.SIGLA, pd.PRECOCUSTO
            order by VALOR_TOTAL desc
            """,
            (rs, rowNum) -> new InsumoQueMaisCustouDTO(
                String.valueOf(rs.getInt("CODIGO")),
                rs.getString("NOME"),
                rs.getString("SIGLA"),
                rs.getDouble("quantidade"),
                rs.getDouble("PRECOCUSTO"),
                rs.getDouble("valor_total")
            ),
            inicio,
            fim
        );
    }

    public InsumoDetalhesDTO getInsumoDetalhes(
        Integer idInsumo,
        LocalDate dataInicio,
        LocalDate dataFim
    ) {
        LocalDateTime inicio = dataInicio.atStartOfDay();
        LocalDateTime fim = dataFim.plusDays(1).atStartOfDay();

        record InsumoStats(
            String nome,
            String sigla,
            Double quantidadeTotal,
            Double custoTotal,
            Integer movimentacoes
        ) {}

        InsumoStats stats = this.jdbc.queryForObject(
            """
            SELECT
                MIN(pdt.NOME) AS nome,
                MIN(u.SIGLA) AS sigla,
                ABS(SUM(em.QTD)) AS qtd_total,
                ABS(SUM(em.QTD * pd.PRECOCUSTO)) AS custo_total,
                COUNT(*) AS movimentacoes
            FROM ESTOQUEMOVIMENTACAO em
            JOIN PRODUTODETALHE pd ON pd.CODIGO = em.CODIGOPRODUTODETALHE
            JOIN PRODUTOS pdt ON pdt.CODIGO = pd.CODIGOPRODUTO
            LEFT JOIN UNIDADECOMERCIALIZACAO u ON u.CODIGO = pdt.CODIGOUNIDADECOMERCIAL
            WHERE
                em.DATADELETE IS NULL
                AND em.TIPO = 'S'
                AND pd.CODIGOPRODUTO = ?
                AND em.DATAINSERT >= ?
                AND em.DATAINSERT < ?
            """,
            (rs, rowNum) -> new InsumoStats(
                rs.getString("nome"),
                rs.getString("sigla"),
                rs.getDouble("qtd_total"),
                rs.getDouble("custo_total"),
                rs.getInt("movimentacoes")
            ),
            idInsumo, inicio, fim
        );

        List<ProdutoQueUsaInsumoDTO> produtos = this.jdbc.query(
            """
            SELECT
                FIRST 15
                i.CODIGOPRODUTODETALHE,
                MIN(i.NOMEPRODUTO) AS nome_produto,
                ABS(SUM(em.QTD)) AS qtd_usada,
                ABS(SUM(em.QTD * pd.PRECOCUSTO)) AS custo_total
            FROM ESTOQUEMOVIMENTACAO em
            JOIN PRODUTODETALHE pd ON pd.CODIGO = em.CODIGOPRODUTODETALHE
            JOIN ITENSPEDIDO i ON i.CODIGO = em.CODIGOITEMPEDIDO
            WHERE
                em.DATADELETE IS NULL
                AND em.TIPO = 'S'
                AND pd.CODIGOPRODUTO = ?
                AND em.DATAINSERT >= ?
                AND em.DATAINSERT < ?
                AND i.DATADELETE IS NULL
            GROUP BY i.CODIGOPRODUTODETALHE
            ORDER BY custo_total DESC
            """,
            (rs, rowNum) -> new ProdutoQueUsaInsumoDTO(
                String.valueOf(rs.getInt("CODIGOPRODUTODETALHE")),
                rs.getString("nome_produto"),
                rs.getDouble("qtd_usada"),
                rs.getDouble("custo_total")
            ),
            idInsumo, inicio, fim
        );

        String nome = stats != null ? stats.nome() : null;
        String sigla = stats != null ? stats.sigla() : null;
        double quantidadeTotal = stats != null && stats.quantidadeTotal() != null
            ? stats.quantidadeTotal() : 0.0;
        double custoTotal = stats != null && stats.custoTotal() != null
            ? stats.custoTotal() : 0.0;
        int movimentacoes = stats != null && stats.movimentacoes() != null
            ? stats.movimentacoes() : 0;
        double custoMedioPorUnidade = quantidadeTotal > 0 ? custoTotal / quantidadeTotal : 0.0;

        return new InsumoDetalhesDTO(
            nome,
            sigla,
            quantidadeTotal,
            custoTotal,
            custoMedioPorUnidade,
            produtos.size(),
            movimentacoes,
            produtos
        );
    }
}
