package cl.pirca.lib.logging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import cl.pirca.lib.logging.testapp.TestApplication;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

/** With a threshold below the work, an async request is slow, so its one line is a WARN. */
@SpringBootTest(classes = TestApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "pirca.logging.slow-request-threshold=200ms")
class SlowAsyncRequestLineIntTest {

    @BeforeAll
    static void freshLoggingSystem() {
        IntTestSupport.resetLoggingSystem();
    }

    @LocalServerPort
    int port;

    private RequestLines lines;

    @BeforeEach
    void captureLines() {
        lines = new RequestLines();
    }

    @AfterEach
    void releaseLines() {
        lines.close();
    }

    @ParameterizedTest
    @ValueSource(strings = {"callable", "future", "deferred"})
    void aSlowAsyncRequestIsOneWarnLineWithoutTrace(String type) throws Exception {
        String id = UUID.randomUUID().toString();

        IntTestSupport.get(port, "/async-line/" + type + "/ok", "X-Correlation-Id", id);

        await().atMost(Duration.ofSeconds(5)).until(() -> !lines.of(id).isEmpty());
        Thread.sleep(300);
        assertThat(lines.of(id)).hasSize(1);
        ILoggingEvent line = lines.of(id).getFirst();
        assertThat(line.getLevel()).isEqualTo(Level.WARN);
        assertThat(RequestLines.status(line)).isEqualTo(200);
        assertThat(line.getThrowableProxy()).isNull();
    }
}
