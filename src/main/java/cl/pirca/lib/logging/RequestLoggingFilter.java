package cl.pirca.lib.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.slf4j.event.Level;
import org.springframework.core.Ordered;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerMapping;

/**
 * Sets the correlation id and the tenant in the MDC for the whole request, returns the correlation
 * id in the response and writes one log line per request when it finishes.
 */
public class RequestLoggingFilter extends OncePerRequestFilter implements Ordered {

    /** Order of the filter: later than {@code TenantFilter.ORDER} (HIGHEST_PRECEDENCE + 10), so the tenant is set. */
    public static final int ORDER = Ordered.HIGHEST_PRECEDENCE + 20;

    /** Name of the HTTP header that carries the correlation id between services. */
    public static final String CORRELATION_ID_HEADER = "X-Correlation-Id";

    /** MDC key of the correlation id. */
    public static final String CORRELATION_ID_KEY = "correlationId";

    /** MDC key of the tenant. */
    public static final String TENANT_KEY = "tenant";

    /**
     * Name of the request attribute where an exception handler leaves the exception it handled, so
     * this filter logs it once. It is a contract with lib-errors, which this library does not depend on.
     */
    public static final String ERROR_ATTRIBUTE = "cl.pirca.lib.errors.EXCEPTION";

    private static final Pattern VALID_CORRELATION_ID = Pattern.compile("[A-Za-z0-9._-]{1,128}");
    private static final Logger LOG = LoggerFactory.getLogger(RequestLoggingFilter.class);

    private final TenantResolver tenantResolver;
    private final Duration slowThreshold;

    /**
     * Creates the filter.
     *
     * @param tenantResolver where the tenant of the request comes from
     * @param properties     the library configuration
     */
    public RequestLoggingFilter(TenantResolver tenantResolver, LoggingProperties properties) {
        this.tenantResolver = tenantResolver;
        this.slowThreshold = properties.slowRequestThreshold();
    }

    @Override
    public int getOrder() {
        return ORDER;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long start = System.nanoTime();
        String correlationId = correlationIdOf(request);
        MDC.put(CORRELATION_ID_KEY, correlationId);
        tenantResolver.current().ifPresent(tenant -> MDC.put(TENANT_KEY, tenant));
        response.setHeader(CORRELATION_ID_HEADER, correlationId);
        try {
            chain.doFilter(request, response);
            logRequest(request, response.getStatus(), elapsed(start), (Throwable) request.getAttribute(ERROR_ATTRIBUTE));
        } catch (ServletException | IOException | RuntimeException e) {
            logRequest(request, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, elapsed(start), unwrap(e));
            answerWithAnError(response, e);
        } finally {
            MDC.remove(CORRELATION_ID_KEY);
            MDC.remove(TENANT_KEY);
        }
    }

    private static String correlationIdOf(HttpServletRequest request) {
        String header = request.getHeader(CORRELATION_ID_HEADER);
        if (header != null && VALID_CORRELATION_ID.matcher(header).matches()) {
            return header;
        }
        return UUID.randomUUID().toString();
    }

    private static Duration elapsed(long startNanos) {
        return Duration.ofNanos(System.nanoTime() - startNanos);
    }

    /**
     * The exception has been logged with its trace. Answering with a 500 here, instead of letting it
     * reach the servlet container, keeps the container from logging the same trace a second time.
     * When the response is already committed there is nothing to answer, so the exception goes on.
     */
    private static void answerWithAnError(HttpServletResponse response, Exception e)
            throws ServletException, IOException {
        if (response.isCommitted()) {
            if (e instanceof ServletException servletException) {
                throw servletException;
            }
            if (e instanceof IOException ioException) {
                throw ioException;
            }
            throw (RuntimeException) e;
        }
        response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
    }

    private static Throwable unwrap(Exception e) {
        return e instanceof ServletException && e.getCause() != null ? e.getCause() : e;
    }

    private void logRequest(HttpServletRequest request, int status, Duration duration, Throwable error) {
        Level level = levelOf(status, duration);
        String message = request.getMethod() + " " + routeOf(request) + " -> " + status + " in "
                + duration.toMillis() + " ms";
        if (level == Level.ERROR && error != null) {
            LOG.atError().setCause(error).log(message);
        } else {
            LOG.atLevel(level).log(message);
        }
    }

    private Level levelOf(int status, Duration duration) {
        if (status >= 500) {
            return Level.ERROR;
        }
        if (status >= 400 || duration.compareTo(slowThreshold) > 0) {
            return Level.WARN;
        }
        return Level.INFO;
    }

    private static String routeOf(HttpServletRequest request) {
        Object pattern = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        return pattern != null ? pattern.toString() : request.getRequestURI();
    }
}
