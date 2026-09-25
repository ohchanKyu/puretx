package io.github.ohchankyu.puretx.spring.http;

import io.github.ohchankyu.puretx.PuretxEngine;
import io.github.ohchankyu.puretx.spring.InstrumentationReport;
import java.util.function.Supplier;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Attaches the filter to every {@code WebClient} bean in the context.
 *
 * <p>A {@code WebClientCustomizer} only reaches builders that came from Boot's auto-configured
 * {@code WebClient.Builder} bean. A client built with the static {@code WebClient.builder()} or
 * {@code WebClient.create()} never passes through one — the same gap {@code RestClient} had.
 * A client that already carries the filter is returned untouched rather than reported twice.
 */
public final class PuretxWebClientPostProcessor implements BeanPostProcessor {

    private final PuretxExchangeFilterFunction filter;

    private final InstrumentationReport report;

    public PuretxWebClientPostProcessor(final Supplier<PuretxEngine> engineSupplier, final InstrumentationReport report) {
        this.filter = new PuretxExchangeFilterFunction(engineSupplier);
        this.report = report;
        report.watchingHttp();
    }

    @Override
    public Object postProcessAfterInitialization(final Object bean, final String beanName) throws BeansException {
        if (!(bean instanceof WebClient webClient)) {
            return bean;
        }
        final WebClient.Builder builder = webClient.mutate();
        if (!filter.installOn(builder)) {
            return bean;
        }
        report.instrumented("WebClient");
        return builder.build();
    }
}
