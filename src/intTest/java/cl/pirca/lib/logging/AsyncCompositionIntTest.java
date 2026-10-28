package cl.pirca.lib.logging;

import static org.assertj.core.api.Assertions.assertThat;

import cl.pirca.lib.tenant.TenantAutoConfiguration;
import cl.pirca.lib.tenant.TenantContext;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.task.TaskExecutionAutoConfiguration;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.core.task.TaskDecorator;

/** The decorator of lib-logging composes with the one of lib-tenant, with the application's own, and works alone. */
class AsyncCompositionIntTest {

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    private static String seenByATask(AsyncTaskExecutor executor, boolean withTenant) throws Exception {
        AtomicReference<String> seen = new AtomicReference<>();
        MDC.put("correlationId", "c-1");
        Runnable submit = () -> {
            try {
                executor.submit(() -> seen.set(MDC.get("correlationId") + "|"
                        + (withTenant ? TenantContext.current().orElse("-") : "n/a"))).get();
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        };
        if (withTenant) {
            TenantContext.runAs("acme", submit);
        } else {
            submit.run();
        }
        return seen.get();
    }

    @Test
    void worksWithTheTenantDecoratorFromLibTenant() {
        new WebApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(TaskExecutionAutoConfiguration.class,
                        LoggingAutoConfiguration.class, TenantAutoConfiguration.class))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(seenByATask(context.getBean("applicationTaskExecutor", AsyncTaskExecutor.class), true))
                            .isEqualTo("c-1|acme");
                });
    }

    @Test
    void worksWhenLibTenantIsAbsentWithOnlyTheCorrelationId() {
        new WebApplicationContextRunner()
                .withClassLoader(new FilteredClassLoader("cl.pirca.lib.tenant"))
                .withConfiguration(AutoConfigurations.of(TaskExecutionAutoConfiguration.class,
                        LoggingAutoConfiguration.class))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(seenByATask(context.getBean("applicationTaskExecutor", AsyncTaskExecutor.class), false))
                            .isEqualTo("c-1|n/a");
                });
    }

    @Test
    void appliesTheApplicationsOwnDecoratorToo() {
        new WebApplicationContextRunner()
                .withClassLoader(new FilteredClassLoader("cl.pirca.lib.tenant"))
                .withConfiguration(AutoConfigurations.of(TaskExecutionAutoConfiguration.class,
                        LoggingAutoConfiguration.class))
                .withUserConfiguration(OwnDecorator.class)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    AtomicReference<String> seen = new AtomicReference<>();
                    MDC.put("correlationId", "c-2");
                    context.getBean("applicationTaskExecutor", AsyncTaskExecutor.class)
                            .submit(() -> seen.set(MDC.get("correlationId") + "|" + OwnDecorator.MARK.get())).get();
                    assertThat(seen).hasValue("c-2|own");
                });
    }

    @Configuration(proxyBeanMethods = false)
    static class OwnDecorator {
        static final ThreadLocal<String> MARK = new ThreadLocal<>();

        @Bean
        TaskDecorator ownTaskDecorator() {
            return task -> () -> {
                MARK.set("own");
                try {
                    task.run();
                } finally {
                    MARK.remove();
                }
            };
        }
    }
}
