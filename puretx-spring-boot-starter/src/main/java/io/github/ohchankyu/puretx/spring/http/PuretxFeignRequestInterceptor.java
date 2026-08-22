package io.github.ohchankyu.puretx.spring.http;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import io.github.ohchankyu.puretx.PuretxEngine;
import io.github.ohchankyu.puretx.ViolationType;
import io.github.ohchankyu.puretx.spring.InstrumentationReport;

/**
 * Catches Feign calls made inside a transaction.
 *
 * <p>Feign only offers a hook before the request goes out, so the reported violation carries no
 * duration. The transaction it interrupted is still named, which is the part that matters.
 */
public final class PuretxFeignRequestInterceptor implements RequestInterceptor {

    private final PuretxEngine engine;

    public PuretxFeignRequestInterceptor(final PuretxEngine engine, final InstrumentationReport report) {
        this.engine = engine;
        // Feign has no bean to post-process: registering this interceptor is the instrumentation.
        report.watchingHttp();
        report.instrumented("Feign");
    }

    @Override
    public void apply(final RequestTemplate template) {
        engine.report(ViolationType.HTTP_CALL, () -> summarize(template));
    }

    private static String summarize(final RequestTemplate template) {
        final String base = template.feignTarget() == null ? "" : template.feignTarget().url();
        return "HTTP " + template.method() + " " + base + template.path();
    }
}
