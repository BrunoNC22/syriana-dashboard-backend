package com.firebird_data_extractor.www.services;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.firebird_data_extractor.www.services.HolidayCalendarService.Uplift;

/**
 * Motor de previsão de séries diárias em <b>Java puro</b> (zero libs novas — sem
 * risco de reflection no GraalVM). Combina três ideias, na altitude que o
 * domínio de restaurante pede:
 *
 * <ol>
 *   <li><b>Holt-Winters aditivo</b> com sazonalidade semanal (m=7): nível +
 *       tendência + padrão dia-a-dia da semana. É o modelo que capta o efeito
 *       dominante (sábado ≠ terça).</li>
 *   <li><b>Ajuste de feriados</b> (via {@link HolidayCalendarService}): o pico de
 *       Dia das Mães/Namorados/Pais é <i>removido</i> da série antes de treinar
 *       (senão contamina o padrão semanal) e <i>reaplicado</i> quando o horizonte
 *       previsto contém a data.</li>
 *   <li><b>Baseline naïve sazonal</b> (o próximo sábado = o último sábado) como
 *       piso de comparação e <i>fallback</i>: um backtest rolling-origin escolhe
 *       entre Holt-Winters e o naïve pelo menor MAPE e devolve esse erro junto
 *       da previsão — previsão sem barra de erro é desinformação gerencial.</li>
 * </ol>
 *
 * <p>Séries individuais de produto/insumo são esparsas demais para modelar uma a
 * uma; este serviço prevê o <b>total</b> (estável) e a desagregação por mix share
 * fica em quem consome (F4-03 / relatório de compras).
 */
@Service
public class ForecastService {

    /** Período sazonal: 7 dias (o padrão semanal domina em restaurante). */
    private static final int M = 7;

    /** Mínimo de dias para tentar Holt-Winters (precisa de ~2 ciclos completos). */
    private static final int MIN_DIAS_HW = 2 * M;

    /** Janela do backtest rolling-origin (dias avaliados no fim da série). */
    private static final int JANELA_BACKTEST = 60;

    /** Mínimo de pontos válidos no backtest para o MAPE ter sentido. */
    private static final int MIN_PONTOS_BACKTEST = 7;

    /** z ~ 1,28 → banda de confiança de ~80% (o suficiente p/ decisão gerencial). */
    private static final double Z_INTERVALO = 1.28;

    /** Abaixo disto um valor é "zero" — evita divisão por ~0 no MAPE/uplift. */
    private static final double EPSILON = 1e-9;

    // Grade de busca dos parâmetros de suavização do Holt-Winters. Pequena e
    // barata (série de ~2 anos = ~730 pontos; cada ajuste é O(n)).
    private static final double[] GRADE_ALPHA = {0.1, 0.2, 0.3, 0.5};
    private static final double[] GRADE_BETA = {0.02, 0.05, 0.1};
    private static final double[] GRADE_GAMMA = {0.1, 0.2, 0.3};

    private final HolidayCalendarService feriados;

    public ForecastService(HolidayCalendarService feriados) {
        this.feriados = feriados;
    }

    // ==================================================================
    // Tipos de resultado
    // ==================================================================

    /** Método efetivamente usado na previsão (escolhido pelo backtest). */
    public enum Metodo { HOLT_WINTERS, NAIVE_SAZONAL }

    /**
     * Um dia previsto com a banda de confiança.
     *
     * @param data           o dia previsto
     * @param valor          previsão central (já com o uplift de feriado reaplicado)
     * @param limiteInferior piso da banda de ~80%
     * @param limiteSuperior teto da banda de ~80%
     * @param feriado        true se o dia é feriado/data comemorativa conhecida
     */
    public record PontoPrevisto(
        LocalDate data,
        double valor,
        double limiteInferior,
        double limiteSuperior,
        boolean feriado
    ) {}

    /**
     * Resultado da previsão.
     *
     * @param metodo        modelo escolhido pelo backtest
     * @param mape          erro percentual médio do backtest (menor = melhor);
     *                      {@code NaN} quando não houve histórico p/ validar
     * @param diasHistorico nº de dias de série usados no treino
     * @param pontos        previsão dia a dia no horizonte pedido
     */
    public record Previsao(
        Metodo metodo,
        double mape,
        int diasHistorico,
        List<PontoPrevisto> pontos
    ) {}

