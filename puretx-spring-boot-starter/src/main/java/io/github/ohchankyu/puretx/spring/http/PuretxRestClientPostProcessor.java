package io.github.ohchankyu.puretx.spring.http;

import io.github.ohchankyu.puretx.PuretxEngine;
import java.util.function.Supplier;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.web.client.RestClient;

/**
 * Attaches the interceptor to every {@code RestClient} bean in the context.
 *
 * <p>A {@code RestClientCustomizer} would have been the obvious hook, but it only reaches builders
 * that came from Boot's auto-configured {@code RestClient.Builder} bean. The idiomatic way to build
 * one is the static {@code RestClient.builder()}, which no customizer ever sees — an application
 * doing that got a puretx that reported nothing and never said why.
 *
 * <p>{@code mutate()} carries over the request factory, the base URL and any interceptors already
 * configured, and the bean stays a {@code RestClient}, so nothing downstream notices.
 *
 * <p>There is deliberately no customizer alongside this. Unlike {@code RestTemplate}, a built
 * {@code RestClient} does not expose its interceptors, so a second registration could not be
 * detected and would report every call twice.
 */
public final class PuretxRestClientPostProcessor implements BeanPostProcessor {

    private final PuretxClientHttpRequestInterceptor interceptor;

    public PuretxRestClientPostProcessor(final Supplier<PuretxEngine> engineSupplier) {
        this.interceptor = new PuretxClientHttpRequestInterceptor(engineSupplier);
    }

    @Override
    public Object postProcessAfterInitialization(final Object bean, final String beanName) throws BeansException {
        if (bean instanceof RestClient restClient) {
            return restClient.mutate().requestInterceptor(interceptor).build();
        }
        return bean;
    }
}
