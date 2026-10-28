package cl.pirca.lib.logging;

import static org.assertj.core.api.Assertions.assertThat;

import cl.pirca.lib.tenant.TenantContext;
import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskDecorator;

class LoggingAutoConfigurationTest {

    private final WebApplicationContextRunner runner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(LoggingAutoConfiguration.class));

    @Test
    void registersEveryPieceInAServletApplication() {
        runner.run(context -> {
            assertThat(context).hasSingleBean(RequestLoggingFilter.class);
            assertThat(context).hasSingleBean(TenantResolver.class);
            assertThat(context).hasSingleBean(CorrelationIdRestClientCustomizer.class);
            assertThat(context).hasSingleBean(MethodLoggingAspect.class);
            assertThat(context).hasSingleBean(LoggingTaskDecorator.class);
            assertThat(context.getBean(LoggingProperties.class).slowRequestThreshold())
                    .isEqualTo(Duration.ofSeconds(2));
        });
    }

    @Test
    void isInactiveOutsideAServletApplication() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(LoggingAutoConfiguration.class))
                .run(context -> assertThat(context).doesNotHaveBean(RequestLoggingFilter.class));
    }

    @Test
    void theSlowRequestThresholdIsConfigurable() {
        runner.withPropertyValues("pirca.logging.slow-request-threshold=500ms")
                .run(context -> assertThat(context.getBean(LoggingProperties.class).slowRequestThreshold())
                        .isEqualTo(Duration.ofMillis(500)));
    }

    @Test
    void readsTheTenantFromLibTenantWhenItIsPresent() {
        runner.run(context -> {
            TenantResolver resolver = context.getBean(TenantResolver.class);
            assertThat(resolver.current()).isEmpty();
            TenantContext.runAs("acme", () -> assertThat(resolver.current()).contains("acme"));
        });
    }

    @Test
    void startsWithoutLibTenantAndHasNoTenant() {
        runner.withClassLoader(new FilteredClassLoader("cl.pirca.lib.tenant")).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(TenantResolver.class).current()).isEmpty();
            assertThat(context).hasSingleBean(RequestLoggingFilter.class);
        });
    }

    @Test
    void skipsTheAopAndRestClientPiecesWhenTheirClassesAreMissing() {
        runner.withClassLoader(new FilteredClassLoader("org.aspectj", "org.springframework.web.client.RestClient"))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(MethodLoggingAspect.class);
                    assertThat(context).doesNotHaveBean(CorrelationIdRestClientCustomizer.class);
                });
    }

    @Test
    void everyPieceBacksOffWhenTheApplicationDefinesItsOwn() {
        runner.withUserConfiguration(OwnBeans.class).run(context -> {
            assertThat(context).hasSingleBean(RequestLoggingFilter.class);
            assertThat(context.getBean(RequestLoggingFilter.class)).isSameAs(OwnBeans.FILTER);
            assertThat(context.getBean(TenantResolver.class)).isSameAs(OwnBeans.RESOLVER);
            assertThat(context.getBean(CorrelationIdRestClientCustomizer.class)).isSameAs(OwnBeans.CUSTOMIZER);
            assertThat(context.getBean(MethodLoggingAspect.class)).isSameAs(OwnBeans.ASPECT);
            assertThat(context.getBean(LoggingTaskDecorator.class)).isSameAs(OwnBeans.DECORATOR);
        });
    }

    @Configuration(proxyBeanMethods = false)
    static class OwnBeans {
        static final TenantResolver RESOLVER = Optional::empty;
        static final RequestLoggingFilter FILTER =
                new RequestLoggingFilter(RESOLVER, new LoggingProperties(Duration.ofSeconds(1)));
        static final CorrelationIdRestClientCustomizer CUSTOMIZER = new CorrelationIdRestClientCustomizer();
        static final MethodLoggingAspect ASPECT = new MethodLoggingAspect();
        static final LoggingTaskDecorator DECORATOR = new LoggingTaskDecorator(null);

        @Bean
        TenantResolver ownResolver() {
            return RESOLVER;
        }

        @Bean
        RequestLoggingFilter ownFilter() {
            return FILTER;
        }

        @Bean
        CorrelationIdRestClientCustomizer ownCustomizer() {
            return CUSTOMIZER;
        }

        @Bean
        MethodLoggingAspect ownAspect() {
            return ASPECT;
        }

        @Bean
        LoggingTaskDecorator ownDecorator() {
            return DECORATOR;
        }

        @Bean
        TaskDecorator unrelated() {
            return r -> r;
        }
    }
}