    // ==================================================================
    // API pública
    // ==================================================================

    /**
     * Prevê os próximos {@code horizonte} dias a partir da série diária histórica.
     *
     * @param serieDiaria histórico {@code dia → valor} (faturamento ou quantidade);
     *                    lacunas de calendário são preenchidas com 0 (dia fechado)
     * @param horizonte   nº de dias a prever (>= 1)
     * @return a previsão com método, MAPE do backtest e pontos com banda de confiança
     */
    public Previsao prever(Map<LocalDate, Double> serieDiaria, int horizonte) {
        if (horizonte < 1) {
            throw new IllegalArgumentException("horizonte deve ser >= 1");
        }
        if (serieDiaria == null || serieDiaria.isEmpty()) {
            return new Previsao(Metodo.NAIVE_SAZONAL, Double.NaN, 0, List.of());
        }

        LocalDate inicio = null;
        LocalDate fim = null;
        for (LocalDate d : serieDiaria.keySet()) {
            if (inicio == null || d.isBefore(inicio)) {
                inicio = d;
            }
            if (fim == null || d.isAfter(fim)) {
                fim = d;
            }
        }

        int n = (int) (fim.toEpochDay() - inicio.toEpochDay()) + 1;
        LocalDate[] datas = new LocalDate[n];
        double[] originais = new double[n];
        for (int i = 0; i < n; i++) {
            LocalDate dia = inicio.plusDays(i);
            datas[i] = dia;
            Double v = serieDiaria.get(dia);
            originais[i] = v != null ? v : 0.0;
        }

        // Série "limpa": remove o pico de feriado (÷ uplift aprendido) para não
        // contaminar o padrão semanal que os modelos vão aprender.
        double[] limpos = new double[n];
        for (int i = 0; i < n; i++) {
            double fator = feriados.isFeriado(datas[i])
                ? feriados.upliftDe(datas[i], serieDiaria).fator()
                : 1.0;
            limpos[i] = fator > EPSILON ? originais[i] / fator : originais[i];
        }

        // Histórico curto demais até para o naïve semanal: previsão plana pela
        // média, sem confiança de backtest.
        if (n < M + 1) {
            return previsaoPlana(datas, limpos, serieDiaria, fim, horizonte, n);
        }

        // Backtest rolling-origin: cada modelo prevê 1 dia à frente ao longo da
        // janela final e mede o MAPE. O menor vence (naïve é o sarrafo).
        double[] melhorHW = n >= MIN_DIAS_HW ? melhoresParametrosHW(limpos) : null;
        double mapeHW = melhorHW != null
            ? mapeBacktestHW(limpos, melhorHW)
            : Double.POSITIVE_INFINITY;
        double mapeNaive = mapeBacktestNaive(limpos);

        boolean usaHW = melhorHW != null && mapeHW <= mapeNaive;
        Metodo metodo = usaHW ? Metodo.HOLT_WINTERS : Metodo.NAIVE_SAZONAL;
        double mape = usaHW ? mapeHW : mapeNaive;
        if (Double.isInfinite(mape)) {
            mape = Double.NaN; // sem pontos válidos p/ validar
        }

        // Ajuste in-sample do método escolhido → resíduos → σ da banda.
        double[] ajuste = usaHW
            ? holtWinters(limpos, horizonte, melhorHW[0], melhorHW[1], melhorHW[2]).ajuste()
            : naiveAjuste(limpos);
        double sigma = desvioResiduos(limpos, ajuste);

        double[] previsaoLimpa = usaHW
            ? holtWinters(limpos, horizonte, melhorHW[0], melhorHW[1], melhorHW[2]).previsao()
            : naivePrevisao(limpos, horizonte);

        // Reaplica o uplift de feriado sobre a previsão e monta os pontos.
        List<PontoPrevisto> pontos = new ArrayList<>(horizonte);
        for (int h = 0; h < horizonte; h++) {
            LocalDate dia = fim.plusDays(h + 1L);
            boolean ehFeriado = feriados.isFeriado(dia);
            double fator = 1.0;
            if (ehFeriado) {
                Uplift up = feriados.upliftDe(dia, serieDiaria);
                fator = up.fator();
            }
            double centro = Math.max(0.0, previsaoLimpa[h] * fator);
            // Banda cresce com sqrt(passo) (incerteza acumula no horizonte) e
            // escala com o mesmo fator do dia (feriado tem magnitude maior).
            double margem = Z_INTERVALO * sigma * Math.sqrt(h + 1.0) * fator;
            double inferior = Math.max(0.0, centro - margem);
            double superior = centro + margem;
            pontos.add(new PontoPrevisto(dia, centro, inferior, superior, ehFeriado));
        }

        return new Previsao(metodo, mape, n, pontos);
    }

