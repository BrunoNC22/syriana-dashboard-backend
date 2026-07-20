package com.firebird_data_extractor.www.config;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Relógio de negócio da aplicação — a fonte de "que dia é hoje" para métricas,
 * comparações e previsões.
 *
 * <p><b>Por que existe:</b> o banco de desenvolvimento tem dados só até certa
 * data. Com o relógio do sistema, tudo que depende do presente (previsão,
 * sparklines, "produtos abandonados") olha para dias sem movimento. O campo
 * {@code dataAtualSimulada} do {@code server-config.json} faz a aplicação
 * acreditar que hoje é aquele dia.
 *
 * <p><b>Nunca vaza para produção:</b> em produção o backend roda como native
 * image, e ali o campo é ignorado incondicionalmente. Como desenvolvimento é
 * sempre JVM e produção é sempre o {@code www.exe}, a proteção não depende de
 * ninguém lembrar de ativar nada.
 *
 * <p><b>Cuidado — são dois relógios.</b> Este é o de <i>negócio</i>. Timestamp
 * de log e expiração de cache (TTL) usam o de <i>parede</i>
 * ({@code System.currentTimeMillis()}) e devem continuar assim: TTL calculado
 * sobre um relógio congelado nunca expiraria.
 */
// proxyBeanMethods = false: sem subclasse CGLIB, e portanto sem metadata de
// proxy para o native-image manter em dia. O bean não chama outro @Bean daqui.
@Configuration(proxyBeanMethods = false)
public class ClockConfig {

    private static final Logger log = LoggerFactory.getLogger(ClockConfig.class);

    private static final String CONFIG_FILE_NAME = "server-config.json";

    private static final String CAMPO_DATA = "dataAtualSimulada";

    /** Propriedade que o próprio GraalVM define dentro do binário nativo. */
    private static final String NATIVE_IMAGE_PROPERTY = "org.graalvm.nativeimage.imagecode";

    @Bean
    public Clock clock() {
        ZoneId zona = ZoneId.systemDefault();

        if (System.getProperty(NATIVE_IMAGE_PROPERTY) != null) {
            // Produção: a data simulada é ignorada mesmo se estiver no arquivo.
            return Clock.systemDefaultZone();
        }

        LocalDate simulada = lerDataSimulada(
            Path.of(System.getProperty("user.dir"), CONFIG_FILE_NAME)
        );

        if (simulada == null) {
            log.info("Relógio: sistema ({})", LocalDate.now(zona));
            return Clock.systemDefaultZone();
        }

        log.warn(
            "Relógio SIMULADO: a aplicação vai considerar que hoje é {}. "
                + "Definido por 'dataAtualSimulada' no {}.",
            simulada,
            CONFIG_FILE_NAME
        );

        return Clock.fixed(simulada.atStartOfDay(zona).toInstant(), zona);
    }

    /**
     * Lê {@code dataAtualSimulada} do server-config.json.
     *
     * <p>Usa a árvore do Jackson em vez de desserializar para um record: sem
     * classe de destino não há reflection para registrar no native-image — e o
     * arquivo tem vários campos que não interessam aqui.
     *
     * <p>Recebe o caminho em vez de montá-lo, e é pública, para poder ser
     * testada sem depender do {@code user.dir} de quem roda a suíte (os testes
     * vivem em {@code www.tests}). Não é ponto de extensão — use o bean.
     *
     * @return a data, ou {@code null} se o arquivo/campo não existir ou a data
     *         for inválida — qualquer problema cai no relógio real, sempre com
     *         aviso no log (falhar em silêncio aqui custaria horas de confusão).
     */
    public static LocalDate lerDataSimulada(Path configPath) {

        if (!Files.isRegularFile(configPath)) {
            return null;
        }

        try {
            String valor = new ObjectMapper()
                .readTree(configPath.toFile())
                .path(CAMPO_DATA)
                .asText(null);

            if (valor == null || valor.isBlank()) {
                return null;
            }

            return LocalDate.parse(valor.trim());

        } catch (DateTimeParseException e) {
            log.warn(
                "'dataAtualSimulada' não está no formato ISO (yyyy-MM-dd): {}. "
                    + "Usando o relógio do sistema.",
                e.getParsedString()
            );
            return null;

        } catch (Exception e) {
            log.warn(
                "Não foi possível ler '{}' para o relógio simulado ({}). "
                    + "Usando o relógio do sistema.",
                CONFIG_FILE_NAME,
                e.getMessage()
            );
            return null;
        }
    }
}
