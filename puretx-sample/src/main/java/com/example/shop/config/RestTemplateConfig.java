package com.example.shop.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

/**
 * The application's HTTP client.
 *
 * <p>Plain {@code new RestTemplate()} on purpose: puretx attaches its interceptor to any
 * {@code RestTemplate} bean through a bean post-processor, and this sample runs against every
 * Spring Boot version puretx supports, including Boot 4, where {@code RestTemplateBuilder} moved
 * to a starter of its own. A template built from the builder is instrumented just the same.
 */
@Configuration(proxyBeanMethods = false)
public class RestTemplateConfig {

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}
