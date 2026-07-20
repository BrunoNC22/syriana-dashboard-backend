package com.firebird_data_extractor.www.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.firebird_data_extractor.www.config.ClockConfig;

/**
 * Leitura de {@code dataAtualSimulada} do server-config.json.
 *
 * <p>O contrato importante aqui é o do <b>fallback</b>: qualquer problema
 * (arquivo ausente, campo ausente, data malformada) cai no relógio real em vez
 * de derrubar o boot — mas nunca em silêncio. Um relógio errado que passa
 * despercebido faria toda métrica mentir sem sintoma óbvio.
 */
class ClockConfigTest {

    @TempDir
    Path dir;

    private Path escrever(String json) throws IOException {
        Path arquivo = dir.resolve("server-config.json");
        Files.writeString(arquivo, json);
        return arquivo;
    }

    @Test
    @DisplayName("arquivo inexistente → relógio real")
    void arquivoInexistente() {
        assertNull(ClockConfig.lerDataSimulada(dir.resolve("nao-existe.json")));
    }

    @Test
    @DisplayName("campo ausente → relógio real")
    void campoAusente() throws IOException {
        assertNull(ClockConfig.lerDataSimulada(escrever("{\"host\":\"localhost\",\"port\":3050}")));
    }

    @Test
    @DisplayName("campo em branco → relógio real")
    void campoEmBranco() throws IOException {
        assertNull(ClockConfig.lerDataSimulada(escrever("{\"dataAtualSimulada\":\"   \"}")));
    }

    @Test
    @DisplayName("campo nulo → relógio real")
    void campoNulo() throws IOException {
        assertNull(ClockConfig.lerDataSimulada(escrever("{\"dataAtualSimulada\":null}")));
    }

    @Test
    @DisplayName("data em formato não-ISO → relógio real, sem estourar")
    void formatoInvalido() throws IOException {
        assertNull(ClockConfig.lerDataSimulada(escrever("{\"dataAtualSimulada\":\"26/04/2026\"}")));
    }

    @Test
    @DisplayName("data inexistente no calendário → relógio real")
    void dataInexistente() throws IOException {
        assertNull(ClockConfig.lerDataSimulada(escrever("{\"dataAtualSimulada\":\"2026-02-31\"}")));
    }

    @Test
    @DisplayName("JSON corrompido → relógio real, sem derrubar o boot")
    void jsonCorrompido() throws IOException {
        assertNull(ClockConfig.lerDataSimulada(escrever("{ isto não é json")));
    }

    @Test
    @DisplayName("data válida é usada, tolerando espaços em volta")
    void dataValida() throws IOException {
        assertEquals(
            LocalDate.of(2026, 4, 26),
            ClockConfig.lerDataSimulada(escrever("{\"dataAtualSimulada\":\" 2026-04-26 \"}"))
        );
    }

    @Test
    @DisplayName("os demais campos do arquivo são ignorados")
    void ignoraOutrosCampos() throws IOException {
        String json = """
            {
              "host": "localhost",
              "port": 3050,
              "user": "SYSDBA",
              "password": "masterkey",
              "adminPassword": "admin",
              "dataAtualSimulada": "2026-04-26",
              "campoFuturoDesconhecido": true
            }
            """;

        assertEquals(LocalDate.of(2026, 4, 26), ClockConfig.lerDataSimulada(escrever(json)));
    }
}
