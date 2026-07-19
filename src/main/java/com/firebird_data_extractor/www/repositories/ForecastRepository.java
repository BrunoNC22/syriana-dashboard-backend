package com.firebird_data_extractor.www.repositories;

import java.sql.Date;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Séries diárias históricas para o motor de previsão ({@code ForecastService}).
 *
 * Segue a altitude do projeto: o SQL agrega por dia (GROUP BY) e devolve só os
 * dias com movimento; o preenchimento de lacunas (dias fechados = 0) e o
 * cômputo ficam em Java (ver {@code PrevisaoService}).
 *
 * <p>As duas métricas vêm de PEDIDOS por {@code DATAABERTURA}: faturamento é
 * {@code SUM(VALORTOTAL)} (confiável só no histórico recente — VALORTOTAL é NULL
 * em ~48% dos pedidos antigos, por isso a janela de treino é curta) e pedidos é
 * {@code COUNT(*)} (confiável em toda a série).
 */
@Repository
public class ForecastRepository {

    private final JdbcTemplate jdbc;

    public ForecastRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Faturamento diário (SUM VALORTOTAL) no intervalo; só dias com pedidos. */
    public Map<LocalDate, Double> getSerieDiariaFaturamento(LocalDate dataInicio, LocalDate dataFim) {
        return serieDiaria(
            """
            SELECT
                CAST(DATAABERTURA AS DATE) AS dia,
                SUM(VALORTOTAL) AS valor
            FROM PEDIDOS
            WHERE
                DATADELETE IS NULL
                AND DATAABERTURA >= ?
                AND DATAABERTURA < ?
            GROUP BY CAST(DATAABERTURA AS DATE)
            """,
            dataInicio, dataFim
        );
    }

    /** Nº de pedidos por dia (COUNT) no intervalo; só dias com pedidos. */
    public Map<LocalDate, Double> getSerieDiariaPedidos(LocalDate dataInicio, LocalDate dataFim) {
        return serieDiaria(
            """
            SELECT
                CAST(DATAABERTURA AS DATE) AS dia,
                COUNT(*) AS valor
            FROM PEDIDOS
            WHERE
                DATADELETE IS NULL
                AND DATAABERTURA >= ?
                AND DATAABERTURA < ?
            GROUP BY CAST(DATAABERTURA AS DATE)
            """,
            dataInicio, dataFim
        );
    }

    private Map<LocalDate, Double> serieDiaria(String sql, LocalDate dataInicio, LocalDate dataFim) {
        LocalDateTime inicio = dataInicio.atStartOfDay();
        LocalDateTime fim = dataFim.plusDays(1).atStartOfDay();

        Map<LocalDate, Double> serie = new HashMap<>();
        this.jdbc.query(
            sql,
            rs -> {
                Date dia = rs.getDate("dia");
                double valor = rs.getDouble("valor");
                if (!rs.wasNull() && dia != null) {
                    serie.put(dia.toLocalDate(), valor);
                }
            },
            inicio, fim
        );
        return serie;
    }
}
