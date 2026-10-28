package cl.pirca.lib.logging;

import org.slf4j.MDC;
import org.springframework.boot.restclient.RestClientCustomizer;
import org.springframework.web.client.RestClient;

/**
 * Sends the correlation id of the current request as {@code X-Correlation-Id} on every outgoing call
 * made with an injected {@code RestClient.Builder}.
 */
public class CorrelationIdRestClientCustomizer implements RestClientCustomizer {

    @Override
    public void customize(RestClient.Builder restClientBuilder) {
        restClientBuilder.requestInterceptor((request, body, execution) -> {
            String correlationId = MDC.get(RequestLoggingFilter.CORRELATION_ID_KEY);
            if (correlationId != null && !request.getHeaders().containsHeader(RequestLoggingFilter.CORRELATION_ID_HEADER)) {
                request.getHeaders().set(RequestLoggingFilter.CORRELATION_ID_HEADER, correlationId);
            }
            return execution.execute(request, body);
        });
    }
}
