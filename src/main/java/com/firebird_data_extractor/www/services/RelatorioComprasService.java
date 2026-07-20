package com.firebird_data_extractor.www.services;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;

import com.firebird_data_extractor.www.dto.ConsumoInsumoBucketDTO;
import com.firebird_data_extractor.www.dto.FeriadoDTO;
import com.firebird_data_extractor.www.dto.LinhaCompraDTO;
import com.firebird_data_extractor.www.dto.PanoramaCompraDTO;
import com.firebird_data_extractor.www.dto.PontoPrevisaoDTO;
import com.firebird_data_extractor.www.dto.PrevisaoDTO;
import com.firebird_data_extractor.www.dto.RelatorioComprasDTO;
import com.firebird_data_extractor.www.dto.SaldoInsumoDTO;
import com.firebird_data_extractor.www.dto.ValorPrevistoDTO;
import com.firebird_data_extractor.www.repositories.EstoqueRepository;
import com.firebird_data_extractor.www.repositories.EstoqueRepository.Granularidade;
import com.firebird_data_extractor.www.repositories.ForecastRepository;
import com.firebird_data_extractor.www.services.ForecastService.PontoPrevisto;
import com.firebird_data_extractor.www.services.ForecastService.Previsao;
import com.firebird_data_extractor.www.services.HolidayCalendarService.Feriado;

/**
 * Monta o relatório de previsão de compras (item 9 do plano): compõe a previsão
 * do consumo, o estoque atual (com a flag de confiabilidade da F5-01) e as
 * regras de compra numa única resposta.
 *
 * <p><b>Consumo previsto — top-down.</b> Prever a série de consumo de cada
 * insumo individualmente seria ruidoso (séries esparsas, unidades distintas).
 * Em vez disso prevê-se o <b>custo total de consumo</b> (série homogênea em R$,
 * via {@link ForecastService} — já capta sazonalidade semanal, tendência e o
 * uplift de feriados) e desagrega-se por insumo pela sua participação recente:
 *
 * <pre>
 *   S = custoConsumoPrevisto(horizonte) / custoConsumoRecente(janela)
 *   consumoPrevisto_i = quantidadeRecente_i × S     (na unidade do insumo)
 *   custoPrevisto_i   = custoRecente_i × S           (R$)
 * </pre>
 *
 * O fator {@code S} escala o consumo recente de cada insumo para o nível
 * previsto, respeitando a diferença entre a janela recente e o horizonte, e
 * mantém cada insumo na sua própria unidade (não soma kg com litros).
 *
 * <p><b>Faturamento/pedidos do panorama</b> reutilizam o {@link PrevisaoService}
 * (mesma previsão cacheada do dashboard). O relatório inteiro também é cacheado
 * em memória (TTL curto) — app desktop single-user, previsão determinística.
 *
 * <p>Camada de composição, somente leitura: nenhuma escrita no Firebird.
 */
@Service
public class RelatorioComprasService {

    /** Janela de treino da previsão de custo (~2 anos), como no PrevisaoService. */
    private static final int LOOKBACK_DIAS = 728;

    /** Janela recente usada para a participação (mix) e a variabilidade de cada insumo. */
    private static final int JANELA_MIX_DIAS = 90;

    /** Horizonte padrão do relatório: o próximo mês. */
    private static final int HORIZONTE_PADRAO = 30;

    /** Teto do horizonte (1 ano) — igual ao PrevisaoService. */
    private static final int HORIZONTE_MAX = 365;

    /** z ~ 1,64 → nível de serviço de ~95% no estoque de segurança. */
    private static final double Z_SEGURANCA = 1.64;

    /** Estoque acima de k× o consumo mensal previsto = "capital parado". */
    private static final double FATOR_EXCESSO = 2.0;

    /** Dias de cobertura abaixo destes limiares → risco alto / médio. */
    private static final int RUPTURA_ALTA_DIAS = 7;
    private static final int RUPTURA_MEDIA_DIAS = 15;

    /** Coeficiente de variação que separa margem baixa/normal/alta. */
    private static final double CV_BAIXA = 0.5;
    private static final double CV_ALTA = 1.5;

