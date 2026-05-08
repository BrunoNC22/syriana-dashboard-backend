package com.firebird_data_extractor.www.repositories;

import java.sql.Date;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.firebird_data_extractor.www.dto.FaturamentoPorDiaDTO;
import com.firebird_data_extractor.www.dto.FaturamentoPorHoraDTO;
import com.firebird_data_extractor.www.dto.FaturamentoPorProduto;
import com.firebird_data_extractor.www.dto.ProdutoMaisVendidoDTO;

@Repository
public class DashboardRepository {

    private final JdbcTemplate jdbc;

    public DashboardRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Double getTotalVendido() {
        Double result = this.jdbc.queryForObject(
            """
            SELECT SUM(VALORTOTAL)
            FROM PEDIDOS
            WHERE DATADELETE IS NULL
            """,
            Double.class
        );

        return result != null ? result : 0.0;
    }

    public int getQuantidadeDePedidos() {
        return this.jdbc.queryForObject("""
            SELECT COUNT(*)
            FROM PEDIDOS
            WHERE DATADELETE IS NULL;
            """,
            Integer.class
        );
    }

    public List<ProdutoMaisVendidoDTO> getProdutosMaisVendidos() {
        return this.jdbc.query(
            """
            SELECT
                FIRST 10
                i.NOMEPRODUTO,
                SUM(i.QUANTIDADE) AS total_vendido
            FROM ITENSPEDIDO i
            WHERE i.DATADELETE IS NULL
            GROUP BY i.NOMEPRODUTO
            ORDER BY total_vendido DESC
            """,
            (rs, rowNum) -> new ProdutoMaisVendidoDTO(
                rs.getString("NOMEPRODUTO"),
                rs.getDouble("total_vendido")
            )
        );
    }

    public List<FaturamentoPorDiaDTO> getFaturamentoPorDia() {
        var faturamentosPorDia =  this.jdbc.query(
            """
            SELECT 
                FIRST 30
                CAST(DATAABERTURA AS DATE) AS dia,
                SUM(VALORTOTAL) AS faturamento
            FROM PEDIDOS
            WHERE DATADELETE IS NULL
            GROUP BY dia
            ORDER BY dia DESC
            """,
            (rs, rowNum) -> {
                Date data = rs.getDate("dia");

                Double faturamento = rs.getDouble("faturamento");
                if (rs.wasNull()) faturamento = 0.0;

                return new FaturamentoPorDiaDTO(
                    data.toLocalDate(),
                    faturamento
                );
            }
        );

        Collections.reverse(faturamentosPorDia);

        return faturamentosPorDia;
    }
    

    public List<FaturamentoPorProduto> getFaturamentoPorProduto() {
        return this.jdbc.query(
            """
            SELECT 
                FIRST 10
                i.NOMEPRODUTO,
                SUM(i.VALORTOTAL) AS faturamento
            FROM ITENSPEDIDO i
            WHERE i.DATADELETE is NULL
            GROUP BY i.NOMEPRODUTO
            ORDER BY faturamento DESC;
            """,
            (rs, rowNum) -> {
                return new FaturamentoPorProduto(rs.getString("NOMEPRODUTO"), rs.getDouble("faturamento"));
            }
        );
    }

    public List<FaturamentoPorHoraDTO> getMediaFaturamentoPorHoraPorDiaSemana(int diaSemana) {
        Map<Integer, FaturamentoPorHoraDTO> faturamentos = new HashMap<>();

        for (int hora = 0; hora < 24; hora++) {
            faturamentos.put(hora, new FaturamentoPorHoraDTO(hora, 0.0));
        }

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
                GROUP BY dia, hora
            ) sub
            GROUP BY hora
            """,
            new Object[]{diaSemana},
            rs -> {
                int hora = rs.getInt("hora");

                Double media = rs.getDouble("media_faturamento");
                if (rs.wasNull()) media = 0.0;

                faturamentos.put(hora, new FaturamentoPorHoraDTO(hora, media));
            }
        );

        return faturamentos.values().stream()
            .sorted(Comparator.comparing(FaturamentoPorHoraDTO::hora))
            .toList();
    }
}
