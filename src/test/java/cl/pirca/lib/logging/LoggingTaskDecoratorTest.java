package cl.pirca.lib.logging;

import static org.assertj.core.api.Assertions.assertThat;

import cl.pirca.lib.tenant.TenantContext;
import cl.pirca.lib.tenant.TenantTaskDecorator;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.core.task.TaskDecorator;

class LoggingTaskDecoratorTest {

    private final ExecutorService worker = Executors.newSingleThreadExecutor();

    @AfterEach
    void cleanUp() {
        worker.shutdownNow();
        MDC.clear();
    }

    private LoggingTaskDecorator decoratorWith(TaskDecorator... others) {
        DefaultListableBeanFactory factory = new DefaultListableBeanFactory();
        List<TaskDecorator> all = new ArrayList<>(List.of(others));
        ObjectProvider<TaskDecorator> provider = factory.getBeanProvider(TaskDecorator.class);
        LoggingTaskDecorator decorator = new LoggingTaskDecorator(provider);
        factory.registerSingleton("logging", decorator);
        for (int i = 0; i < all.size(); i++) {
            factory.registerSingleton("other" + i, all.get(i));
        }
        return decorator;
    }

    private void runOnWorker(Runnable decorated) throws Exception {
        worker.submit(decorated).get();
    }

    @Test
    void theWorkerSeesTheMdcOfTheSubmittingThread() throws Exception {
        MDC.put("correlationId", "c-1");
        MDC.put("tenant", "acme");
        AtomicReference<String> correlation = new AtomicReference<>();
        AtomicReference<String> tenant = new AtomicReference<>();

        Runnable decorated = decoratorWith().decorate(() -> {
            correlation.set(MDC.get("correlationId"));
            tenant.set(MDC.get("tenant"));
        });
        MDC.clear();
        runOnWorker(decorated);

        assertThat(correlation).hasValue("c-1");
        assertThat(tenant).hasValue("acme");
    }

    @Test
    void restoresThePreviousMdcOfTheWorkerAndClearsWhenThereWasNone() throws Exception {
        AtomicReference<Boolean> leaked = new AtomicReference<>();
        MDC.put("correlationId", "c-1");
        Runnable decorated = decoratorWith().decorate(() -> { });
        worker.submit(() -> MDC.put("own", "value")).get();

        worker.submit(decorated).get();
        worker.submit(() -> leaked.set(MDC.get("correlationId") != null)).get();

        assertThat(leaked).hasValue(false);
        AtomicReference<String> own = new AtomicReference<>();
        worker.submit(() -> own.set(MDC.get("own"))).get();
        assertThat(own).hasValue("value");
    }

    @Test
    void runsWithoutMdcWhenTheSubmitterHasNone() throws Exception {
        AtomicReference<String> seen = new AtomicReference<>("untouched");

        runOnWorker(decoratorWith().decorate(() -> seen.set(MDC.get("correlationId"))));

        assertThat(seen.get()).isNull();
    }

    @Test
    void composesWithTheTenantDecorator() throws Exception {
        AtomicReference<String> correlation = new AtomicReference<>();
        AtomicReference<String> tenant = new AtomicReference<>();
        MDC.put("correlationId", "c-9");

        Runnable[] decorated = new Runnable[1];
        TenantContext.runAs("acme", () -> decorated[0] = decoratorWith(new TenantTaskDecorator()).decorate(() -> {
            correlation.set(MDC.get("correlationId"));
            tenant.set(TenantContext.current().orElse(null));
        }));
        runOnWorker(decorated[0]);

        assertThat(correlation).hasValue("c-9");
        assertThat(tenant).hasValue("acme");
    }
}
