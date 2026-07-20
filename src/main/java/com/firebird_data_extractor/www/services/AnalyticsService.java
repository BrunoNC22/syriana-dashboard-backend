package com.firebird_data_extractor.www.services;

import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.firebird_data_extractor.www.dto.InsumoCapitalParadoDTO;
import com.firebird_data_extractor.www.dto.InsumoForaDoPadraoDTO;
import com.firebird_data_extractor.www.dto.InsumoPoucoUtilizadoDTO;
import com.firebird_data_extractor.www.dto.InsumoQuedaConsumoDTO;
import com.firebird_data_extractor.www.dto.InsumosBaixoUsoDTO;
import com.firebird_data_extractor.www.dto.ProdutoAbandonadoDTO;
import com.firebird_data_extractor.www.dto.ProdutoQuedaDTO;
import com.firebird_data_extractor.www.dto.ProdutoSemVendaDTO;
import com.firebird_data_extractor.www.dto.ProdutoTendenciaQuedaDTO;
import com.firebird_data_extractor.www.dto.ProdutosBaixoDesempenhoDTO;
import com.firebird_data_extractor.www.repositories.AnalyticsRepository;
import com.firebird_data_extractor.www.repositories.AnalyticsRepository.ConsumoAgregado;
import com.firebird_data_extractor.www.repositories.AnalyticsRepository.ConsumoDiario;
import com.firebird_data_extractor.www.repositories.AnalyticsRepository.InsumoUniverso;
import com.firebird_data_extractor.www.repositories.AnalyticsRepository.ProdutoUniverso;
import com.firebird_data_extractor.www.repositories.AnalyticsRepository.VendaAgregada;
import com.firebird_data_extractor.www.repositories.AnalyticsRepository.VendaDiaria;
import com.firebird_data_extractor.www.repositories.AnalyticsRepository.VendaMensal;

/**
 * Classifica produtos de baixo desempenho sobre o cardápio ativo vendável.
 *
 * A altitude do projeto: o repositório agrega no SQL; aqui em Java rodam as
 * regras (anti-join, variação, regressão) sobre o resultado agregado — volume
 * pequeno (algumas centenas de produtos).
 */
@Service
public class AnalyticsService {

    /**
     * Piso de volume: quantidade mínima no período de referência para um item
     * entrar nas listas de queda/tendência. Evita falsos alarmes de itens
     * marginais (ex.: vendeu 1 unidade e virou 0 → "queda de 100%").
     */
    private static final double PISO_QUANTIDADE = 5.0;

    /** Nº mínimo de meses distintos com venda (em 6) para ser "abandonado". */
    private static final int ABANDONADO_MESES_MINIMOS = 3;

    /** Janela, em meses, para avaliar "abandonados". */
    private static final int ABANDONADO_JANELA_MESES = 6;

    /** Dias sem qualquer venda que caracterizam inatividade recente. */
    private static final int INATIVIDADE_DIAS = 30;

    /** Idade máxima da primeira venda para o produto ser marcado como "novo". */
    private static final int NOVO_DIAS = 30;

    /** Nº mínimo de semanas para ajustar uma reta de tendência com sentido. */
    private static final int TENDENCIA_MIN_SEMANAS = 3;

    /** Tamanho máximo de cada lista retornada (as mais críticas no topo). */
    private static final int LIMITE_LISTA = 50;

    /**
     * Piso de consumo, em R$, no período anterior para um insumo entrar na lista
     * de queda de consumo. Em R$ (não em quantidade) porque insumos têm unidades
     * heterogêneas (kg, L, un) — o custo é a régua comparável entre eles.
     */
    private static final double PISO_CUSTO_INSUMO = 50.0;

    /** Janela de referência (semanas) para o z-score do consumo de insumo. */
    private static final int ZSCORE_JANELA_SEMANAS = 12;

    /** Nº mínimo de semanas com consumo na janela para o z-score ter sentido. */
    private static final int ZSCORE_MIN_SEMANAS_COM_CONSUMO = 3;

