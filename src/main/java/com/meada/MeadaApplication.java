package com.meada;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Ponto de entrada do backend Meada.
 *
 * @SpringBootApplication habilita component-scan a partir deste pacote
 * (com.meada) e a auto-configuração do Spring Boot — incluindo o
 * DataSource/Hikari a partir das propriedades spring.datasource.* do
 * application.yml. Nenhum bean é declarado manualmente aqui; configurações
 * específicas entram em classes próprias quando necessário.
 *
 * @ConfigurationPropertiesScan registra os records @ConfigurationProperties do
 * pacote (ex. OutboundRetryProperties) como beans, sem precisar enumerá-los.
 *
 * @EnableScheduling liga o agendamento de tarefas do Spring (camada 5.19 #63) — o
 * ReminderJob roda em @Scheduled(fixedDelay). O intervalo é grande por padrão e o job
 * só age sobre linhas DUE (agendamentos futuros próximos), então não dispara em testes.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
public class MeadaApplication {

    public static void main(String[] args) {
        SpringApplication.run(MeadaApplication.class, args);
    }
}
