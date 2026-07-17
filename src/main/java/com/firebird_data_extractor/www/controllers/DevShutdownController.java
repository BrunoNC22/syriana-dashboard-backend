package com.firebird_data_extractor.www.controllers;

import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoint de shutdown usado SOMENTE no fluxo do native-image-agent (profile
 * "native-agent" — nunca ativo em produção).
 *
 * Motivo: o NX mata a árvore de processos abruptamente no Ctrl+C (taskkill /F),
 * impedindo o JVM de rodar os shutdown hooks — que é onde o agent grava as
 * configs de reflection e apaga o arquivo .lock. Encerrar via este endpoint
 * dispara o graceful shutdown por dentro do processo, garantindo a escrita
 * das configs independentemente de como o wrapper for encerrado.
 */
@RestController
@Profile("native-agent")
public class DevShutdownController {

    private final ConfigurableApplicationContext context;

    public DevShutdownController(ConfigurableApplicationContext context) {
        this.context = context;
    }

    @PostMapping("/dev/shutdown")
    public String shutdown() {
        Thread shutdownThread = new Thread(() -> {
            try {
                // Dá tempo da resposta HTTP ser enviada antes de derrubar o servidor
                Thread.sleep(300);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            System.exit(SpringApplication.exit(context, () -> 0));
        });
        shutdownThread.setDaemon(false);
        shutdownThread.start();

        return "Encerrando backend com graceful shutdown — configs de reflection serão gravadas.";
    }
}
