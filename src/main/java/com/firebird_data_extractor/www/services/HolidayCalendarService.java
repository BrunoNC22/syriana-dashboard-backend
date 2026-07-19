package com.firebird_data_extractor.www.services;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.Month;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.IntFunction;

import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Calendário de feriados e datas comemorativas brasileiras + fatores de uplift.
 *
 * <p>Java puro, zero libs novas: o único uso de biblioteca é o Jackson (já
 * presente) para ler a lista opcional do restaurante — a matemática de datas e
 * de uplift é toda própria. É pré-requisito da previsão: no domínio de
 * restaurante os maiores dias do ano são datas comemorativas (Dia das
 * Mães/Namorados/Pais), e um modelo que as ignora erra o pico e ainda contamina
 * o padrão semanal aprendido.
 *
 * <p><b>Uplift</b> = quanto uma data rende acima do "dia normal": {@code venda
 * no dia ÷ baseline (mesmo dia da semana ao redor)}, média das últimas
 * ocorrências. A previsão remove o efeito antes de treinar (série "limpa") e o
 * reaplica quando o horizonte contém a data.
 */
@Service
public class HolidayCalendarService {

    /** Categoria de uma data especial — agrupa datas de comportamento parecido
     *  para o fallback de uplift quando o histórico da data específica é curto. */
    public enum CategoriaFeriado {
        /** Datas comemorativas de grande movimento (Mães, Namorados, Pais, vésperas). */
        COMEMORATIVA,
        /** Feriados nacionais fixos (muitos com o restaurante fechado ou fraco). */
        NACIONAL,
        /** Móveis por Páscoa (Carnaval, Sexta-feira Santa, Corpus Christi). */
        MOVEL,
        /** Datas do próprio restaurante, vindas do {@code feriados.json} local. */
        PERSONALIZADO
    }

    /** Uma ocorrência concreta de feriado num dia específico. */
    public record Feriado(LocalDate data, String nome, CategoriaFeriado categoria) {}

    /**
     * Fator de uplift de uma data e a confiança por trás dele.
     *
     * @param fator                multiplicador sobre o dia normal (1.0 = neutro)
     * @param ocorrenciasUsadas    nº de ocorrências passadas da própria data usadas
     * @param usouFallbackCategoria true quando o fator veio da média da categoria
     *                              (histórico próprio insuficiente)
     */
    public record Uplift(double fator, int ocorrenciasUsadas, boolean usouFallbackCategoria) {}

    /**
     * Definição de um feriado <b>recorrente</b>: sabe calcular sua data em
     * qualquer ano. Datas de uma vez só (JSON com {@code data} absoluta) ficam
     * em {@link #personalizadosFixos}.
     */
    private record DefinicaoFeriado(String nome, CategoriaFeriado categoria, IntFunction<LocalDate> resolver) {}

    /** Semanas para cada lado usadas na baseline do "mesmo dia da semana". */
    private static final int BASELINE_JANELA_SEMANAS = 6;

    /** Máximo de ocorrências passadas mescladas na média do uplift. */
    private static final int MAX_OCORRENCIAS = 3;

    /** Mínimo de ocorrências próprias para confiar no fator específico da data. */
    private static final int MIN_OCORRENCIAS = 2;

    private final List<DefinicaoFeriado> definicoes;
    private final List<Feriado> personalizadosFixos;

    public HolidayCalendarService() {
        this(Path.of(System.getProperty("user.dir")));
    }

    /**
     * Construtor que aponta o diretório do {@code feriados.json} — usado pelos
     * testes (com um dir com/sem o arquivo). O Spring instancia pelo construtor
     * sem-arg: havendo mais de um construtor e nenhum {@code @Autowired}, ele
     * usa o padrão (sem argumentos), então este não vira candidato de injeção.
     */
    public HolidayCalendarService(Path baseDir) {
        List<DefinicaoFeriado> defs = new ArrayList<>(definicoesPadrao());
        List<Feriado> fixos = new ArrayList<>();
        carregarPersonalizados(baseDir, defs, fixos);
        this.definicoes = List.copyOf(defs);
        this.personalizadosFixos = List.copyOf(fixos);
    }

    // ==================================================================
    // API pública
    // ==================================================================