    /** Validade de cada entrada do cache. */
    private static final long TTL_MS = 10 * 60 * 1000L;

    private static final double EPSILON = 1e-9;

    private final ForecastRepository forecastRepository;
    private final EstoqueRepository estoqueRepository;
    private final ForecastService forecast;
    private final PrevisaoService previsaoService;
    private final HolidayCalendarService feriados;
    private final Clock clock;
    private final Map<String, Entrada> cache = new ConcurrentHashMap<>();

    public RelatorioComprasService(
        ForecastRepository forecastRepository,
        EstoqueRepository estoqueRepository,
        ForecastService forecast,
        PrevisaoService previsaoService,
        HolidayCalendarService feriados,
        Clock clock
    ) {
        this.clock = clock;
        this.forecastRepository = forecastRepository;
        this.estoqueRepository = estoqueRepository;
        this.forecast = forecast;
        this.previsaoService = previsaoService;
        this.feriados = feriados;
    }

    private record Entrada(RelatorioComprasDTO dto, long expiraEm) {}

    /** Acumulador da janela recente de um insumo (quantidade, custo e série diária). */
    private static final class ResumoInsumo {
        double quantidade;
        double custo;
        final double[] diario;

        ResumoInsumo(int dias) {
            this.diario = new double[dias];
        }
    }

    /**
     * Gera o relatório para o horizonte pedido (com cache).
     *
     * @param horizonteParam nº de dias, com sufixo opcional (d=dias, s=semanas,
     *                       m=meses, a=anos). Vazio → {@link #HORIZONTE_PADRAO}.
     */
    public RelatorioComprasDTO gerar(String horizonteParam) {
        int horizonteDias = parseHorizonte(horizonteParam);
        LocalDate hoje = LocalDate.now(clock);

        String chave = horizonteDias + "|" + hoje;
        // Relógio de parede de propósito: o TTL mede tempo real. Com o relógio
        // de negócio (que pode estar congelado em dev) a entrada nunca expiraria.
        long agora = System.currentTimeMillis();
        Entrada cached = cache.get(chave);
        if (cached != null && cached.expiraEm() > agora) {
            return cached.dto();
        }

        RelatorioComprasDTO dto = computar(horizonteDias, hoje);
        cache.put(chave, new Entrada(dto, agora + TTL_MS));
        return dto;
    }

    private RelatorioComprasDTO computar(int horizonteDias, LocalDate hoje) {
        LocalDate horizonteInicio = hoje;
        LocalDate horizonteFim = hoje.plusDays(horizonteDias - 1L);
        LocalDate fimSerie = hoje.minusDays(1); // último dia completo

        // 1. Previsão do custo total de consumo (série homogênea em R$).
        LocalDate inicioTreino = fimSerie.minusDays(LOOKBACK_DIAS - 1L);
        Map<LocalDate, Double> serieCusto = preencherZeros(
            inicioTreino, fimSerie, forecastRepository.getSerieDiariaCustoConsumo(inicioTreino, fimSerie)
        );
        Previsao previsaoCusto = forecast.prever(serieCusto, horizonteDias);
        double custoConsumoPrevisto = 0.0;
        for (PontoPrevisto p : previsaoCusto.pontos()) {
            custoConsumoPrevisto += p.valor();
        }

        // 2. Participação (mix) e variabilidade recentes por insumo.
        LocalDate inicioMix = fimSerie.minusDays(JANELA_MIX_DIAS - 1L);
        Map<String, ResumoInsumo> resumos = resumirJanela(inicioMix, fimSerie);
        double custoRecenteTotal = 0.0;
        for (ResumoInsumo r : resumos.values()) {
            custoRecenteTotal += r.custo;
        }

        PanoramaCompraDTO panorama = montarPanorama(horizonteDias, horizonteInicio, horizonteFim);
        List<String> avisos = new ArrayList<>();

        // Sem histórico de consumo recente: não há como projetar compras.
        if (custoRecenteTotal <= EPSILON) {
            avisos.add("Sem histórico de consumo de insumos nos últimos "
                + JANELA_MIX_DIAS + " dias — não foi possível projetar compras.");
            return new RelatorioComprasDTO(
                hoje, horizonteInicio, horizonteFim, horizonteDias,
                baseHistorica(previsaoCusto), panorama, List.of(), avisos
            );
        }

        double escala = custoConsumoPrevisto / custoRecenteTotal;

        // 3. Saldo atual por insumo (com a flag de confiabilidade da F5-01).
        Map<String, SaldoInsumoDTO> saldos = new HashMap<>();
        for (SaldoInsumoDTO s : estoqueRepository.getSaldosPorInsumo()) {
            saldos.put(s.id(), s);
        }

        // 4. Uma linha de compra por insumo consumido na janela.
        List<LinhaCompraDTO> compras = new ArrayList<>(resumos.size());
        for (Map.Entry<String, ResumoInsumo> e : resumos.entrySet()) {
            compras.add(montarLinha(e.getKey(), e.getValue(), escala, horizonteDias, saldos.get(e.getKey())));
        }

        // Ordena por risco (alto → baixo) e, dentro do risco, pela maior compra.
        compras.sort(
            Comparator.comparingInt((LinhaCompraDTO l) -> prioridadeRisco(l.riscoRuptura()))
                .thenComparing(l -> -l.custoCompraRecomendada())
        );

        montarAvisos(avisos, compras, panorama);

        return new RelatorioComprasDTO(
            hoje, horizonteInicio, horizonteFim, horizonteDias,
            baseHistorica(previsaoCusto), panorama, compras, avisos
        );
    }

