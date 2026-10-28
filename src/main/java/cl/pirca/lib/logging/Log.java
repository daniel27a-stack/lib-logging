package cl.pirca.lib.logging;

import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.event.Level;
import org.slf4j.spi.LoggingEventBuilder;

/**
 * Fluent API for logs written on demand:
 * {@code Log.action("debts").description("Debts listed").field("count", 3).info();}
 * The level is the last call. The log carries the {@code correlationId} and the {@code tenant} of
 * the MDC, and is written with the logger of the class that calls {@link #action(String)}.
 */
public final class Log {

    private static final String ACTION_KEY = "action";
    private static final StackWalker WALKER = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE);

    private final Logger logger;
    private final String action;
    private final Map<String, Object> fields = new LinkedHashMap<>();
    private String description;

    private Log(Logger logger, String action) {
        this.logger = logger;
        this.action = action;
    }

    /**
     * Starts a log for the given action. The action is always the first field of the log.
     *
     * @param action what is being done, a short stable name
     * @return the log being built
     */
    public static Log action(String action) {
        return new Log(LoggerFactory.getLogger(WALKER.getCallerClass()), action);
    }

    /**
     * Sets the readable message of the log.
     *
     * @param description the message
     * @return this log
     */
    public Log description(String description) {
        this.description = description;
        return this;
    }

    /**
     * Adds a field. In text it prints as {@code key=value}; in JSON it is a separate field.
     *
     * @param key   the field name; {@code action} is reserved
     * @param value the field value
     * @return this log
     */
    public Log field(String key, Object value) {
        if (ACTION_KEY.equals(key)) {
            throw new IllegalArgumentException("'action' is reserved for the action of the log");
        }
        fields.put(key, value);
        return this;
    }

    /** Writes the log at DEBUG. */
    public void debug() {
        write(Level.DEBUG, null);
    }

    /** Writes the log at INFO. */
    public void info() {
        write(Level.INFO, null);
    }

    /** Writes the log at WARN. */
    public void warn() {
        write(Level.WARN, null);
    }

    /** Writes the log at ERROR. */
    public void error() {
        write(Level.ERROR, null);
    }

    /**
     * Writes the log at ERROR with the stack trace of the throwable, once.
     *
     * @param cause what failed
     */
    public void error(Throwable cause) {
        write(Level.ERROR, cause);
    }

    private void write(Level level, Throwable cause) {
        LoggingEventBuilder builder = logger.atLevel(level).addKeyValue(ACTION_KEY, action);
        for (Map.Entry<String, Object> field : fields.entrySet()) {
            builder = builder.addKeyValue(field.getKey(), field.getValue());
        }
        if (cause != null) {
            builder = builder.setCause(cause);
        }
        builder.log(description != null ? description : action);
    }
}
