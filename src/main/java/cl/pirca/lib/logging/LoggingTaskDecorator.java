package cl.pirca.lib.logging;

import java.util.Map;
import org.slf4j.MDC;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.task.TaskDecorator;

/**
 * Copies the MDC (correlation id and tenant) of the submitting thread to the thread that runs the
 * task. It is a composite: it also applies every other {@link TaskDecorator} bean (for example the
 * one of lib-tenant, or the application's own), inside the MDC copy.
 */
public class LoggingTaskDecorator implements TaskDecorator {

    private final ObjectProvider<TaskDecorator> decorators;

    /**
     * Creates the decorator.
     *
     * @param decorators every {@link TaskDecorator} bean; this one is ignored
     */
    public LoggingTaskDecorator(ObjectProvider<TaskDecorator> decorators) {
        this.decorators = decorators;
    }

    @Override
    public Runnable decorate(Runnable runnable) {
        Runnable inner = runnable;
        for (TaskDecorator other : decorators.orderedStream().filter(d -> d != this).toList()) {
            inner = other.decorate(inner);
        }
        return withMdc(MDC.getCopyOfContextMap(), inner);
    }

    private static Runnable withMdc(Map<String, String> captured, Runnable task) {
        return () -> {
            Map<String, String> previous = MDC.getCopyOfContextMap();
            setMdc(captured);
            try {
                task.run();
            } finally {
                setMdc(previous);
            }
        };
    }

    private static void setMdc(Map<String, String> context) {
        if (context == null) {
            MDC.clear();
        } else {
            MDC.setContextMap(context);
        }
    }
}