    /** Agrega o consumo diário por insumo na janela recente (quantidade, custo, série). */
    private Map<String, ResumoInsumo> resumirJanela(LocalDate inicio, LocalDate fim) {
        int dias = (int) (fim.toEpochDay() - inicio.toEpochDay()) + 1;
        Map<String, ResumoInsumo> resumos = new LinkedHashMap<>();
        for (ConsumoInsumoBucketDTO b : estoqueRepository.getConsumoPorInsumo(inicio, fim, Granularidade.DIA)) {
            ResumoInsumo r = resumos.computeIfAbsent(b.id(), k -> new ResumoInsumo(dias));
            double q = b.quantidade() != null ? b.quantidade() : 0.0;
            double c = b.custo() != null ? b.custo() : 0.0;
            r.quantidade += q;
            r.custo += c;
            int idx = (int) (b.bucket().toEpochDay() - inicio.toEpochDay());
            if (idx >= 0 && idx < dias) {
                r.diario[idx] += q;
            }
        }
        return resumos;
    }

    private LinhaCompraDTO montarLinha(
        String id, ResumoInsumo resumo, double escala, int horizonteDias, SaldoInsumoDTO saldo
    ) {
        String nome = saldo != null ? saldo.nome() : id;
        String sigla = saldo != null ? saldo.siglaUnidadeDeMedida() : null;
        boolean confiavel = saldo != null && saldo.confiavel();
        Double estoqueAtual = saldo != null ? saldo.estoqueAtual() : null;
        Double estoqueMinimo = saldo != null ? saldo.estoqueMinimo() : null;
        Double precoCusto = saldo != null ? saldo.precoCusto() : null;

        // Estoque assumido: 0 quando não confiável (negativo/NULL).
        double estoqueEfetivo = (confiavel && estoqueAtual != null) ? estoqueAtual : 0.0;

        double consumoPrevisto = resumo.quantidade * escala;
        double custoPrevisto = resumo.custo * escala;

        // Estoque de segurança: z × σ_diário × sqrt(horizonte), piso em ESTOQUEMINIMO.
        int dias = resumo.diario.length;
        double media = soma(resumo.diario) / dias;
        double sigma = desvioPadrao(resumo.diario, media);
        double seguranca = Z_SEGURANCA * sigma * Math.sqrt(horizonteDias);
        if (estoqueMinimo != null && estoqueMinimo > seguranca) {
            seguranca = estoqueMinimo;
        }
        double cv = media > EPSILON ? sigma / media : 0.0;
        String margem = cv < CV_BAIXA ? "baixa" : cv < CV_ALTA ? "normal" : "alta";

        double compra = Math.max(0.0, consumoPrevisto + seguranca - estoqueEfetivo);
        double custoCompra;
        if (precoCusto != null && precoCusto > EPSILON) {
            custoCompra = compra * precoCusto;
        } else if (consumoPrevisto > EPSILON) {
            custoCompra = custoPrevisto * compra / consumoPrevisto; // estima pelo custo médio previsto
        } else {
            custoCompra = 0.0;
        }

        double consumoDiarioPrevisto = consumoPrevisto / horizonteDias;
        Double diasCobertura = consumoDiarioPrevisto > EPSILON
            ? arredondar(estoqueEfetivo / consumoDiarioPrevisto, 1)
            : null;
        String risco = classificarRisco(diasCobertura);

        double consumoMensalEq = consumoPrevisto * (30.0 / horizonteDias);
        boolean excesso = consumoMensalEq > EPSILON
            ? estoqueEfetivo > FATOR_EXCESSO * consumoMensalEq
            : estoqueEfetivo > 0.0;

        String justificativa = justificar(
            resumo, consumoPrevisto, seguranca, estoqueEfetivo, confiavel, compra, sigla, horizonteDias
        );

        return new LinhaCompraDTO(
            id, nome, sigla,
            arredondar(consumoPrevisto, 3), arredondar(custoPrevisto, 2),
            estoqueAtual, confiavel,
            arredondar(seguranca, 3), margem,
            arredondar(compra, 3), arredondar(custoCompra, 2),
            risco, diasCobertura, excesso,
            justificativa
        );
    }

