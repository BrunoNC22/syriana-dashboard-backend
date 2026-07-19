package com.firebird_data_extractor.www.tests;

import java.nio.file.Path;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.firebird_data_extractor.www.services.ForecastService;
import com.firebird_data_extractor.www.services.ForecastService.Metodo;
import com.firebird_data_extractor.www.services.ForecastService.PontoPrevisto;
import com.firebird_data_extractor.www.services.ForecastService.Previsao;
import com.firebird_data_extractor.www.services.HolidayCalendarService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Testes do motor de previsão. O {@code @TempDir} garante que o
 * {@link HolidayCalendarService} roda só com o calendário padrão (sem
 * {@code feriados.json}).
 */
class ForecastServiceTest {

    @TempDir
    Path tmpDir;

    private ForecastService service() {
        return new ForecastService(new HolidayCalendarService(tmpDir));
    }

    // ---------- Holt-Winters vence o naïve quando há tendência ----------

    @Test
    void holtWintersGanhaComTendenciaMaisSazonalidade() {
        // Série com padrão semanal fixo + tendência de alta: o naïve sazonal
        // (repete o mesmo dia da semana anterior) carrega um viés constante do
        // crescimento; o Holt-Winters capta a tendência e erra menos.
        Map<LocalDate, Double> serie = new HashMap<>();
        LocalDate inicio = LocalDate.of(2025, 1, 6); // segunda-feira
        double[] semana = {60, 70, 80, 90, 120, 180, 150}; // seg..dom
        for (int t = 0; t < 160; t++) {
            LocalDate dia = inicio.plusDays(t);
            double valor = 100 + 0.8 * t + semana[dia.getDayOfWeek().getValue() - 1];
            serie.put(dia, valor);
        }

        Previsao p = service().prever(serie, 14);

        assertEquals(Metodo.HOLT_WINTERS, p.metodo(), "H-W deveria vencer o naïve");
        assertTrue(p.mape() < 5.0, "MAPE do backtest deveria ser baixo, foi " + p.mape());
        assertEquals(14, p.pontos().size());
        assertEquals(inicio.plusDays(160), p.pontos().get(0).data(), "1º ponto é o dia seguinte ao fim");
    }

    // ---------- Fallback para naïve com histórico curto ----------

    @Test
    void historicoCurtoCaiParaNaive() {
        // 10 dias: insuficiente para Holt-Winters (precisa de ~2 ciclos) → naïve.
        Map<LocalDate, Double> serie = new HashMap<>();
        LocalDate inicio = LocalDate.of(2025, 3, 3);
        for (int t = 0; t < 10; t++) {
            serie.put(inicio.plusDays(t), 100.0 + (t % 7) * 10);
        }

        Previsao p = service().prever(serie, 7);

        assertEquals(Metodo.NAIVE_SAZONAL, p.metodo());
        assertEquals(7, p.pontos().size());
        assertEquals(inicio.plusDays(10), p.pontos().get(0).data());
    }

    // ---------- Histórico muito curto: previsão plana ----------

    @Test
    void historicoMuitoCurtoPrevisaoPlana() {
        // 5 dias (< m+1) → média plana, banda pelo desvio, sem MAPE.
        Map<LocalDate, Double> serie = new HashMap<>();
        LocalDate inicio = LocalDate.of(2025, 3, 3);
        for (int t = 0; t < 5; t++) {
            serie.put(inicio.plusDays(t), 200.0); // constante → média 200, σ 0
        }

        Previsao p = service().prever(serie, 3);

        assertEquals(3, p.pontos().size());
        assertTrue(Double.isNaN(p.mape()), "sem histórico p/ backtest → MAPE NaN");
        for (PontoPrevisto ponto : p.pontos()) {
            if (!ponto.feriado()) {
                assertEquals(200.0, ponto.valor(), 1e-6, "previsão plana pela média");
            }
        }
    }

    // ---------- Uplift de feriado reaplicado na previsão ----------

    @Test
    void feriadoNoHorizonteRecebeUplift() {
        // Baseline 100/dia; Dia das Mães de 2024 e 2025 rendeu 250 (2,5×). O
        // horizonte alcança o Dia das Mães/2026 (10/05, domingo): o ponto deve
        // vir marcado como feriado e bem acima de um dia normal (~100 × 2,5).
        Map<LocalDate, Double> serie = new HashMap<>();
        LocalDate d = LocalDate.of(2024, 1, 1);
        LocalDate fim = LocalDate.of(2026, 5, 3); // domingo, ~1 semana antes do alvo
        while (!d.isAfter(fim)) {
            serie.put(d, 100.0);
            d = d.plusDays(1);
        }
        serie.put(LocalDate.of(2024, 5, 12), 250.0); // Dia das Mães/2024
        serie.put(LocalDate.of(2025, 5, 11), 250.0); // Dia das Mães/2025

        Previsao p = service().prever(serie, 10); // alcança 13/05... cobre 10/05

        PontoPrevisto diaDasMaes = pontoEm(p.pontos(), LocalDate.of(2026, 5, 10));
        PontoPrevisto diaComum = pontoEm(p.pontos(), LocalDate.of(2026, 5, 4)); // segunda comum

        assertEquals(DayOfWeek.SUNDAY, LocalDate.of(2026, 5, 10).getDayOfWeek());
        assertTrue(diaDasMaes.feriado(), "10/05/2026 é Dia das Mães");
        assertFalse(diaComum.feriado(), "04/05/2026 é dia comum");
        assertTrue(
            diaDasMaes.valor() > 1.8 * diaComum.valor(),
            "Dia das Mães deveria receber o uplift (~2,5×): " + diaDasMaes.valor()
                + " vs comum " + diaComum.valor()
        );
        assertTrue(
            diaDasMaes.limiteSuperior() > diaDasMaes.valor(),
            "banda de confiança em torno do valor"
        );
    }

    // ---------- helpers ----------

    private static PontoPrevisto pontoEm(List<PontoPrevisto> pontos, LocalDate data) {
        for (PontoPrevisto ponto : pontos) {
            if (ponto.data().equals(data)) {
                return ponto;
            }
        }
        throw new AssertionError("ponto não previsto para " + data);
    }
}
