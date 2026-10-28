package cl.pirca.lib.logging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import cl.pirca.lib.tenant.TenantFilter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import java.io.IOException;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.servlet.HandlerMapping;

class RequestLoggingFilterTest {

    private static final FilterChain OK = (req, res) -> { };

    private final MockHttpServletRequest request = new MockHttpServletRequest("GET", "/debts/7");
    private final MockHttpServletResponse response = new MockHttpServletResponse();
    private CapturedLogs logs;

    @BeforeEach
    void captureLogs() {
        logs = new CapturedLogs(RequestLoggingFilter.class, Level.INFO);
    }

    @AfterEach
    void releaseLogs() {
        logs.close();
        MDC.clear();
    }

    private static RequestLoggingFilter filter(Optional<String> tenant, Duration threshold) {
        return new RequestLoggingFilter(() -> tenant, new LoggingProperties(threshold));
    }

    private static RequestLoggingFilter filter() {
        return filter(Optional.empty(), Duration.ofSeconds(2));
    }

    private static FilterChain status(int status) {
        return (req, res) -> ((MockHttpServletResponse) res).setStatus(status);
    }

    @Test
    void keepsTheCorrelationIdOfTheRequestAndReturnsIt() throws Exception {
        request.addHeader("X-Correlation-Id", "abc-123");
        AtomicReference<String> seen = new AtomicReference<>();

        filter().doFilter(request, response, (req, res) -> seen.set(MDC.get("correlationId")));

        assertThat(seen).hasValue("abc-123");
        assertThat(response.getHeader("X-Correlation-Id")).isEqualTo("abc-123");
        assertThat(logs.only().getMDCPropertyMap()).containsEntry("correlationId", "abc-123");
    }

    @Test
    void generatesAUuidWhenTheRequestHasNone() throws Exception {
        filter().doFilter(request, response, OK);

        assertThat(response.getHeader("X-Correlation-Id")).matches("[0-9a-f-]{36}");
    }

    @Test
    void replacesAnInvalidCorrelationId() throws Exception {
        request.addHeader("X-Correlation-Id", "bad value\nforged line");

        filter().doFilter(request, response, OK);

        assertThat(response.getHeader("X-Correlation-Id")).matches("[0-9a-f-]{36}");
    }

    @Test
    void putsTheTenantInTheMdcWhenThereIsOne() throws Exception {
        AtomicReference<String> seen = new AtomicReference<>();

        filter(Optional.of("acme"), Duration.ofSeconds(2))
                .doFilter(request, response, (req, res) -> seen.set(MDC.get("tenant")));

        assertThat(seen).hasValue("acme");
        assertThat(logs.only().getMDCPropertyMap()).containsEntry("tenant", "acme");
    }

    @Test
    void hasNoTenantKeyWhenThereIsNone() throws Exception {
        filter().doFilter(request, response, OK);

        assertThat(logs.only().getMDCPropertyMap()).doesNotContainKey("tenant");
    }

    @Test
    void clearsTheMdcAtTheEndOfEachRequest() throws Exception {
        filter(Optional.of("acme"), Duration.ofSeconds(2)).doFilter(request, response, OK);
        assertThat(MDC.get("correlationId")).isNull();
        assertThat(MDC.get("tenant")).isNull();

        AtomicReference<String> second = new AtomicReference<>();
        filter().doFilter(new MockHttpServletRequest("GET", "/x"), new MockHttpServletResponse(),
                (req, res) -> second.set(MDC.get("tenant")));

        assertThat(second.get()).isNull();
    }

    @Test
    void runsAfterTheTenantFilter() {
        assertThat(filter().getOrder()).isGreaterThan(TenantFilter.ORDER);
    }

    @Test
    void logsInfoWithMethodRouteStatusAndDuration() throws Exception {
        request.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, "/debts/{id}");

        filter().doFilter(request, response, status(200));

        ILoggingEvent event = logs.only();
        assertThat(event.getLevel()).isEqualTo(Level.INFO);
        assertThat(event.getFormattedMessage()).matches("GET /debts/\\{id} -> 200 in \\d+ ms");
    }

    @Test
    void usesTheUriWhenThereIsNoMatchedRoute() throws Exception {
        filter().doFilter(request, response, status(200));

        assertThat(logs.only().getFormattedMessage()).startsWith("GET /debts/7 -> 200");
    }

    @Test
    void logsA4xxAsWarnWithoutTrace() throws Exception {
        request.setAttribute(RequestLoggingFilter.ERROR_ATTRIBUTE, new IllegalStateException("bad input"));

        filter().doFilter(request, response, status(404));

        ILoggingEvent event = logs.only();
        assertThat(event.getLevel()).isEqualTo(Level.WARN);
        assertThat(event.getThrowableProxy()).isNull();
    }

    @Test
    void logsASlowRequestAsWarn() throws Exception {
        filter(Optional.empty(), Duration.ZERO).doFilter(request, response, (req, res) -> {
            try {
                Thread.sleep(2);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        assertThat(logs.only().getLevel()).isEqualTo(Level.WARN);
    }

    @Test
    void logsA5xxAsErrorWithTheTraceOnce() throws Exception {
        request.setAttribute(RequestLoggingFilter.ERROR_ATTRIBUTE, new IllegalStateException("boom"));

        filter().doFilter(request, response, status(500));

        ILoggingEvent event = logs.only();
        assertThat(event.getLevel()).isEqualTo(Level.ERROR);
        assertThat(event.getThrowableProxy().getMessage()).isEqualTo("boom");
    }

    @Test
    void logsA5xxWithoutExceptionAsErrorWithoutTrace() throws Exception {
        filter().doFilter(request, response, status(503));

        ILoggingEvent event = logs.only();
        assertThat(event.getLevel()).isEqualTo(Level.ERROR);
        assertThat(event.getThrowableProxy()).isNull();
    }

    @Test
    void logsAThrownExceptionAsErrorWithTheTraceOnceAndAnswersWith500() throws Exception {
        IllegalStateException cause = new IllegalStateException("boom");
        FilterChain throwing = (req, res) -> {
            throw new ServletException("wrapped", cause);
        };

        filter().doFilter(request, response, throwing);

        ILoggingEvent event = logs.only();
        assertThat(event.getLevel()).isEqualTo(Level.ERROR);
        assertThat(event.getThrowableProxy().getMessage()).isEqualTo("boom");
        assertThat(response.getStatus()).isEqualTo(500);
        assertThat(MDC.get("correlationId")).isNull();
    }

    @Test
    void logsAThrownRuntimeExceptionAsIs() throws Exception {
        FilterChain throwing = (req, res) -> {
            throw new IllegalArgumentException("direct");
        };

        filter().doFilter(request, response, throwing);

        assertThat(logs.only().getThrowableProxy().getMessage()).isEqualTo("direct");
    }

    @Test
    void rethrowsWhenTheResponseIsAlreadyCommitted() {
        response.setCommitted(true);

        assertThatThrownBy(() -> filter().doFilter(request, response, (req, res) -> {
            throw new IllegalArgumentException("late");
        })).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> filter().doFilter(request, response, (req, res) -> {
            throw new ServletException("late");
        })).isInstanceOf(ServletException.class);
        assertThatThrownBy(() -> filter().doFilter(request, response, (req, res) -> {
            throw new IOException("late");
        })).isInstanceOf(IOException.class);

        assertThat(logs.events()).hasSize(3);
    }
}