    private PanoramaCompraDTO montarPanorama(int horizonteDias, LocalDate inicio, LocalDate fim) {
        String horizonteStr = String.valueOf(horizonteDias);
        ValorPrevistoDTO faturamento = agregarPrevisao(previsaoService.prever("faturamento", horizonteStr));
        ValorPrevistoDTO pedidos = agregarPrevisao(previsaoService.prever("pedidos", horizonteStr));

        List<FeriadoDTO> datasEspeciais = new ArrayList<>();
        for (Feriado f : feriados.feriadosNoIntervalo(inicio, fim)) {
            datasEspeciais.add(new FeriadoDTO(f.data(), f.nome(), f.categoria().name()));
        }
        return new PanoramaCompraDTO(faturamento, pedidos, datasEspeciais);
    }

    /** Soma os pontos diários de uma previsão num único total (com banda). */
    private ValorPrevistoDTO agregarPrevisao(PrevisaoDTO previsao) {
        double total = 0.0;
        double inferior = 0.0;
        double superior = 0.0;
        for (PontoPrevisaoDTO p : previsao.pontos()) {
            total += p.valor();
            inferior += p.limiteInferior();
            superior += p.limiteSuperior();
        }
        return new ValorPrevistoDTO(
            arredondar(total, 2), arredondar(inferior, 2), arredondar(superior, 2),
            previsao.metodo(), previsao.mape()
        );
    }

    private void montarAvisos(List<String> avisos, List<LinhaCompraDTO> compras, PanoramaCompraDTO panorama) {
        long naoConfiaveis = compras.stream().filter(l -> !l.estoqueConfiavel()).count();
        if (naoConfiaveis > 0) {
            avisos.add(naoConfiaveis + " insumo(s) com saldo não confiável (negativo ou não cadastrado): "
                + "assumido estoque 0 e sinalizado por linha.");
        }
        avisos.add("Custos estimados pelo preço de custo atual — o sistema legado não registra o valor de compra.");
        avisos.add("O controle de estoque do sistema legado é parcial: use este relatório como estimativa de "
            + "apoio à compra, não como ordem de compra.");
        if (!panorama.datasEspeciais().isEmpty()) {
            avisos.add("O horizonte contém datas comemorativas — a previsão já reflete o pico esperado, "
                + "mas confira os itens críticos dessas datas.");
        }
    }

