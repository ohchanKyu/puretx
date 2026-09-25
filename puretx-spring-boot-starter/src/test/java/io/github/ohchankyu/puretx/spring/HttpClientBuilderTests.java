package io.github.ohchankyu.puretx.spring;

import static org.assertj.core.api.Assertions.assertThat;

import com.acme.orders.PuretxIntegrationTest;
import io.github.ohchankyu.puretx.spring.http.PuretxClientHttpRequestInterceptor;
import io.github.ohchankyu.puretx.spring.http.PuretxExchangeFilterFunction;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * A client built from an injected builder inside a constructor never becomes a bean, so no
 * post-processor sees it. Boot's customizers are what reach it, and this is the pattern the
 * Spring Boot reference guide recommends.
 *
 * <p>The builder beans are looked up by name, and {@code RestTemplateBuilder} is called
 * reflectively, because that class moved packages in Spring Boot 4 and these tests run against
 * both.
 */
@PuretxIntegrationTest
class HttpClientBuilderTests {

    @Autowired
    private ApplicationContext context;

    @Test
    @DisplayName("a RestTemplate from the injected builder carries the interceptor")
    void restTemplateBuilderInstallsTheInterceptor() throws ReflectiveOperationException {
        final Object builder = context.getBean("restTemplateBuilder");
        final RestTemplate template = (RestTemplate) builder.getClass().getMethod("build").invoke(builder);

        assertThat(template.getInterceptors())
                .filteredOn(PuretxClientHttpRequestInterceptor.class::isInstance).hasSize(1);
    }

    @Test
    @DisplayName("a RestClient from the injected builder carries the interceptor")
    void restClientBuilderInstallsTheInterceptor() {
        final RestClient client = context.getBean("restClientBuilder", RestClient.Builder.class).build();

        final List<ClientHttpRequestInterceptor> interceptors = new ArrayList<>();
        client.mutate().requestInterceptors(interceptors::addAll);
        assertThat(interceptors)
                .filteredOn(PuretxClientHttpRequestInterceptor.class::isInstance).hasSize(1);
    }

    @Test
    @DisplayName("a WebClient from the injected builder carries the filter")
    void webClientBuilderInstallsTheFilter() {
        final WebClient client = context.getBean("webClientBuilder", WebClient.Builder.class).build();

        final List<ExchangeFilterFunction> filters = new ArrayList<>();
        client.mutate().filters(filters::addAll);
        assertThat(filters).filteredOn(PuretxExchangeFilterFunction.class::isInstance).hasSize(1);
    }
}
