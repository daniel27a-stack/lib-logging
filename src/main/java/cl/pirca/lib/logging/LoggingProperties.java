package cl.pirca.lib.logging;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Configuration of the library, under the prefix {@code pirca.logging}.
 *
 * @param slowRequestThreshold a request that takes longer than this is logged as WARN (default 2 seconds)
 */
@ConfigurationProperties("pirca.logging")
public record LoggingProperties(@DefaultValue("2s") Duration slowRequestThreshold) {
}
