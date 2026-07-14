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

import com.firebird_data_extractor.www.dto.FaturamentoPorDiaDTO;
import com.firebird_data_extractor.www.dto.FaturamentoPorHoraDTO;
import com.firebird_data_extractor.www.dto.FaturamentoPorProduto;
import com.firebird_data_extractor.www.dto.InsumoQueMaisCustouDTO;
import com.firebird_data_extractor.www.dto.InsumoDoProdutoDTO;
import com.firebird_data_extractor.www.dto.ProdutoDetalhesDTO;
import com.firebird_data_extractor.www.dto.ProdutoMaisVendidoDTO;

@Repository
public class DashboardRepository {

    private final JdbcTemplate jdbc;

    public DashboardRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private LocalDateTime getDataInicio(int ano, Integer mes) {
        if (mes != null) {
            return LocalDate.of(ano, mes, 1).atStartOfDay();
        }

        return LocalDate.of(ano, 1, 1).atStartOfDay();
    }

    private LocalDateTime getDataFim(int ano, Integer mes) {
        if (mes != null) {
            return LocalDate.of(ano, mes, 1)
                .plusMonths(1)
                .atStartOfDay();
        }

        return LocalDate.of(ano, 1, 1)
            .plusYears(1)
            .atStartOfDay();
    }

    public Double getTotalVendido(int ano, Integer mes) {

        LocalDateTime inicio = getDataInicio(ano, mes);
        LocalDateTime fim = getDataFim(ano, mes);

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

    public int getQuantidadeDePedidos(int ano, Integer mes) {

        LocalDateTime inicio = getDataInicio(ano, mes);
        LocalDateTime fim = getDataFim(ano, mes);

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

    public List<ProdutoMaisVendidoDTO> getProdutosMaisVendidos(int ano, Integer mes) {

        LocalDateTime inicio = getDataInicio(ano, mes);
        LocalDateTime fim = getDataFim(ano, mes);

        return this.jdbc.query(
            """
            SELECT
                FIRST 10
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

    public List<FaturamentoPorDiaDTO> getFaturamentoPorDia(int ano, Integer mes) {

        Map<LocalDate, FaturamentoPorDiaDTO> faturamentos = new HashMap<>();

        LocalDate inicioPeriodo;
        LocalDate fimPeriodo;

        if (mes != null) {
            inicioPeriodo = LocalDate.of(ano, mes, 1);
            fimPeriodo = inicioPeriodo.withDayOfMonth(
                inicioPeriodo.lengthOfMonth()
            );
        } else {
            inicioPeriodo = LocalDate.of(ano, 1, 1);
            fimPeriodo = LocalDate.of(ano, 12, 31);
        }

        for (
            LocalDate data = inicioPeriodo;
            !data.isAfter(fimPeriodo);
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

        LocalDateTime inicio = getDataInicio(ano, mes);
        LocalDateTime fim = getDataFim(ano, mes);

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

    public List<FaturamentoPorProduto> getFaturamentoPorProduto(
        int ano,
        Integer mes
    ) {

        LocalDateTime inicio = getDataInicio(ano, mes);
        LocalDateTime fim = getDataFim(ano, mes);

        return this.jdbc.query(
            """
            SELECT 
                FIRST 10
                i.NOMEPRODUTO,
                SUM(i.VALORTOTAL) AS faturamento
            FROM ITENSPEDIDO i
            WHERE
                i.DATADELETE IS NULL
                AND i.DATAHORACADASTRO >= ?
                AND i.DATAHORACADASTRO < ?
            GROUP BY i.NOMEPRODUTO
            ORDER BY faturamento DESC
            """,
            (rs, rowNum) -> new FaturamentoPorProduto(
                rs.getString("NOMEPRODUTO"),
                rs.getDouble("faturamento")
            ),
            inicio,
            fim
        );
    }

    public List<FaturamentoPorHoraDTO> getMediaFaturamentoPorHoraPorDiaSemana(
        int diaSemana,
        int ano,
        Integer mes
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

        LocalDateTime inicio = getDataInicio(ano, mes);
        LocalDateTime fim = getDataFim(ano, mes);

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
        int ano,
        Integer mes
    ) {

        LocalDateTime inicio = getDataInicio(ano, mes);
        LocalDateTime fim = getDataFim(ano, mes);

        Double result = this.jdbc.queryForObject(
            """
            SELECT
                SUM(
                    ABS(QTD * VALORUNITARIO)
                ) AS total_gasto
            FROM ESTOQUEMOVIMENTACAO
            WHERE
                DATADELETE IS NULL
                AND TIPO = 'S'
                AND DATAINSERT >= ?
                AND DATAINSERT < ?
            """,
            Double.class,
            inicio,
            fim
        );

        return result != null ? result : 0.0;
    }

    public ProdutoDetalhesDTO getProdutoDetalhes(
        Integer idProdutoDetalhe,
        int ano,
        Integer mes
    ) {
        LocalDateTime inicio = getDataInicio(ano, mes);
        LocalDateTime fim = getDataFim(ano, mes);

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
            (rs, _) -> new SalesStats(
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
        int ano,
        Integer mes
    ) {
        LocalDateTime inicio = getDataInicio(ano, mes);
        LocalDateTime fim = getDataFim(ano, mes);

        return this.jdbc.query(
            """
            SELECT 
                first 10
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
}
