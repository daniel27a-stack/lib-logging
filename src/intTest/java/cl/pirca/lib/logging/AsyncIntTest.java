package cl.pirca.lib.logging;

import static org.assertj.core.api.Assertions.assertThat;

import cl.pirca.lib.logging.testapp.GreetingService;
import cl.pirca.lib.logging.testapp.TestApplication;
import cl.pirca.lib.tenant.TenantContext;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.task.AsyncTaskExecutor;

@SpringBootTest(classes = TestApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AsyncIntTest {

    @LocalServerPort
    int port;

    @Autowired
    GreetingService service;

    @Autowired
    @Qualifier("applicationTaskExecutor")
    AsyncTaskExecutor executor;

    @Test
    void anAsyncMethodSeesTheCorrelationIdAndTheTenantOfTheRequest() throws Exception {
        assertThat(IntTestSupport.get(port, "/async", "X-Correlation-Id", "c-5", "X-Tenant-Id", "acme").body())
                .isEqualTo("c-5|acme");
    }

    @Test
    void theApplicationTaskExecutorSeesBothValues() throws Exception {
        AtomicReference<String> seen = new AtomicReference<>();
        MDC.put("correlationId", "c-6");
        try {
            TenantContext.runAs("globex", () -> submit(executor, seen));
        } finally {
            MDC.clear();
        }

        assertThat(seen).hasValue("c-6|globex");
    }

    private static void submit(Executor executor, AtomicReference<String> seen) {
        try {
            ((AsyncTaskExecutor) executor).submit(
                    () -> seen.set(MDC.get("correlationId") + "|" + TenantContext.current().orElse("-"))).get();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