    /** True se a data é feriado/data comemorativa conhecida (recorrente ou do restaurante). */
    public boolean isFeriado(LocalDate data) {
        if (definicaoDe(data) != null) {
            return true;
        }
        for (Feriado f : personalizadosFixos) {
            if (f.data().equals(data)) {
                return true;
            }
        }
        return false;
    }

    /** Todas as ocorrências de feriado no intervalo [inicio, fim], ordenadas por data. */
    public List<Feriado> feriadosNoIntervalo(LocalDate inicio, LocalDate fim) {
        List<Feriado> lista = new ArrayList<>();
        for (int ano = inicio.getYear(); ano <= fim.getYear(); ano++) {
            for (DefinicaoFeriado def : definicoes) {
                LocalDate d = resolver(def, ano);
                if (d != null && !d.isBefore(inicio) && !d.isAfter(fim)) {
                    lista.add(new Feriado(d, def.nome(), def.categoria()));
                }
            }
        }
        for (Feriado f : personalizadosFixos) {
            if (!f.data().isBefore(inicio) && !f.data().isAfter(fim)) {
                lista.add(f);
            }
        }
        lista.sort(Comparator.comparing(Feriado::data));
        return lista;
    }

    /**
     * Fator de uplift aprendido para a data, a partir da série diária histórica.
     *
     * <p>Estratégia: para a data recorrente, olha as últimas {@link #MAX_OCORRENCIAS}
     * ocorrências anteriores no histórico e mede, em cada uma,
     * {@code valor ÷ baseline(mesmo dia da semana, excluindo feriados)}. Se houver
     * ao menos {@link #MIN_OCORRENCIAS} medições válidas, usa a média delas; senão
     * cai para a média da categoria ("grandes domingos"); sem base alguma, 1.0
     * (neutro). Datas que não são feriado recorrente também retornam 1.0.
     *
     * @param data          o dia (tipicamente futuro) cujo uplift se quer prever
     * @param serieDiaria   histórico {@code dia → valor} (faturamento ou quantidade)
     */
    public Uplift upliftDe(LocalDate data, Map<LocalDate, Double> serieDiaria) {
        DefinicaoFeriado def = definicaoDe(data);
        if (def == null || serieDiaria == null || serieDiaria.isEmpty()) {
            return new Uplift(1.0, 0, false);
        }

        int menorAno = menorAno(serieDiaria);
        List<Double> fatores = new ArrayList<>();
        for (int ano = data.getYear(); ano >= menorAno && fatores.size() < MAX_OCORRENCIAS; ano--) {
            LocalDate ocorrencia = resolver(def, ano);
            if (ocorrencia == null || !ocorrencia.isBefore(data)) {
                continue; // só ocorrências estritamente anteriores à data-alvo
            }
            double f = upliftOcorrencia(ocorrencia, serieDiaria);
            if (!Double.isNaN(f)) {
                fatores.add(f);
            }
        }

        if (fatores.size() >= MIN_OCORRENCIAS) {
            return new Uplift(media(fatores), fatores.size(), false);
        }

        double fatorCategoria = upliftCategoria(def.categoria(), data, serieDiaria);
        if (!Double.isNaN(fatorCategoria)) {
            return new Uplift(fatorCategoria, fatores.size(), true);
        }

        return new Uplift(1.0, fatores.size(), false);
    }

    // ==================================================================
    // Uplift — internos
    // ==================================================================

    /** Uplift de uma única ocorrência; NaN quando falta o valor do dia ou a baseline. */
    private double upliftOcorrencia(LocalDate dia, Map<LocalDate, Double> serie) {
        Double valor = serie.get(dia);
        if (valor == null || valor <= 0.0) {
            return Double.NaN;
        }
        double base = baseline(dia, serie);
        if (Double.isNaN(base) || base <= 0.0) {
            return Double.NaN;
        }
        return valor / base;
    }

    /**
     * Baseline = média dos mesmos dias da semana em torno de {@code dia}
     * (±{@link #BASELINE_JANELA_SEMANAS} semanas), excluindo o próprio dia e
     * qualquer outro feriado — senão um pico contaminaria a referência. NaN se
     * não houver amostras.
     */
    private double baseline(LocalDate dia, Map<LocalDate, Double> serie) {
        double soma = 0.0;
        int n = 0;
        for (int k = -BASELINE_JANELA_SEMANAS; k <= BASELINE_JANELA_SEMANAS; k++) {
            if (k == 0) {
                continue;
            }
            LocalDate d = dia.plusWeeks(k);
            Double v = serie.get(d);
            if (v == null || v <= 0.0 || isFeriado(d)) {
                continue;
            }
            soma += v;
            n++;
        }
        return n == 0 ? Double.NaN : soma / n;
    }

