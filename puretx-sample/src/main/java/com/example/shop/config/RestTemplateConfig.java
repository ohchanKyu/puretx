package com.example.shop.config;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

/**
 * The application's HTTP client.
 *
 * <p>Built through {@link RestTemplateBuilder} rather than {@code new RestTemplate()}, which is
 * how puretx's interceptor gets attached — see {@code PuretxAutoConfiguration.RestTemplateDetection}.
 * A hand-constructed {@code RestTemplate} bean is picked up too, by a bean post-processor, but the
 * builder is the path everything else in Spring Boot expects as well.
 */
@Configuration(proxyBeanMethods = false)
public class RestTemplateConfig {

    @Bean
    public RestTemplate restTemplate(final RestTemplateBuilder builder) {
        return builder.build();
    }
}
