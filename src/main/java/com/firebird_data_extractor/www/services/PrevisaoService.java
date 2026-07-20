package com.firebird_data_extractor.www.services;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;

import com.firebird_data_extractor.www.dto.FeriadoDTO;
import com.firebird_data_extractor.www.dto.PontoPrevisaoDTO;
import com.firebird_data_extractor.www.dto.PrevisaoDTO;
import com.firebird_data_extractor.www.repositories.ForecastRepository;
import com.firebird_data_extractor.www.services.ForecastService.Previsao;
import com.firebird_data_extractor.www.services.HolidayCalendarService.Feriado;

/**
 * Orquestra a previsão exposta em {@code /analytics/previsao}: busca a série
 * diária, preenche as lacunas de calendário, chama o {@link ForecastService},
 * anexa os feriados do horizonte e monta o DTO — tudo por trás de um cache em
 * memória com TTL.
 *
 * <p><b>Cache</b>: {@link ConcurrentHashMap} chaveado por (métrica, horizonte,
 * último dia). App desktop single-user + previsão determinística → um cache
 * simples de ~10 min evita recomputar o mesmo pedido a cada re-render do
 * gráfico. Deliberadamente <b>não</b> usa {@code @Cacheable}/Caffeine (atrito
 * desnecessário com o GraalVM Native Image).
 */
@Service
public class PrevisaoService {

    /** Janela de treino: ~2 anos (104 semanas) — dentro do histórico confiável de VALORTOTAL. */
    private static final int LOOKBACK_DIAS = 728;

    /** Teto do horizonte pedido (1 ano), para não extrapolar sem limite. */
    private static final int HORIZONTE_MAX = 365;

    /** Acima disto a previsão é extrapolação de baixa confiança (aviso ao usuário). */
    private static final int AVISO_HORIZONTE_DIAS = 120;

    /** Abaixo disto o histórico é curto demais para confiar no padrão semanal. */
    private static final int AVISO_HISTORICO_MINIMO = 60;

    /** Validade de cada entrada do cache. */
    private static final long TTL_MS = 10 * 60 * 1000L;

    private final ForecastRepository repository;
    private final ForecastService forecast;
    private final HolidayCalendarService feriados;
    private final Clock clock;
    private final Map<String, Entrada> cache = new ConcurrentHashMap<>();

    public PrevisaoService(
        ForecastRepository repository,
        ForecastService forecast,
        HolidayCalendarService feriados,
        Clock clock
    ) {
        this.repository = repository;
        this.forecast = forecast;
        this.feriados = feriados;
        this.clock = clock;
    }

    /** Métrica prevista. */
    public enum Metrica { FATURAMENTO, PEDIDOS }

    private record Entrada(PrevisaoDTO dto, long expiraEm) {}

    /**
     * Prevê a métrica no horizonte pedido (com cache).
     *
     * @param metricaParam   "faturamento" ou "pedidos" (case-insensitive)
     * @param horizonteParam nº de dias, aceitando sufixo: {@code d}ias (padrão),
     *                       {@code s}emanas, {@code m}eses, {@code a}nos
     *                       (ex.: "30", "7d", "4s", "3m", "1a")
     */
    public PrevisaoDTO prever(String metricaParam, String horizonteParam) {
        Metrica metrica = parseMetrica(metricaParam);
        int horizonte = parseHorizonte(horizonteParam);
        LocalDate fim = LocalDate.now(clock).minusDays(1); // último dia completo

        String chave = metrica + "|" + horizonte + "|" + fim;
        // Relógio de parede de propósito: o TTL mede tempo real. Com o relógio
        // de negócio (que pode estar congelado em dev) a entrada nunca expiraria.
        long agora = System.currentTimeMillis();
        Entrada cached = cache.get(chave);
        if (cached != null && cached.expiraEm() > agora) {
            return cached.dto();
        }

        PrevisaoDTO dto = computar(metrica, horizonte, fim);
        cache.put(chave, new Entrada(dto, agora + TTL_MS));
        return dto;
    }

