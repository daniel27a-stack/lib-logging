package cl.pirca.lib.logging;

import java.util.Map;
import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.Profiles;

/**
 * Sets the default log format with the lowest precedence, so the application can override any of it:
 * structured JSON with the {@code prod} profile, readable text with correlation id and tenant otherwise.
 */
public class LoggingEnvironmentPostProcessor implements EnvironmentPostProcessor {

    static final String SOURCE_NAME = "pirca-logging-defaults";
    static final String PROD_PROFILE = "prod";
    static final String TEXT_PATTERN = "%d{yyyy-MM-dd'T'HH:mm:ss.SSSXXX} %5p --- "
            + "%esb(){APPLICATION_NAME}%esb{APPLICATION_GROUP}[%15.15t] "
            + "[%X{correlationId:-},%X{tenant:-}] %-40.40logger{39} : %m%replace(%kvp{NONE}){'^(?=.)',' '}%n%wEx";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        Map<String, Object> defaults = environment.acceptsProfiles(Profiles.of(PROD_PROFILE))
                ? Map.of("logging.structured.format.console", "logstash")
                : Map.of("logging.pattern.correlation", "[%X{correlationId:-},%X{tenant:-}] ",
                        "logging.pattern.console", TEXT_PATTERN);
        environment.getPropertySources().addLast(new MapPropertySource(SOURCE_NAME, defaults));
    }
}
