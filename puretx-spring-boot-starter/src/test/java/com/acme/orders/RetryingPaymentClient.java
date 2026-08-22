package com.acme.orders;

import java.io.IOException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * A client whose own interceptor retries, the way production clients usually do.
 *
 * <p>This is what makes interceptor order observable. If puretx sits inside the retry loop it
 * times one attempt out of four — or none, because the interceptor chain is single-use and the
 * retries bypass it entirely — while the transaction is held for the whole sequence.
 */
@Component
public class RetryingPaymentClient {

    public static final int ATTEMPTS = 4;

    public static final long BACKOFF_MILLIS = 200;

    private final RestClient restClient;

    public RetryingPaymentClient(@Qualifier("retryingRestClient") final RestClient restClient) {
        this.restClient = restClient;
    }

    public void charge(final String url) {
        try {
            restClient.get().uri(url).retrieve().toBodilessEntity();
        } catch (RuntimeException ignored) {
            // The stub answers 503 on purpose; the retries are what this exercises.
        }
    }

    /** Retries a failed response, sleeping between attempts. */
    public static final class RetryInterceptor implements ClientHttpRequestInterceptor {

        @Override
        public ClientHttpResponse intercept(final HttpRequest request, final byte[] body,
                final ClientHttpRequestExecution execution) throws IOException {
            ClientHttpResponse response = execution.execute(request, body);
            for (int attempt = 1; attempt < ATTEMPTS && response.getStatusCode().is5xxServerError(); attempt++) {
                sleep();
                response = execution.execute(request, body);
            }
            return response;
        }

        private static void sleep() {
            try {
                Thread.sleep(BACKOFF_MILLIS);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
