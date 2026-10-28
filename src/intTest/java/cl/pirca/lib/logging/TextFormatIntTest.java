package cl.pirca.lib.logging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import cl.pirca.lib.logging.testapp.TestApplication;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.server.LocalServerPort;

@SpringBootTest(classes = TestApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "pirca.test=text")
@ExtendWith(OutputCaptureExtension.class)
class TextFormatIntTest {

    @BeforeAll
    static void freshLoggingSystem() {
        IntTestSupport.resetLoggingSystem();
    }

    @LocalServerPort
    int port;

    @Test
    void printsReadableTextWithServiceNameLoggerCorrelationIdAndTenant(CapturedOutput output) throws Exception {
        HttpResponse<String> response = IntTestSupport.get(port, "/greet/ana",
                "X-Correlation-Id", "c-77", "X-Tenant-Id", "acme");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("X-Correlation-Id")).contains("c-77");
        await().untilAsserted(() -> {
            assertThat(output.getOut()).containsPattern(
                    "INFO --- \\[test-app] \\[.*] \\[c-77,acme] c\\.p\\.lib\\.logging\\.RequestLoggingFilter\\s+: "
                            + "GET /greet/\\{name} -> 200 in \\d+ ms");
            assertThat(output.getOut()).containsPattern(
                    "\\[c-77,acme] c\\.p\\.lib\\.logging\\.testapp\\.GreetingService\\s+: "
                            + "Greeting ana action=greet name=ana");
        });
    }
}
