package com.acme.orders;

import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.reactive.function.client.WebClient;

/** Minimal application: an embedded H2 datasource, a transaction manager, and a RestTemplate. */
@SpringBootConfiguration
@EnableAutoConfiguration(exclude = KafkaAutoConfiguration.class)
@ComponentScan
public class PuretxTestApplication {

    @Bean
    RestTemplate restTemplate(final RestTemplateBuilder builder) {
        return builder.build();
    }

    @Bean
    RestClient restClient(final RestClient.Builder builder) {
        return builder.build();
    }

    @Bean
    WebClient webClient(final WebClient.Builder builder) {
        return builder.build();
    }

    @Bean
    StubHttpServer stubHttpServer() {
        return new StubHttpServer();
    }
}
