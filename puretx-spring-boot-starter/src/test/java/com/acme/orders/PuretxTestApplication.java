package com.acme.orders;

import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Primary;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Minimal application: an embedded H2 datasource, a transaction manager, and one of each HTTP
 * client, every one built without Boot's builder beans so that the bean post-processors are
 * what instruments them. The builder path is covered by {@code HttpClientBuilderTests}.
 *
 * <p>Nothing in here names a Spring Boot class that moved in Boot 4, because the same sources
 * are run against every supported Boot version.
 */
@SpringBootConfiguration
@EnableAutoConfiguration
@ComponentScan
public class PuretxTestApplication {

    @Bean
    RestTemplate restTemplate() {
        return new RestTemplate();
    }

    @Bean
    @Primary
    RestClient restClient() {
        return RestClient.builder().build();
    }

    /** A client that retries internally, the way production clients usually do. */
    @Bean
    RestClient retryingRestClient() {
        return RestClient.builder()
                .requestInterceptor(new RetryingPaymentClient.RetryInterceptor())
                .build();
    }

    @Bean
    WebClient webClient() {
        return WebClient.builder().build();
    }

    @Bean
    StubHttpServer stubHttpServer() {
        return new StubHttpServer();
    }
}