    private PrevisaoDTO computar(Metrica metrica, int horizonte, LocalDate fim) {
        LocalDate inicio = fim.minusDays(LOOKBACK_DIAS - 1L);

        Map<LocalDate, Double> bruto = metrica == Metrica.FATURAMENTO
            ? repository.getSerieDiariaFaturamento(inicio, fim)
            : repository.getSerieDiariaPedidos(inicio, fim);

        // Série contígua: dias sem movimento (fechado) entram como 0.
        Map<LocalDate, Double> serie = new HashMap<>();
        for (LocalDate d = inicio; !d.isAfter(fim); d = d.plusDays(1)) {
            serie.put(d, bruto.getOrDefault(d, 0.0));
        }

        Previsao previsao = forecast.prever(serie, horizonte);

        List<PontoPrevisaoDTO> pontos = new ArrayList<>(previsao.pontos().size());
        for (ForecastService.PontoPrevisto p : previsao.pontos()) {
            pontos.add(new PontoPrevisaoDTO(
                p.data(), p.valor(), p.limiteInferior(), p.limiteSuperior(), p.feriado()
            ));
        }

        List<FeriadoDTO> feriadosHorizonte = new ArrayList<>();
        for (Feriado f : feriados.feriadosNoIntervalo(fim.plusDays(1), fim.plusDays(horizonte))) {
            feriadosHorizonte.add(new FeriadoDTO(f.data(), f.nome(), f.categoria().name()));
        }

        Double mape = Double.isNaN(previsao.mape()) ? null : arredondar(previsao.mape());
        String aviso = montarAviso(horizonte, previsao.diasHistorico());

        return new PrevisaoDTO(
            metrica.name().toLowerCase(),
            previsao.metodo().name(),
            mape,
            horizonte,
            previsao.diasHistorico(),
            aviso,
            pontos,
            feriadosHorizonte
        );
    }

    /** Aviso legível quando a previsão é pouco confiável; null quando não há ressalva. */
    private static String montarAviso(int horizonte, int diasHistorico) {
        List<String> avisos = new ArrayList<>();
        if (horizonte > AVISO_HORIZONTE_DIAS) {
            avisos.add("Horizonte longo (> " + AVISO_HORIZONTE_DIAS
                + " dias): previsão extrapolada, com confiança reduzida.");
        }
        if (diasHistorico < AVISO_HISTORICO_MINIMO) {
            avisos.add("Histórico curto (" + diasHistorico
                + " dias): padrão sazonal pouco estabelecido.");
        }
        return avisos.isEmpty() ? null : String.join(" ", avisos);
    }

    private static Metrica parseMetrica(String metrica) {
        if (metrica == null) {
            throw new IllegalArgumentException(mensagemMetrica());
        }
        return switch (metrica.trim().toLowerCase()) {
            case "faturamento" -> Metrica.FATURAMENTO;
            case "pedidos" -> Metrica.PEDIDOS;
            default -> throw new IllegalArgumentException(mensagemMetrica());
        };
    }

    /**
     * Converte "30", "7d", "4s", "3m", "1a" em dias. Sufixos: d=dia, s=semana,
     * m=mês (30d), a=ano (365d). Sem sufixo → dias. Valida 1..{@link #HORIZONTE_MAX}.
     */
    private static int parseHorizonte(String horizonte) {
        if (horizonte == null || horizonte.isBlank()) {
            throw new IllegalArgumentException(mensagemHorizonte());
        }
        String h = horizonte.trim().toLowerCase();
        char sufixo = h.charAt(h.length() - 1);
        int multiplicador = switch (sufixo) {
            case 'd' -> 1;
            case 's' -> 7;
            case 'm' -> 30;
            case 'a' -> 365;
            default -> Character.isDigit(sufixo) ? 1 : -1;
        };
        if (multiplicador < 0) {
            throw new IllegalArgumentException(mensagemHorizonte());
        }
        String numero = Character.isDigit(sufixo) ? h : h.substring(0, h.length() - 1);
        int dias;
        try {
            dias = Integer.parseInt(numero.trim()) * multiplicador;
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(mensagemHorizonte());
        }
        if (dias < 1 || dias > HORIZONTE_MAX) {
            throw new IllegalArgumentException(
                "Horizonte fora do limite: deve estar entre 1 e " + HORIZONTE_MAX + " dias."
            );
        }
        return dias;
    }

    private static String mensagemMetrica() {
        return """
            Parâmetro 'metrica' inválido.

            Valores aceitos: 'faturamento' ou 'pedidos'.

            Exemplo correto:
            /analytics/previsao?metrica=faturamento&horizonte=30d
            """;
    }

    private static String mensagemHorizonte() {
        return """
            Parâmetro 'horizonte' inválido.

            Use um número de dias, opcionalmente com sufixo:
            d=dias, s=semanas, m=meses, a=anos (ex.: '30', '7d', '4s', '3m', '1a').

            Exemplo correto:
            /analytics/previsao?metrica=faturamento&horizonte=30d
            """;
    }

    private static double arredondar(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
