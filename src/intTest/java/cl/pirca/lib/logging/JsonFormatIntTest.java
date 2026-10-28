package cl.pirca.lib.logging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import cl.pirca.lib.logging.testapp.TestApplication;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.server.LocalServerPort;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(classes = TestApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.profiles.active=prod")
@ExtendWith(OutputCaptureExtension.class)
class JsonFormatIntTest {

    @BeforeAll
    static void freshLoggingSystem() {
        IntTestSupport.resetLoggingSystem();
    }

    @LocalServerPort
    int port;

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> jsonLines(String output) {
        JsonMapper mapper = JsonMapper.builder().build();
        return output.lines().filter(line -> line.startsWith("{"))
                .map(line -> (Map<String, Object>) mapper.readValue(line, Map.class)).toList();
    }

    @Test
    void withTheProdProfileLogsAreJsonWithMdcAndFieldsAsSeparateKeys(CapturedOutput output) throws Exception {
        IntTestSupport.get(port, "/greet/ana", "X-Correlation-Id", "c-88", "X-Tenant-Id", "acme");

        await().untilAsserted(() -> {
            Map<String, Object> onDemand = jsonLines(output.getOut()).stream()
                    .filter(line -> "greet".equals(line.get("action"))).findFirst().orElseThrow();
            assertThat(onDemand).containsEntry("correlationId", "c-88").containsEntry("tenant", "acme")
                    .containsEntry("name", "ana").containsEntry("action", "greet");
            assertThat(String.valueOf(onDemand.get("message"))).contains("Greeting ana");
            assertThat(jsonLines(output.getOut())).anySatisfy(line -> assertThat(String.valueOf(line.get("message")))
                    .matches("GET /greet/\\{name} -> 200 in \\d+ ms"));
        });
    }
}