    /** |z| a partir do qual a última semana é considerada "fora do padrão". */
    private static final double ZSCORE_LIMITE = 2.0;

    private final AnalyticsRepository repository;
    private final Clock clock;

    public AnalyticsService(AnalyticsRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    public ProdutosBaixoDesempenhoDTO analisarProdutos(
        LocalDate dataInicio,
        LocalDate dataFim
    ) {
        LocalDate hoje = LocalDate.now(clock);

        // Período anterior de mesma duração (comparação "período anterior").
        long dias = ChronoUnit.DAYS.between(dataInicio, dataFim) + 1;
        LocalDate inicioAnterior = dataInicio.minusDays(dias);
        LocalDate fimAnterior = dataInicio.minusDays(1);

        List<ProdutoUniverso> universo = repository.getCatalogoVendavelAtivo();

        Map<String, VendaAgregada> agregados = new HashMap<>();
        for (VendaAgregada a : repository.getAgregadoVendasPorProduto(
            dataInicio, dataFim, inicioAnterior, fimAnterior
        )) {
            agregados.put(a.id(), a);
        }

        List<ProdutoSemVendaDTO> semVendas = classificarSemVendas(universo, agregados, hoje);
        List<ProdutoQuedaDTO> maioresQuedas = classificarQuedas(universo, agregados);
        List<ProdutoTendenciaQuedaDTO> tendencia = classificarTendencia(universo, dataInicio, dataFim, hoje);
        List<ProdutoAbandonadoDTO> abandonados = classificarAbandonados(universo, agregados, hoje);

        return new ProdutosBaixoDesempenhoDTO(semVendas, maioresQuedas, tendencia, abandonados);
    }

    private boolean isNovo(VendaAgregada agg, LocalDate hoje) {
        return agg != null
            && agg.primeiraVenda() != null
            && agg.primeiraVenda().isAfter(hoje.minusDays(NOVO_DIAS));
    }

    /** Itens ativos com quantidade zero no período selecionado. */
    private List<ProdutoSemVendaDTO> classificarSemVendas(
        List<ProdutoUniverso> universo,
        Map<String, VendaAgregada> agregados,
        LocalDate hoje
    ) {
        List<ProdutoSemVendaDTO> lista = new ArrayList<>();
        for (ProdutoUniverso p : universo) {
            VendaAgregada agg = agregados.get(p.id());
            double qtdAtual = agg != null ? agg.qtdAtual() : 0.0;
            if (qtdAtual > 0.0) {
                continue;
            }
            LocalDate ultimaVenda = agg != null ? agg.ultimaVenda() : null;
            lista.add(new ProdutoSemVendaDTO(
                p.id(), p.nome(), p.etiqueta(), ultimaVenda, isNovo(agg, hoje)
            ));
        }
        // Quem parou de vender há menos tempo primeiro (mais acionável); nunca
        // vendidos (última venda null) por último.
        lista.sort(
            Comparator.comparing(
                ProdutoSemVendaDTO::ultimaVenda,
                Comparator.nullsFirst(Comparator.naturalOrder())
            ).reversed()
        );
        return limitar(lista);
    }

    /** Maiores quedas % vs. o período anterior (só quem tinha volume antes). */
    private List<ProdutoQuedaDTO> classificarQuedas(
        List<ProdutoUniverso> universo,
        Map<String, VendaAgregada> agregados
    ) {
        List<ProdutoQuedaDTO> lista = new ArrayList<>();
        for (ProdutoUniverso p : universo) {
            VendaAgregada agg = agregados.get(p.id());
            if (agg == null || agg.qtdAnterior() < PISO_QUANTIDADE) {
                continue;
            }
            if (agg.qtdAtual() >= agg.qtdAnterior()) {
                continue;
            }
            double variacao = (agg.qtdAtual() - agg.qtdAnterior()) / agg.qtdAnterior() * 100.0;
            lista.add(new ProdutoQuedaDTO(
                p.id(), p.nome(), p.etiqueta(),
                agg.qtdAtual(), agg.qtdAnterior(), variacao
            ));
        }
        // Queda mais acentuada (variação mais negativa) primeiro.
        lista.sort(Comparator.comparingDouble(ProdutoQuedaDTO::variacaoPercentual));
        return limitar(lista);
    }

    /** Tendência de queda na série semanal do período (slope < 0). */
    private List<ProdutoTendenciaQuedaDTO> classificarTendencia(
        List<ProdutoUniverso> universo,
        LocalDate dataInicio,
        LocalDate dataFim,
        LocalDate hoje
    ) {
        // Ignora semanas futuras (evita queda artificial até zero no fim).
        LocalDate fimEfetivo = dataFim.isAfter(hoje) ? hoje : dataFim;
        if (fimEfetivo.isBefore(dataInicio)) {
            return List.of();
        }

        long totalDias = ChronoUnit.DAYS.between(dataInicio, fimEfetivo) + 1;
        int nSemanas = (int) Math.ceil(totalDias / 7.0);
        if (nSemanas < TENDENCIA_MIN_SEMANAS) {
            return List.of();
        }

        Map<String, double[]> semanasPorProduto = new HashMap<>();
        for (VendaDiaria v : repository.getVendasDiariasPorProduto(dataInicio, fimEfetivo)) {
            long offset = ChronoUnit.DAYS.between(dataInicio, v.dia());
            int semana = (int) (offset / 7);
            if (semana < 0 || semana >= nSemanas) {
                continue;
            }
            double[] buckets = semanasPorProduto.computeIfAbsent(v.id(), k -> new double[nSemanas]);
            buckets[semana] += v.qtd();
        }

        Map<String, ProdutoUniverso> universoPorId = indexar(universo);

        List<ProdutoTendenciaQuedaDTO> lista = new ArrayList<>();
        for (Map.Entry<String, double[]> entry : semanasPorProduto.entrySet()) {
            ProdutoUniverso p = universoPorId.get(entry.getKey());
            if (p == null) {
                continue;
            }
            double[] buckets = entry.getValue();
            double total = 0.0;
            for (double v : buckets) {
                total += v;
            }
            if (total < PISO_QUANTIDADE) {
                continue;
            }
            double slope = slopePorSemana(buckets);
            if (slope >= 0.0) {
                continue;
            }
            lista.add(new ProdutoTendenciaQuedaDTO(
                p.id(), p.nome(), p.etiqueta(), slope, total, nSemanas
            ));
        }
        // Queda mais acentuada (slope mais negativo) primeiro.
        lista.sort(Comparator.comparingDouble(ProdutoTendenciaQuedaDTO::slopePorSemana));
        return limitar(lista);
    }

    /** Vendiam com regularidade (≥3 meses em 6) e pararam há >30 dias. */
    private List<ProdutoAbandonadoDTO> classificarAbandonados(
        List<ProdutoUniverso> universo,
        Map<String, VendaAgregada> agregados,
        LocalDate hoje
    ) {
        LocalDate inicioJanela = hoje.minusMonths(ABANDONADO_JANELA_MESES);

        record Resumo(int meses, double total) {}
        Map<String, int[]> mesesPorProduto = new HashMap<>();
        Map<String, double[]> totalPorProduto = new HashMap<>();
        for (VendaMensal v : repository.getVendasMensaisPorProduto(inicioJanela, hoje)) {
            if (v.qtd() <= 0.0) {
                continue;
            }
            mesesPorProduto.computeIfAbsent(v.id(), k -> new int[]{0})[0]++;
            totalPorProduto.computeIfAbsent(v.id(), k -> new double[]{0.0})[0] += v.qtd();
        }

        LocalDate limiteInatividade = hoje.minusDays(INATIVIDADE_DIAS);
        Map<String, ProdutoUniverso> universoPorId = indexar(universo);

        List<ProdutoAbandonadoDTO> lista = new ArrayList<>();
        for (Map.Entry<String, int[]> entry : mesesPorProduto.entrySet()) {
            String id = entry.getKey();
            int meses = entry.getValue()[0];
            if (meses < ABANDONADO_MESES_MINIMOS) {
                continue;
            }
            ProdutoUniverso p = universoPorId.get(id);
            if (p == null) {
                continue;
            }
            VendaAgregada agg = agregados.get(id);
            LocalDate ultimaVenda = agg != null ? agg.ultimaVenda() : null;
            // "0 nos últimos 30 dias": sem venda ou última venda anterior ao limite.
            boolean inativoRecente = ultimaVenda == null || ultimaVenda.isBefore(limiteInatividade);
            if (!inativoRecente) {
                continue;
            }
            double total = totalPorProduto.get(id)[0];
            if (total < PISO_QUANTIDADE) {
                continue;
            }
            lista.add(new ProdutoAbandonadoDTO(
                p.id(), p.nome(), p.etiqueta(), ultimaVenda, meses, total
            ));
        }
        // Maior volume perdido primeiro.
        lista.sort(Comparator.comparingDouble(ProdutoAbandonadoDTO::quantidadeUltimos6Meses).reversed());
        return limitar(lista);
    }

    private static Map<String, ProdutoUniverso> indexar(List<ProdutoUniverso> universo) {
        Map<String, ProdutoUniverso> map = new HashMap<>();
        for (ProdutoUniverso p : universo) {
            map.put(p.id(), p);
        }
        return map;
    }

    private static <T> List<T> limitar(List<T> lista) {
        return lista.size() > LIMITE_LISTA ? new ArrayList<>(lista.subList(0, LIMITE_LISTA)) : lista;
    }

    /**
     * Inclinação da regressão linear (mínimos quadrados) de y sobre x=0..n-1.
     * Retorna unidades por semana; 0 quando não há variação de x.
     */
    private static double slopePorSemana(double[] y) {
        int n = y.length;
        double somaX = 0, somaY = 0, somaXX = 0, somaXY = 0;
        for (int i = 0; i < n; i++) {
            somaX += i;
            somaY += y[i];
            somaXX += (double) i * i;
            somaXY += (double) i * y[i];
        }
        double denominador = n * somaXX - somaX * somaX;
        if (denominador == 0.0) {
            return 0.0;
        }
        return (n * somaXY - somaX * somaY) / denominador;
    }

    // ==================================================================
    // Insumos de baixo uso (espelho do produto, sobre ESTOQUEMOVIMENTACAO 'S').
    // ==================================================================

    public InsumosBaixoUsoDTO analisarInsumos(
        LocalDate dataInicio,
        LocalDate dataFim
    ) {
        LocalDate hoje = LocalDate.now(clock);

        // Período anterior de mesma duração (comparação "período anterior").
        long dias = ChronoUnit.DAYS.between(dataInicio, dataFim) + 1;
        LocalDate inicioAnterior = dataInicio.minusDays(dias);
        LocalDate fimAnterior = dataInicio.minusDays(1);

        List<InsumoUniverso> universo = repository.getCatalogoInsumoAtivo();

        Map<String, ConsumoAgregado> agregados = new HashMap<>();
        for (ConsumoAgregado c : repository.getAgregadoConsumoPorInsumo(
            dataInicio, dataFim, inicioAnterior, fimAnterior
        )) {
            agregados.put(c.id(), c);
        }

        List<InsumoPoucoUtilizadoDTO> poucoUtilizados = classificarPoucoUtilizados(universo, agregados, hoje);
        List<InsumoCapitalParadoDTO> capitalParado = classificarCapitalParado(universo, agregados);
        List<InsumoQuedaConsumoDTO> quedaConsumo = classificarQuedaConsumo(universo, agregados);
        List<InsumoForaDoPadraoDTO> foraDoPadrao = classificarForaDoPadrao(universo, dataFim, hoje);

        return new InsumosBaixoUsoDTO(poucoUtilizados, capitalParado, quedaConsumo, foraDoPadrao);
    }

    private boolean isNovoInsumo(ConsumoAgregado agg, LocalDate hoje) {
        return agg != null
            && agg.primeiroUso() != null
            && agg.primeiroUso().isAfter(hoje.minusDays(NOVO_DIAS));
    }

    /** Todos os insumos ativos ranqueados por consumo crescente (0 = sem uso no topo). */
    private List<InsumoPoucoUtilizadoDTO> classificarPoucoUtilizados(
        List<InsumoUniverso> universo,
        Map<String, ConsumoAgregado> agregados,
        LocalDate hoje
    ) {
        List<InsumoPoucoUtilizadoDTO> lista = new ArrayList<>();
        for (InsumoUniverso ins : universo) {
            ConsumoAgregado agg = agregados.get(ins.id());
            double qtd = agg != null ? agg.qtdAtual() : 0.0;
            double custo = agg != null ? agg.custoAtual() : 0.0;
            LocalDate ultimoUso = agg != null ? agg.ultimoUso() : null;
            lista.add(new InsumoPoucoUtilizadoDTO(
                ins.id(), ins.nome(), ins.sigla(), qtd, custo, ultimoUso, isNovoInsumo(agg, hoje)
            ));
        }
        // Menor consumo primeiro; entre empates, o que ficou parado há mais tempo
        // (nunca usado — última saída null — primeiro).
        lista.sort(
            Comparator.comparingDouble(InsumoPoucoUtilizadoDTO::quantidadeConsumida)
                .thenComparing(
                    InsumoPoucoUtilizadoDTO::ultimoUso,
                    Comparator.nullsFirst(Comparator.naturalOrder())
                )
        );
        return limitar(lista);
    }

    /** Insumos com saldo em mãos (> 0) e nenhum consumo no período. */
    private List<InsumoCapitalParadoDTO> classificarCapitalParado(
        List<InsumoUniverso> universo,
        Map<String, ConsumoAgregado> agregados
    ) {
        List<InsumoCapitalParadoDTO> lista = new ArrayList<>();
        for (InsumoUniverso ins : universo) {
            if (ins.estoqueAtual() <= 0.0) {
                continue;
            }
            ConsumoAgregado agg = agregados.get(ins.id());
            double qtdAtual = agg != null ? agg.qtdAtual() : 0.0;
            if (qtdAtual > 0.0) {
                continue; // girou no período → não é capital parado
            }
            LocalDate ultimoUso = agg != null ? agg.ultimoUso() : null;
            lista.add(new InsumoCapitalParadoDTO(
                ins.id(), ins.nome(), ins.sigla(),
                ins.estoqueAtual(), ins.custoEstoque(), ultimoUso
            ));
        }
        // Maior valor imobilizado primeiro.
        lista.sort(Comparator.comparingDouble(InsumoCapitalParadoDTO::custoEstoqueParado).reversed());
        return limitar(lista);
    }

    /** Maiores quedas % de consumo vs. o período anterior (piso de R$ no anterior). */
    private List<InsumoQuedaConsumoDTO> classificarQuedaConsumo(
        List<InsumoUniverso> universo,
        Map<String, ConsumoAgregado> agregados
    ) {
        List<InsumoQuedaConsumoDTO> lista = new ArrayList<>();
        for (InsumoUniverso ins : universo) {
            ConsumoAgregado agg = agregados.get(ins.id());
            if (agg == null || agg.custoAnterior() < PISO_CUSTO_INSUMO || agg.qtdAnterior() <= 0.0) {
                continue;
            }
            if (agg.qtdAtual() >= agg.qtdAnterior()) {
                continue;
            }
            double variacao = (agg.qtdAtual() - agg.qtdAnterior()) / agg.qtdAnterior() * 100.0;
            lista.add(new InsumoQuedaConsumoDTO(
                ins.id(), ins.nome(), ins.sigla(),
                agg.qtdAtual(), agg.qtdAnterior(), variacao
            ));
        }
        // Queda mais acentuada (variação mais negativa) primeiro.
        lista.sort(Comparator.comparingDouble(InsumoQuedaConsumoDTO::variacaoPercentual));
        return limitar(lista);
    }

    /** Consumo da última semana desviando (|z| ≥ 2) do histórico do próprio insumo. */
    private List<InsumoForaDoPadraoDTO> classificarForaDoPadrao(
        List<InsumoUniverso> universo,
        LocalDate dataFim,
        LocalDate hoje
    ) {
        // Ignora o futuro: a janela termina no fim do período ou hoje, o que vier antes.
        LocalDate fimEfetivo = dataFim.isAfter(hoje) ? hoje : dataFim;
        int nSemanas = ZSCORE_JANELA_SEMANAS;
        LocalDate inicioRef = fimEfetivo.minusDays((long) nSemanas * 7 - 1);

        Map<String, double[]> semanasPorInsumo = new HashMap<>();
        for (ConsumoDiario c : repository.getConsumoDiarioPorInsumo(inicioRef, fimEfetivo)) {
            long offset = ChronoUnit.DAYS.between(inicioRef, c.dia());
            int semana = (int) (offset / 7);
            if (semana < 0 || semana >= nSemanas) {
                continue;
            }
            double[] buckets = semanasPorInsumo.computeIfAbsent(c.id(), k -> new double[nSemanas]);
            buckets[semana] += c.qtd();
        }

        Map<String, InsumoUniverso> universoPorId = indexarInsumos(universo);

        List<InsumoForaDoPadraoDTO> lista = new ArrayList<>();
        for (Map.Entry<String, double[]> entry : semanasPorInsumo.entrySet()) {
            InsumoUniverso ins = universoPorId.get(entry.getKey());
            if (ins == null) {
                continue; // consumido, mas fora do catálogo ativo
            }
            double[] semanas = entry.getValue();

            int semanasComConsumo = 0;
            for (double v : semanas) {
                if (v > 0.0) {
                    semanasComConsumo++;
                }
            }
            if (semanasComConsumo < ZSCORE_MIN_SEMANAS_COM_CONSUMO) {
                continue;
            }

            double media = media(semanas);
            double desvio = desvioPadrao(semanas, media);
            if (desvio <= 0.0) {
                continue; // consumo constante → sem desvio possível
            }

            double ultima = semanas[nSemanas - 1];
            double z = (ultima - media) / desvio;
            if (Math.abs(z) < ZSCORE_LIMITE) {
                continue;
            }
            lista.add(new InsumoForaDoPadraoDTO(
                ins.id(), ins.nome(), ins.sigla(), ultima, media, desvio, z
            ));
        }
        // Desvio mais forte (|z| maior) primeiro.
        lista.sort(Comparator.comparingDouble((InsumoForaDoPadraoDTO d) -> Math.abs(d.zScore())).reversed());
        return limitar(lista);
    }

    private static Map<String, InsumoUniverso> indexarInsumos(List<InsumoUniverso> universo) {
        Map<String, InsumoUniverso> map = new HashMap<>();
        for (InsumoUniverso ins : universo) {
            map.put(ins.id(), ins);
        }
        return map;
    }

    private static double media(double[] xs) {
        if (xs.length == 0) {
            return 0.0;
        }
        double soma = 0.0;
        for (double x : xs) {
            soma += x;
        }
        return soma / xs.length;
    }

    /** Desvio padrão populacional de xs em torno da média já calculada. */
    private static double desvioPadrao(double[] xs, double media) {
        if (xs.length == 0) {
            return 0.0;
        }
        double soma = 0.0;
        for (double x : xs) {
            double d = x - media;
            soma += d * d;
        }
        return Math.sqrt(soma / xs.length);
    }
}
