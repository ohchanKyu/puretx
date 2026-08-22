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

    /**
     * Built the way applications actually build one: the static factory, not the injected
     * {@code RestClient.Builder} bean. No {@code RestClientCustomizer} is ever consulted for this,
     * which is exactly the case puretx used to miss entirely.
     */
    @Bean
    RestClient restClient() {
        return RestClient.builder().build();
    }

    /**
     * The static factory again, for the same reason as {@link #restClient()}: no
     * {@code WebClientCustomizer} is consulted for a client built this way.
     */
    @Bean
    WebClient webClient() {
        return WebClient.builder().build();
    }

    @Bean
    StubHttpServer stubHttpServer() {
        return new StubHttpServer();
    }
}
