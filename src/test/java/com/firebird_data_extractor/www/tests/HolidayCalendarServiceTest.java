package com.firebird_data_extractor.www.tests;

import java.nio.file.Path;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.firebird_data_extractor.www.services.HolidayCalendarService;
import com.firebird_data_extractor.www.services.HolidayCalendarService.Feriado;
import com.firebird_data_extractor.www.services.HolidayCalendarService.Uplift;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Testes do calendário de feriados e dos fatores de uplift. Puro (sem Spring):
 * o diretório temporário garante que não há {@code feriados.json} — só o
 * calendário padrão entra em cena.
 */
class HolidayCalendarServiceTest {

    @TempDir
    Path tmpDir;

    private HolidayCalendarService service() {
        return new HolidayCalendarService(tmpDir);
    }

    // ---------- Datas com regra (n-ésimo dia da semana) ----------

    @Test
    void diaDasMaesCaiNoSegundoDomingoDeMaio() {
        HolidayCalendarService s = service();
        // 2025: 2º domingo de maio = 11/05 (domingo).
        assertTrue(s.isFeriado(LocalDate.of(2025, 5, 11)));
        assertEquals(DayOfWeek.SUNDAY, LocalDate.of(2025, 5, 11).getDayOfWeek());
        // 2026: 2º domingo de maio = 10/05 (domingo).
        assertTrue(s.isFeriado(LocalDate.of(2026, 5, 10)));
        assertEquals(DayOfWeek.SUNDAY, LocalDate.of(2026, 5, 10).getDayOfWeek());
        // O 1º domingo (04/05/2025) NÃO é Dia das Mães.
        Feriado maes2025 = feriadoPorNome(s, 2025, "Dia das Mães");
        assertEquals(LocalDate.of(2025, 5, 11), maes2025.data());
    }

    @Test
    void diaDosPaisCaiNoSegundoDomingoDeAgosto() {
        Feriado pais2025 = feriadoPorNome(service(), 2025, "Dia dos Pais");
        assertEquals(LocalDate.of(2025, 8, 10), pais2025.data());
        assertEquals(DayOfWeek.SUNDAY, pais2025.data().getDayOfWeek());
    }

    // ---------- Móveis por Páscoa (Computus) ----------

    @Test
    void pascoaPorComputus() {
        assertEquals(LocalDate.of(2025, 4, 20), HolidayCalendarService.pascoa(2025));
        assertEquals(LocalDate.of(2026, 4, 5), HolidayCalendarService.pascoa(2026));
    }

    @Test
    void feriadosMoveisDerivadosDaPascoa2025() {
        HolidayCalendarService s = service();
        // Páscoa 2025 = 20/04. Carnaval (terça) = -47d, Sexta Santa = -2d, Corpus = +60d.
        assertEquals(LocalDate.of(2025, 3, 4), feriadoPorNome(s, 2025, "Carnaval").data());
        assertEquals(LocalDate.of(2025, 4, 18), feriadoPorNome(s, 2025, "Sexta-feira Santa").data());
        assertEquals(LocalDate.of(2025, 6, 19), feriadoPorNome(s, 2025, "Corpus Christi").data());
    }

    // ---------- Fixos e não-feriados ----------

    @Test
    void reconheceFixosEIgnoraDiaComum() {
        HolidayCalendarService s = service();
        assertTrue(s.isFeriado(LocalDate.of(2025, 12, 25)), "Natal");
        assertTrue(s.isFeriado(LocalDate.of(2025, 6, 12)), "Dia dos Namorados");
        assertTrue(s.isFeriado(LocalDate.of(2025, 1, 1)), "Ano Novo");
        // Uma terça-feira qualquer não é feriado.
        assertFalse(s.isFeriado(LocalDate.of(2025, 3, 18)));
    }

