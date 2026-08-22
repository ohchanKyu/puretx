package io.github.ohchankyu.puretx.spring.http;

import io.github.ohchankyu.puretx.PuretxEngine;
import io.github.ohchankyu.puretx.spring.InstrumentationReport;
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
 * <p>The interceptor goes in <em>first</em>, not appended. A client whose own interceptor retries
 * runs the rest of the chain once per attempt, so from inside that loop puretx would time a single
 * attempt while the transaction is held for the whole sequence — and since an interceptor chain is
 * single-use, the retries would bypass it altogether and report a duration near zero. Being
 * outermost is also what keeps the reported call site the application's rather than whichever
 * interceptor wrapped it.
 *
 * <p>There is deliberately no customizer alongside this: a second registration would report every
 * call twice. It could be detected — {@code mutate()} exposes the list — but one hook is enough,
 * and every {@code RestClient} that an application can call through is a bean.
 */
public final class PuretxRestClientPostProcessor implements BeanPostProcessor {

    private final PuretxClientHttpRequestInterceptor interceptor;

    private final InstrumentationReport report;

    public PuretxRestClientPostProcessor(final Supplier<PuretxEngine> engineSupplier,
            final InstrumentationReport report) {
        this.interceptor = new PuretxClientHttpRequestInterceptor(engineSupplier);
        this.report = report;
        report.watchingHttp();
    }

    @Override
    public Object postProcessAfterInitialization(final Object bean, final String beanName) throws BeansException {
        if (bean instanceof RestClient restClient) {
            report.instrumented("RestClient");
            return restClient.mutate()
                    .requestInterceptors(interceptors -> {
                        if (interceptors.stream()
                                .noneMatch(PuretxClientHttpRequestInterceptor.class::isInstance)) {
                            interceptors.add(0, interceptor);
                        }
                    })
                    .build();
        }
        return bean;
    }
}