    /** Fallback de histórico curto: previsão plana pela média, banda pelo desvio. */
    private Previsao previsaoPlana(
        LocalDate[] datas,
        double[] limpos,
        Map<LocalDate, Double> serieDiaria,
        LocalDate fim,
        int horizonte,
        int n
    ) {
        double media = media(limpos, 0, n);
        double sigma = desvioPadrao(limpos, 0, n, media);
        List<PontoPrevisto> pontos = new ArrayList<>(horizonte);
        for (int h = 0; h < horizonte; h++) {
            LocalDate dia = fim.plusDays(h + 1L);
            boolean ehFeriado = feriados.isFeriado(dia);
            double fator = ehFeriado ? feriados.upliftDe(dia, serieDiaria).fator() : 1.0;
            double centro = Math.max(0.0, media * fator);
            double margem = Z_INTERVALO * sigma * fator;
            pontos.add(new PontoPrevisto(
                dia, centro, Math.max(0.0, centro - margem), centro + margem, ehFeriado
            ));
        }
        return new Previsao(Metodo.NAIVE_SAZONAL, Double.NaN, n, pontos);
    }

    // ==================================================================
    // Holt-Winters aditivo (m=7)
    // ==================================================================

    /** Ajuste in-sample (1 passo à frente) + previsão h passos do Holt-Winters. */
    private record HW(double[] ajuste, double[] previsao) {}

    /**
     * Holt-Winters aditivo com sazonalidade {@link #M}. Inicializa nível pela
     * média do 1º ciclo, tendência pela diferença entre os dois primeiros ciclos
     * e os índices sazonais pelo desvio do 1º ciclo em relação ao nível.
     */
    private static HW holtWinters(double[] y, int h, double alpha, double beta, double gamma) {
        int n = y.length;
        double nivel = media(y, 0, M);
        double tendencia = (media(y, M, 2 * M) - media(y, 0, M)) / M;
        double[] sazonal = new double[M];
        for (int i = 0; i < M; i++) {
            sazonal[i] = y[i] - nivel;
        }

        double[] ajuste = new double[n];
        for (int t = 0; t < n; t++) {
            int s = t % M;
            double ultimoNivel = nivel;
            double ultimaSazonal = sazonal[s];
            ajuste[t] = ultimoNivel + tendencia + ultimaSazonal;

            nivel = alpha * (y[t] - ultimaSazonal) + (1 - alpha) * (ultimoNivel + tendencia);
            tendencia = beta * (nivel - ultimoNivel) + (1 - beta) * tendencia;
            sazonal[s] = gamma * (y[t] - nivel) + (1 - gamma) * ultimaSazonal;
        }

        double[] previsao = new double[h];
        for (int i = 0; i < h; i++) {
            int s = (n + i) % M;
            previsao[i] = nivel + (i + 1) * tendencia + sazonal[s];
        }
        return new HW(ajuste, previsao);
    }

    /** Busca em grade os parâmetros que minimizam o SSE in-sample (após o 1º ciclo). */
    private static double[] melhoresParametrosHW(double[] y) {
        double melhorSse = Double.POSITIVE_INFINITY;
        double[] melhor = {GRADE_ALPHA[0], GRADE_BETA[0], GRADE_GAMMA[0]};
        for (double a : GRADE_ALPHA) {
            for (double b : GRADE_BETA) {
                for (double g : GRADE_GAMMA) {
                    double[] ajuste = holtWinters(y, 1, a, b, g).ajuste();
                    double sse = 0.0;
                    for (int t = M; t < y.length; t++) {
                        double e = y[t] - ajuste[t];
                        sse += e * e;
                    }
                    if (sse < melhorSse) {
                        melhorSse = sse;
                        melhor = new double[]{a, b, g};
                    }
                }
            }
        }
        return melhor;
    }

