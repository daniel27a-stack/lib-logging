package cl.pirca.lib.logging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.headerDoesNotExist;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class CorrelationIdRestClientCustomizerTest {

    private final RestClient.Builder builder = RestClient.builder();
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

    CorrelationIdRestClientCustomizerTest() {
        new CorrelationIdRestClientCustomizer().customize(builder);
    }

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void sendsTheCorrelationIdOfTheCurrentRequest() {
        MDC.put("correlationId", "abc-123");
        server.expect(header("X-Correlation-Id", "abc-123")).andRespond(withSuccess());

        builder.build().get().uri("http://other/x").retrieve().toBodilessEntity();

        server.verify();
    }

    @Test
    void sendsNothingWhenThereIsNoCorrelationId() {
        server.expect(headerDoesNotExist("X-Correlation-Id")).andRespond(withSuccess());

        builder.build().get().uri("http://other/x").retrieve().toBodilessEntity();

        server.verify();
    }

    @Test
    void keepsAHeaderThatTheCallerAlreadySet() {
        MDC.put("correlationId", "from-mdc");
        server.expect(header("X-Correlation-Id", "explicit")).andRespond(withSuccess());

        builder.build().get().uri("http://other/x").header("X-Correlation-Id", "explicit")
                .retrieve().toBodilessEntity();

        server.verify();
        assertThat(MDC.get("correlationId")).isEqualTo("from-mdc");
    }
}
