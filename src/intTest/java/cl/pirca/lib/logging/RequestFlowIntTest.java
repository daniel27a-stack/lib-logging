package cl.pirca.lib.logging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import cl.pirca.lib.logging.testapp.TestApplication;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.server.LocalServerPort;

@SpringBootTest(classes = TestApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "logging.level.cl.pirca.lib.logging.testapp=DEBUG")
@ExtendWith(OutputCaptureExtension.class)
class RequestFlowIntTest {

    private static final String REQUEST_LINE = "RequestLoggingFilter";

    @BeforeAll
    static void freshLoggingSystem() {
        IntTestSupport.resetLoggingSystem();
    }

    @LocalServerPort
    int port;

    private static long count(String output, String text) {
        return output.lines().filter(line -> line.contains(text)).count();
    }

    @Test
    void aSuccessfulRequestWritesOneInfoLineAndTheMethodLogsAtDebug(CapturedOutput output) throws Exception {
        IntTestSupport.get(port, "/greet/ben");

        await().untilAsserted(() -> {
            String out = output.getOut();
            assertThat(count(out, "GET /greet/{name} -> 200")).isEqualTo(1);
            assertThat(out.lines().filter(l -> l.contains("GET /greet/{name} -> 200")).findFirst().orElseThrow())
                    .contains(" INFO ");
            assertThat(out).containsPattern("DEBUG .*GreetingController\\s+: -> greet");
            assertThat(out).containsPattern("DEBUG .*GreetingService\\s+: <- greet in \\d+ ms");
        });
    }

    @Test
    void aFourHundredIsOneWarnWithoutTrace(CapturedOutput output) throws Exception {
        IntTestSupport.get(port, "/fail/400");

        await().untilAsserted(() -> {
            String out = output.getOut();
            assertThat(out.lines().filter(l -> l.contains(REQUEST_LINE) && l.contains("/fail/{status} -> 400")).toList())
                    .singleElement().satisfies(line -> assertThat(line).contains(" WARN "));
            assertThat(count(out, "at cl.pirca.lib.logging.testapp.GreetingController.fail(")).isZero();
        });
    }

    @Test
    void aFiveHundredIsOneErrorWithTheTraceOnce(CapturedOutput output) throws Exception {
        IntTestSupport.get(port, "/fail/500");

        await().untilAsserted(() -> {
            String out = output.getOut();
            assertThat(out.lines().filter(l -> l.contains(REQUEST_LINE) && l.contains("/fail/{status} -> 500")).toList())
                    .singleElement().satisfies(line -> assertThat(line).contains("ERROR"));
            assertThat(count(out, "cl.pirca.lib.logging.testapp.TestFailure: failure 500")).isEqualTo(1);
            assertThat(count(out, "at cl.pirca.lib.logging.testapp.GreetingController.fail(")).isEqualTo(1);
        });
    }

    @Test
    void anUnhandledExceptionIsLoggedAsErrorWithTheTraceOnce(CapturedOutput output) throws Exception {
        IntTestSupport.get(port, "/unhandled");

        await().untilAsserted(() -> {
            String out = output.getOut();
            assertThat(count(out, "java.lang.IllegalStateException: unhandled boom")).isEqualTo(1);
        });
    }
}