    private String baseHistorica(Previsao previsao) {
        StringBuilder sb = new StringBuilder();
        sb.append("Consumo dos últimos ").append(JANELA_MIX_DIAS).append(" dias; ")
          .append("previsão de custo treinada em ").append(previsao.diasHistorico()).append(" dias de histórico. ")
          .append("Método: ").append(previsao.metodo().name());
        if (!Double.isNaN(previsao.mape())) {
            sb.append(" (MAPE ").append(arredondar(previsao.mape(), 1)).append("%)");
        }
        sb.append('.');
        return sb.toString();
    }

    private String justificar(
        ResumoInsumo resumo, double consumoPrevisto, double seguranca,
        double estoqueEfetivo, boolean confiavel, double compra, String sigla, int horizonteDias
    ) {
        String u = sigla != null && !sigla.isBlank() ? " " + sigla : "";
        StringBuilder sb = new StringBuilder();
        sb.append("Consumo recente: ").append(arredondar(resumo.quantidade, 2)).append(u)
          .append(" em ").append(JANELA_MIX_DIAS).append(" dias. ")
          .append("Previsto p/ ").append(horizonteDias).append(" dias: ")
          .append(arredondar(consumoPrevisto, 2)).append(u).append(". ")
          .append("Estoque: ").append(confiavel ? arredondar(estoqueEfetivo, 2) + u : "não confiável (assumido 0)")
          .append(". Segurança: ").append(arredondar(seguranca, 2)).append(u).append(". → ");
        if (compra > EPSILON) {
            sb.append("Comprar ").append(arredondar(compra, 2)).append(u).append('.');
        } else {
            sb.append("Estoque suficiente — sem compra.");
        }
        return sb.toString();
    }

    private static String classificarRisco(Double diasCobertura) {
        if (diasCobertura == null) {
            return "baixo"; // sem consumo previsto → não rompe
        }
        if (diasCobertura < RUPTURA_ALTA_DIAS) {
            return "alto";
        }
        if (diasCobertura < RUPTURA_MEDIA_DIAS) {
            return "medio";
        }
        return "baixo";
    }

    private static int prioridadeRisco(String risco) {
        return switch (risco) {
            case "alto" -> 0;
            case "medio" -> 1;
            default -> 2;
        };
    }

    /** Série contígua: dias sem movimento (fechado / sem saída) entram como 0. */
    private static Map<LocalDate, Double> preencherZeros(LocalDate inicio, LocalDate fim, Map<LocalDate, Double> bruto) {
        Map<LocalDate, Double> serie = new HashMap<>();
        for (LocalDate d = inicio; !d.isAfter(fim); d = d.plusDays(1)) {
            serie.put(d, bruto.getOrDefault(d, 0.0));
        }
        return serie;
    }

    /**
     * Converte "30", "30d", "4s", "3m", "1a" em dias. Sufixos: d=dia, s=semana,
     * m=mês (30d), a=ano (365d). Vazio → {@link #HORIZONTE_PADRAO}. Valida 1..{@link #HORIZONTE_MAX}.
     */
    private static int parseHorizonte(String horizonte) {
        if (horizonte == null || horizonte.isBlank()) {
            return HORIZONTE_PADRAO;
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

    private static String mensagemHorizonte() {
        return """
            Parâmetro 'horizonte' inválido.

            Use um número de dias, opcionalmente com sufixo:
            d=dias, s=semanas, m=meses, a=anos (ex.: '30', '4s', '1m').

            Exemplo correto:
            /relatorios/previsao-compras?horizonte=30d
            """;
    }

    private static double soma(double[] v) {
        double s = 0.0;
        for (double x : v) {
            s += x;
        }
        return s;
    }

    private static double desvioPadrao(double[] v, double media) {
        if (v.length == 0) {
            return 0.0;
        }
        double soma = 0.0;
        for (double x : v) {
            double d = x - media;
            soma += d * d;
        }
        return Math.sqrt(soma / v.length);
    }

    private static double arredondar(double v, int casas) {
        double f = Math.pow(10, casas);
        return Math.round(v * f) / f;
    }
}