    /** Média dos upliftes de todas as datas da mesma categoria (fallback), exceto a data-alvo. */
    private double upliftCategoria(CategoriaFeriado categoria, LocalDate alvo, Map<LocalDate, Double> serie) {
        int menorAno = menorAno(serie);
        int maiorAno = maiorAno(serie);
        List<Double> fatores = new ArrayList<>();
        for (DefinicaoFeriado def : definicoes) {
            if (def.categoria() != categoria) {
                continue;
            }
            for (int ano = menorAno; ano <= maiorAno; ano++) {
                LocalDate ocorrencia = resolver(def, ano);
                if (ocorrencia == null || ocorrencia.equals(alvo)) {
                    continue;
                }
                double f = upliftOcorrencia(ocorrencia, serie);
                if (!Double.isNaN(f)) {
                    fatores.add(f);
                }
            }
        }
        return fatores.isEmpty() ? Double.NaN : media(fatores);
    }

    // ==================================================================
    // Definições do calendário
    // ==================================================================

    /** A definição recorrente cuja data no ano de {@code data} coincide com ela; null se nenhuma. */
    private DefinicaoFeriado definicaoDe(LocalDate data) {
        for (DefinicaoFeriado def : definicoes) {
            if (data.equals(resolver(def, data.getYear()))) {
                return def;
            }
        }
        return null;
    }