    @Test
    void intervaloRetornaOrdenadoEComOsFeriadosDoMes() {
        List<Feriado> maio2025 = service()
            .feriadosNoIntervalo(LocalDate.of(2025, 5, 1), LocalDate.of(2025, 5, 31));
        // Maio/2025: Dia do Trabalho (01/05) e Dia das Mães (11/05).
        assertEquals(2, maio2025.size());
        assertEquals(LocalDate.of(2025, 5, 1), maio2025.get(0).data());
        assertEquals("Dia das Mães", maio2025.get(1).nome());
        // Ordenado por data.
        assertTrue(maio2025.get(0).data().isBefore(maio2025.get(1).data()));
    }

    // ---------- Uplift ----------

    @Test
    void upliftMediaDasOcorrenciasProprias() {
        // Série 2023-2026: todo domingo rende 100 (baseline). Dia das Mães dos três
        // anos anteriores rende 250 → uplift 2,5×. Alvo: Dia das Mães/2026.
        Map<LocalDate, Double> serie = serieDomingos(2023, 2026, 100.0);
        serie.put(LocalDate.of(2023, 5, 14), 250.0); // 2º dom maio/2023
        serie.put(LocalDate.of(2024, 5, 12), 250.0); // 2º dom maio/2024
        serie.put(LocalDate.of(2025, 5, 11), 250.0); // 2º dom maio/2025

        Uplift up = service().upliftDe(LocalDate.of(2026, 5, 10), serie);

        assertEquals(2.5, up.fator(), 1e-9);
        assertEquals(3, up.ocorrenciasUsadas());
        assertFalse(up.usouFallbackCategoria());
    }

    @Test
    void upliftCaiParaCategoriaQuandoHistoricoProprioEhCurto() {
        // Série só 2025-2026: Dia dos Pais tem só 1 ocorrência anterior (< mínimo),
        // então usa a média da categoria COMEMORATIVA — puxada pelo Dia das Mães
        // (250 → 2,5×) presente na mesma categoria.
        Map<LocalDate, Double> serie = serieDomingos(2025, 2026, 100.0);
        serie.put(LocalDate.of(2025, 5, 11), 250.0); // Dia das Mães/2025
        serie.put(LocalDate.of(2026, 5, 10), 250.0); // Dia das Mães/2026

        Uplift up = service().upliftDe(LocalDate.of(2026, 8, 9), serie); // Dia dos Pais/2026

        assertTrue(up.usouFallbackCategoria(), "deveria usar fallback de categoria");
        assertEquals(1, up.ocorrenciasUsadas(), "só 1 ocorrência própria antes do alvo");
        assertTrue(up.fator() > 1.0, "fator emprestado da categoria deve superar o normal");
    }

    @Test
    void upliftNeutroForaDeFeriado() {
        Map<LocalDate, Double> serie = serieDomingos(2024, 2026, 100.0);
        Uplift up = service().upliftDe(LocalDate.of(2026, 3, 17), serie); // terça comum
        assertEquals(1.0, up.fator(), 1e-9);
        assertEquals(0, up.ocorrenciasUsadas());
        assertFalse(up.usouFallbackCategoria());
    }

    // ---------- helpers ----------

    private static Feriado feriadoPorNome(HolidayCalendarService s, int ano, String nome) {
        for (Feriado f : s.feriadosNoIntervalo(LocalDate.of(ano, 1, 1), LocalDate.of(ano, 12, 31))) {
            if (f.nome().equals(nome)) {
                return f;
            }
        }
        throw new AssertionError("feriado não encontrado: " + nome + "/" + ano);
    }

    /** Série diária com todo domingo em [anoIni, anoFim] valendo {@code valor}. */
    private static Map<LocalDate, Double> serieDomingos(int anoIni, int anoFim, double valor) {
        Map<LocalDate, Double> serie = new HashMap<>();
        LocalDate d = LocalDate.of(anoIni, 1, 1);
        LocalDate fim = LocalDate.of(anoFim, 12, 31);
        while (!d.isAfter(fim)) {
            if (d.getDayOfWeek() == DayOfWeek.SUNDAY) {
                serie.put(d, valor);
            }
            d = d.plusDays(1);
        }
        return serie;
    }
}
