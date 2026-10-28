package cl.pirca.lib.logging;

import java.util.Optional;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.weaver.Advice;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication.Type;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.restclient.RestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.task.TaskDecorator;
import org.springframework.util.ClassUtils;
import org.springframework.web.client.RestClient;

/**
 * Registers the logging pieces in servlet web applications. Every piece backs off when the
 * application defines its own bean of the same type.
 */
@AutoConfiguration(afterName = "cl.pirca.lib.tenant.TenantAutoConfiguration")
@ConditionalOnWebApplication(type = Type.SERVLET)
@EnableConfigurationProperties(LoggingProperties.class)
public class LoggingAutoConfiguration {

    private static final String TENANT_CONTEXT = "cl.pirca.lib.tenant.TenantContext";

    @Configuration(proxyBeanMethods = false)
    static class Inbound {

        @Bean
        @ConditionalOnMissingBean
        TenantResolver tenantResolver(ResourceLoader resourceLoader) {
            return ClassUtils.isPresent(TENANT_CONTEXT, resourceLoader.getClassLoader())
                    ? new TenantContextResolver()
                    : Optional::empty;
        }

        @Bean
        @ConditionalOnMissingBean
        RequestLoggingFilter requestLoggingFilter(TenantResolver tenantResolver, LoggingProperties properties) {
            return new RequestLoggingFilter(tenantResolver, properties);
        }
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass({RestClient.class, RestClientCustomizer.class})
    static class Outbound {

        @Bean
        @ConditionalOnMissingBean
        CorrelationIdRestClientCustomizer correlationIdRestClientCustomizer() {
            return new CorrelationIdRestClientCustomizer();
        }
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass({Aspect.class, Advice.class})
    static class Aop {

        @Bean
        @ConditionalOnMissingBean
        MethodLoggingAspect methodLoggingAspect() {
            return new MethodLoggingAspect();
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class Async {

        @Bean
        @Primary
        @ConditionalOnMissingBean(LoggingTaskDecorator.class)
        LoggingTaskDecorator loggingTaskDecorator(ObjectProvider<TaskDecorator> decorators) {
            return new LoggingTaskDecorator(decorators);
        }
    }
}
