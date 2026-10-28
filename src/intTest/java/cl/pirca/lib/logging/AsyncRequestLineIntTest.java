package cl.pirca.lib.logging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import cl.pirca.lib.logging.testapp.AsyncController;
import cl.pirca.lib.logging.testapp.MdcProbe;
import cl.pirca.lib.logging.testapp.TestApplication;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

/** The request line is written once, when the request finishes, for synchronous and asynchronous controllers. */
@SpringBootTest(classes = TestApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AsyncRequestLineIntTest {

    @BeforeAll
    static void freshLoggingSystem() {
        IntTestSupport.resetLoggingSystem();
    }

    @LocalServerPort
    int port;

    @Autowired
    MdcProbe probe;

    private RequestLines lines;

    @BeforeEach
    void captureLines() {
        lines = new RequestLines();
    }

    @AfterEach
    void releaseLines() {
        lines.close();
    }

    private ILoggingEvent onlyLine(String id) throws Exception {
        await().atMost(Duration.ofSeconds(5)).until(() -> !lines.of(id).isEmpty());
        Thread.sleep(300);
        assertThat(lines.of(id)).hasSize(1);
        return lines.of(id).getFirst();
    }

    private static String newId() {
        return UUID.randomUUID().toString();
    }

    @Test
    void aSynchronousControllerWritesOneLine() throws Exception {
        String id = newId();

        HttpResponse<String> response = IntTestSupport.get(port, "/async-line/sync",
                "X-Correlation-Id", id, "X-Tenant-Id", "acme");

        ILoggingEvent line = onlyLine(id);
        assertThat(line.getLevel()).isEqualTo(Level.INFO);
        assertThat(RequestLines.status(line)).isEqualTo(200);
        assertThat(line.getMDCPropertyMap()).containsEntry("correlationId", id).containsEntry("tenant", "acme");
        assertThat(response.headers().firstValue("X-Correlation-Id")).hasValue(id);
        assertThat(probe.entries()).contains("pre-handle|" + id + "|acme", "post-handle|" + id + "|acme");
    }

    @ParameterizedTest
    @ValueSource(strings = {"callable", "future", "deferred"})
    void aWorkThatEndsWellWritesOneInfoLineAfterTheDelay(String type) throws Exception {
        String id = newId();

        HttpResponse<String> response = IntTestSupport.get(port, "/async-line/" + type + "/ok",
                "X-Correlation-Id", id, "X-Tenant-Id", "acme");

        ILoggingEvent line = onlyLine(id);
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(line.getLevel()).isEqualTo(Level.INFO);
        assertThat(RequestLines.status(line)).isEqualTo(200);
        assertThat(RequestLines.millis(line)).isGreaterThanOrEqualTo(AsyncController.DELAY_MS);
        assertThat(line.getMDCPropertyMap()).containsEntry("correlationId", id).containsEntry("tenant", "acme");
        assertThat(response.headers().firstValue("X-Correlation-Id")).hasValue(id);
        assertThat(probe.entries()).contains("pre-handle|" + id + "|acme", "post-handle|" + id + "|acme");
    }

    @ParameterizedTest
    @ValueSource(strings = {"callable", "future", "deferred"})
    void aWorkThatFailsWritesOneErrorLineWithA500AndTheTraceOnce(String type) throws Exception {
        String id = newId();

        HttpResponse<String> response = IntTestSupport.get(port, "/async-line/" + type + "/fail",
                "X-Correlation-Id", id, "X-Tenant-Id", "acme");

        ILoggingEvent line = onlyLine(id);
        assertThat(response.statusCode()).isEqualTo(500);
        assertThat(line.getLevel()).isEqualTo(Level.ERROR);
        assertThat(RequestLines.status(line)).isEqualTo(500);
        assertThat(RequestLines.millis(line)).isGreaterThanOrEqualTo(AsyncController.DELAY_MS);
        assertThat(line.getThrowableProxy()).isNotNull();
        assertThat(line.getThrowableProxy().getMessage()).isEqualTo("async boom");
        assertThat(line.getMDCPropertyMap()).containsEntry("correlationId", id).containsEntry("tenant", "acme");
        assertThat(response.headers().firstValue("X-Correlation-Id")).hasValue(id);
    }

    @ParameterizedTest
    @ValueSource(strings = {"callable", "future", "deferred"})
    void aFourHundredAfterTheDelayWritesOneWarnLineWithoutTrace(String type) throws Exception {
        String id = newId();

        HttpResponse<String> response = IntTestSupport.get(port, "/async-line/" + type + "/notfound",
                "X-Correlation-Id", id);

        ILoggingEvent line = onlyLine(id);
        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(line.getLevel()).isEqualTo(Level.WARN);
        assertThat(RequestLines.status(line)).isEqualTo(404);
        assertThat(RequestLines.millis(line)).isGreaterThanOrEqualTo(AsyncController.DELAY_MS);
        assertThat(line.getThrowableProxy()).isNull();
        assertThat(line.getMDCPropertyMap()).doesNotContainKey("tenant");
    }

    @ParameterizedTest
    @ValueSource(strings = {"callable", "future", "deferred"})
    void aGeneratedCorrelationIdIsTheSameInTheHeaderAndInTheLine(String type) throws Exception {
        HttpResponse<String> response = IntTestSupport.get(port, "/async-line/" + type + "/ok");

        String id = response.headers().firstValue("X-Correlation-Id").orElseThrow();
        assertThat(id).matches("[0-9a-f-]{36}");
        assertThat(onlyLine(id).getMDCPropertyMap()).containsEntry("correlationId", id);
    }

    @Test
    void theContainerThreadKeepsNoMdcAfterwards() throws Exception {
        String id = newId();
        IntTestSupport.get(port, "/async-line/callable/ok", "X-Correlation-Id", id, "X-Tenant-Id", "acme");
        onlyLine(id);

        assertThat(List.of(MDC.get("correlationId") == null, MDC.get("tenant") == null)).containsOnly(true);
    }
}
