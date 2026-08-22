package io.github.ohchankyu.puretx.spring.http;

import io.github.ohchankyu.puretx.PuretxEngine;
import io.github.ohchankyu.puretx.spring.InstrumentationReport;
import java.util.function.Supplier;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.web.client.RestTemplate;

/**
 * Adds the interceptor to {@code RestTemplate} beans that were built with {@code new RestTemplate()}
 * rather than through {@code RestTemplateBuilder}, which plenty of applications do.
 *
 * <p>Templates that came from the builder already have it, courtesy of the
 * {@code RestTemplateCustomizer}, so {@link PuretxClientHttpRequestInterceptor#installOn} checks
 * before adding.
 */
public final class PuretxRestTemplatePostProcessor implements BeanPostProcessor {

    private final PuretxClientHttpRequestInterceptor interceptor;

    private final InstrumentationReport report;

    public PuretxRestTemplatePostProcessor(final Supplier<PuretxEngine> engineSupplier,
            final InstrumentationReport report) {
        this.interceptor = new PuretxClientHttpRequestInterceptor(engineSupplier);
        this.report = report;
        report.watchingHttp();
    }

    @Override
    public Object postProcessAfterInitialization(final Object bean, final String beanName) throws BeansException {
        if (bean instanceof RestTemplate restTemplate) {
            interceptor.installOn(restTemplate);
            report.instrumented("RestTemplate");
        }
        return bean;
    }
}