    /** Avalia o resolver com guarda: datas inválidas num dado ano (ex.: 29/02) viram null. */
    private static LocalDate resolver(DefinicaoFeriado def, int ano) {
        try {
            return def.resolver().apply(ano);
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private static List<DefinicaoFeriado> definicoesPadrao() {
        List<DefinicaoFeriado> defs = new ArrayList<>();

        // Datas comemorativas de grande movimento em restaurante.
        defs.add(new DefinicaoFeriado("Dia das Mães", CategoriaFeriado.COMEMORATIVA,
            ano -> nthDiaDaSemana(ano, Month.MAY, DayOfWeek.SUNDAY, 2)));
        defs.add(new DefinicaoFeriado("Dia dos Pais", CategoriaFeriado.COMEMORATIVA,
            ano -> nthDiaDaSemana(ano, Month.AUGUST, DayOfWeek.SUNDAY, 2)));
        defs.add(new DefinicaoFeriado("Dia dos Namorados", CategoriaFeriado.COMEMORATIVA,
            ano -> LocalDate.of(ano, 6, 12)));
        defs.add(new DefinicaoFeriado("Véspera de Natal", CategoriaFeriado.COMEMORATIVA,
            ano -> LocalDate.of(ano, 12, 24)));
        defs.add(new DefinicaoFeriado("Véspera de Ano Novo", CategoriaFeriado.COMEMORATIVA,
            ano -> LocalDate.of(ano, 12, 31)));

        // Feriados nacionais fixos.
        defs.add(new DefinicaoFeriado("Confraternização Universal", CategoriaFeriado.NACIONAL,
            ano -> LocalDate.of(ano, 1, 1)));
        defs.add(new DefinicaoFeriado("Tiradentes", CategoriaFeriado.NACIONAL,
            ano -> LocalDate.of(ano, 4, 21)));
        defs.add(new DefinicaoFeriado("Dia do Trabalho", CategoriaFeriado.NACIONAL,
            ano -> LocalDate.of(ano, 5, 1)));
        defs.add(new DefinicaoFeriado("Independência do Brasil", CategoriaFeriado.NACIONAL,
            ano -> LocalDate.of(ano, 9, 7)));
        defs.add(new DefinicaoFeriado("Nossa Senhora Aparecida", CategoriaFeriado.NACIONAL,
            ano -> LocalDate.of(ano, 10, 12)));
        defs.add(new DefinicaoFeriado("Finados", CategoriaFeriado.NACIONAL,
            ano -> LocalDate.of(ano, 11, 2)));
        defs.add(new DefinicaoFeriado("Proclamação da República", CategoriaFeriado.NACIONAL,
            ano -> LocalDate.of(ano, 11, 15)));
        defs.add(new DefinicaoFeriado("Natal", CategoriaFeriado.NACIONAL,
            ano -> LocalDate.of(ano, 12, 25)));

        // Móveis por Páscoa (Computus).
        defs.add(new DefinicaoFeriado("Carnaval", CategoriaFeriado.MOVEL,
            ano -> pascoa(ano).minusDays(47)));
        defs.add(new DefinicaoFeriado("Sexta-feira Santa", CategoriaFeriado.MOVEL,
            ano -> pascoa(ano).minusDays(2)));
        defs.add(new DefinicaoFeriado("Corpus Christi", CategoriaFeriado.MOVEL,
            ano -> pascoa(ano).plusDays(60)));

        return defs;
    }

    /** N-ésima ocorrência de um dia da semana num mês (ex.: 2º domingo de maio). */
    private static LocalDate nthDiaDaSemana(int ano, Month mes, DayOfWeek dia, int n) {
        return LocalDate.of(ano, mes, 1).with(TemporalAdjusters.dayOfWeekInMonth(n, dia));
    }

    /**
     * Domingo de Páscoa pelo algoritmo de Computus (Meeus/Jones/Butcher,
     * calendário gregoriano).
     */
    public static LocalDate pascoa(int ano) {
        int a = ano % 19;
        int b = ano / 100;
        int c = ano % 100;
        int d = b / 4;
        int e = b % 4;
        int f = (b + 8) / 25;
        int g = (b - f + 1) / 3;
        int h = (19 * a + b - d - g + 15) % 30;
        int i = c / 4;
        int k = c % 4;
        int l = (32 + 2 * e + 2 * i - h - k) % 7;
        int m = (a + 11 * h + 22 * l) / 451;
        int mes = (h + l - 7 * m + 114) / 31;
        int dia = ((h + l - 7 * m + 114) % 31) + 1;
        return LocalDate.of(ano, mes, dia);
    }

    /**
     * Lista opcional do restaurante em {@code feriados.json} (ao lado do
     * {@code server-config.json}). Cada entrada tem {@code nome} e, ou
     * {@code mesDia} ("MM-dd", recorrente todo ano) ou {@code data} ("yyyy-MM-dd",
     * uma vez só). Config tolerante: ausência ou erro de parse nunca derrubam o
     * boot — apenas ficam sem as datas extras.
     */
    private static void carregarPersonalizados(Path baseDir, List<DefinicaoFeriado> defs, List<Feriado> fixos) {
        Path arquivo = baseDir.resolve("feriados.json");
        if (!Files.exists(arquivo)) {
            return;
        }
        try {
            JsonNode raiz = new ObjectMapper().readTree(arquivo.toFile());
            if (raiz == null || !raiz.isArray()) {
                return;
            }
            for (JsonNode e : raiz) {
                String nome = e.hasNonNull("nome") ? e.get("nome").asText() : "Data do restaurante";
                if (e.hasNonNull("mesDia")) {
                    String mesDia = e.get("mesDia").asText();
                    if (mesDia.matches("\\d{2}-\\d{2}")) {
                        int mes = Integer.parseInt(mesDia.substring(0, 2));
                        int dia = Integer.parseInt(mesDia.substring(3, 5));
                        defs.add(new DefinicaoFeriado(nome, CategoriaFeriado.PERSONALIZADO,
                            ano -> LocalDate.of(ano, mes, dia)));
                    }
                } else if (e.hasNonNull("data")) {
                    fixos.add(new Feriado(LocalDate.parse(e.get("data").asText()), nome,
                        CategoriaFeriado.PERSONALIZADO));
                }
            }
        } catch (Exception ex) {
            System.err.println("[HolidayCalendar] feriados.json ignorado: " + ex.getMessage());
        }
    }

    // ==================================================================
    // Utilitários numéricos
    // ==================================================================

    private static int menorAno(Map<LocalDate, Double> serie) {
        int min = Integer.MAX_VALUE;
        for (LocalDate d : serie.keySet()) {
            min = Math.min(min, d.getYear());
        }
        return min;
    }

    private static int maiorAno(Map<LocalDate, Double> serie) {
        int max = Integer.MIN_VALUE;
        for (LocalDate d : serie.keySet()) {
            max = Math.max(max, d.getYear());
        }
        return max;
    }

    private static double media(List<Double> valores) {
        double soma = 0.0;
        for (double v : valores) {
            soma += v;
        }
        return soma / valores.size();
    }
}