    // ==================================================================
    // Naïve sazonal (baseline / fallback)
    // ==================================================================

    /** Ajuste naïve sazonal: fitted[t] = y[t-m] (o mesmo dia da semana anterior). */
    private static double[] naiveAjuste(double[] y) {
        double[] ajuste = new double[y.length];
        for (int t = 0; t < y.length; t++) {
            ajuste[t] = t >= M ? y[t - M] : y[t];
        }
        return ajuste;
    }

    /** Previsão naïve: repete o último mesmo-dia-da-semana, realimentando além de m. */
    private static double[] naivePrevisao(double[] y, int h) {
        int n = y.length;
        double[] previsao = new double[h];
        for (int i = 0; i < h; i++) {
            int idx = n + i - M;
            previsao[i] = idx < n ? y[idx] : previsao[idx - n];
        }
        return previsao;
    }

    // ==================================================================
    // Backtest rolling-origin (1 passo à frente na janela final)
    // ==================================================================

    /** MAPE do Holt-Winters prevendo 1 dia à frente ao longo da janela final. */
    private static double mapeBacktestHW(double[] y, double[] params) {
        int n = y.length;
        int inicio = Math.max(MIN_DIAS_HW, n - JANELA_BACKTEST);
        double somaApe = 0.0;
        int validos = 0;
        for (int t = inicio; t < n; t++) {
            double[] prefixo = new double[t];
            System.arraycopy(y, 0, prefixo, 0, t);
            double pred = holtWinters(prefixo, 1, params[0], params[1], params[2]).previsao()[0];
            if (Math.abs(y[t]) > EPSILON) {
                somaApe += Math.abs(y[t] - pred) / Math.abs(y[t]);
                validos++;
            }
        }
        return validos >= MIN_PONTOS_BACKTEST ? somaApe / validos * 100.0 : Double.POSITIVE_INFINITY;
    }

    /** MAPE do naïve sazonal na mesma janela (pred = valor do mesmo dia da semana anterior). */
    private static double mapeBacktestNaive(double[] y) {
        int n = y.length;
        int inicio = Math.max(M, n - JANELA_BACKTEST);
        double somaApe = 0.0;
        int validos = 0;
        for (int t = inicio; t < n; t++) {
            double pred = y[t - M];
            if (Math.abs(y[t]) > EPSILON) {
                somaApe += Math.abs(y[t] - pred) / Math.abs(y[t]);
                validos++;
            }
        }
        return validos >= MIN_PONTOS_BACKTEST ? somaApe / validos * 100.0 : Double.POSITIVE_INFINITY;
    }

    // ==================================================================
    // Utilitários numéricos
    // ==================================================================

    /** Desvio padrão dos resíduos in-sample (a partir do 1º ciclo sazonal). */
    private static double desvioResiduos(double[] y, double[] ajuste) {
        double soma = 0.0;
        int n = 0;
        for (int t = M; t < y.length; t++) {
            double e = y[t] - ajuste[t];
            soma += e * e;
            n++;
        }
        return n == 0 ? 0.0 : Math.sqrt(soma / n);
    }

    /** Média de {@code y[de..ate)}. */
    private static double media(double[] y, int de, int ate) {
        double soma = 0.0;
        for (int i = de; i < ate; i++) {
            soma += y[i];
        }
        return soma / (ate - de);
    }

    /** Desvio padrão populacional de {@code y[de..ate)} em torno da média dada. */
    private static double desvioPadrao(double[] y, int de, int ate, double media) {
        double soma = 0.0;
        for (int i = de; i < ate; i++) {
            double d = y[i] - media;
            soma += d * d;
        }
        int n = ate - de;
        return n == 0 ? 0.0 : Math.sqrt(soma / n);
    }
}
