package cl.pirca.lib.logging;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.util.List;
import org.slf4j.LoggerFactory;

/** Collects the log events of one class so a test can read them. */
final class CapturedLogs implements AutoCloseable {

    private final Logger logger;
    private final Level previousLevel;
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

    CapturedLogs(Class<?> source, Level level) {
        logger = (Logger) LoggerFactory.getLogger(source);
        previousLevel = logger.getLevel();
        logger.setLevel(level);
        appender.start();
        logger.addAppender(appender);
    }

    List<ILoggingEvent> events() {
        return appender.list;
    }

    ILoggingEvent only() {
        if (appender.list.size() != 1) {
            throw new AssertionError("expected one event but got " + appender.list);
        }
        return appender.list.getFirst();
    }

    @Override
    public void close() {
        logger.detachAppender(appender);
        logger.setLevel(previousLevel);
    }
}
