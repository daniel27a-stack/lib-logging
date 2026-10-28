package cl.pirca.lib.logging;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.mock.env.MockEnvironment;

class LoggingEnvironmentPostProcessorTest {

    private final LoggingEnvironmentPostProcessor processor = new LoggingEnvironmentPostProcessor();

    @Test
    void usesTextWithCorrelationIdAndTenantByDefault() {
        MockEnvironment environment = new MockEnvironment();

        processor.postProcessEnvironment(environment, new SpringApplication());

        assertThat(environment.getProperty("logging.pattern.console"))
                .contains("%X{correlationId:-}", "%X{tenant:-}", "%kvp{NONE}", "APPLICATION_NAME", "logger{39}");
        assertThat(environment.getProperty("logging.structured.format.console")).isNull();
    }

    @Test
    void usesStructuredJsonWithTheProdProfile() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");

        processor.postProcessEnvironment(environment, new SpringApplication());

        assertThat(environment.getProperty("logging.structured.format.console")).isEqualTo("logstash");
        assertThat(environment.getProperty("logging.pattern.console")).isNull();
    }

    @Test
    void theApplicationCanOverrideTheDefaults() {
        MockEnvironment environment = new MockEnvironment().withProperty("logging.pattern.console", "%m%n");

        processor.postProcessEnvironment(environment, new SpringApplication());

        assertThat(environment.getProperty("logging.pattern.console")).isEqualTo("%m%n");
    }
}
