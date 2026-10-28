package cl.pirca.lib.logging;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.LoggerFactory;

/** Collects the request lines the filter writes, so a test can count them and read status and duration. */
final class RequestLines implements AutoCloseable {

    private static final Pattern LINE = Pattern.compile(".* -> (\\d+) in (\\d+) ms");

    private final Logger logger = (Logger) LoggerFactory.getLogger(RequestLoggingFilter.class);
    private final Level previousLevel = logger.getLevel();
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

    RequestLines() {
        logger.setLevel(Level.INFO);
        appender.start();
        logger.addAppender(appender);
    }

    /** The lines of the request with this correlation id. */
    List<ILoggingEvent> of(String correlationId) {
        return List.copyOf(appender.list).stream()
                .filter(e -> correlationId.equals(e.getMDCPropertyMap().get("correlationId")))
                .toList();
    }

    static int status(ILoggingEvent event) {
        return Integer.parseInt(matcher(event).group(1));
    }

    static long millis(ILoggingEvent event) {
        return Long.parseLong(matcher(event).group(2));
    }

    private static Matcher matcher(ILoggingEvent event) {
        Matcher matcher = LINE.matcher(event.getFormattedMessage());
        if (!matcher.matches()) {
            throw new AssertionError("not a request line: " + event.getFormattedMessage());
        }
        return matcher;
    }

    @Override
    public void close() {
        logger.detachAppender(appender);
        logger.setLevel(previousLevel);
    }
}
