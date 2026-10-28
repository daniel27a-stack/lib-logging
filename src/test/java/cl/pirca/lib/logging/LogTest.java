package cl.pirca.lib.logging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

class LogTest {

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void writesTheDescriptionAndTheActionFirstThenTheFieldsInOrder() {
        try (CapturedLogs logs = new CapturedLogs(LogTest.class, Level.DEBUG)) {
            Log.action("debts").description("Debts listed").field("count", 3).field("zone", "north").info();

            ILoggingEvent event = logs.only();
            assertThat(event.getLevel()).isEqualTo(Level.INFO);
            assertThat(event.getFormattedMessage()).isEqualTo("Debts listed");
            assertThat(event.getKeyValuePairs()).extracting(kv -> kv.key, kv -> kv.value)
                    .containsExactly(org.assertj.core.groups.Tuple.tuple("action", "debts"),
                            org.assertj.core.groups.Tuple.tuple("count", 3),
                            org.assertj.core.groups.Tuple.tuple("zone", "north"));
            assertThat(event.getLoggerName()).isEqualTo(LogTest.class.getName());
        }
    }

    @Test
    void usesTheActionAsMessageWhenThereIsNoDescription() {
        try (CapturedLogs logs = new CapturedLogs(LogTest.class, Level.DEBUG)) {
            Log.action("debts").info();

            assertThat(logs.only().getFormattedMessage()).isEqualTo("debts");
        }
    }

    @Test
    void everyTerminalWritesItsLevel() {
        try (CapturedLogs logs = new CapturedLogs(LogTest.class, Level.DEBUG)) {
            Log.action("a").debug();
            Log.action("a").info();
            Log.action("a").warn();
            Log.action("a").error();

            assertThat(logs.events()).extracting(ILoggingEvent::getLevel)
                    .containsExactly(Level.DEBUG, Level.INFO, Level.WARN, Level.ERROR);
        }
    }

    @Test
    void errorWithAThrowableWritesTheTraceOnce() {
        try (CapturedLogs logs = new CapturedLogs(LogTest.class, Level.DEBUG)) {
            Log.action("pay").description("Payment failed").error(new IllegalStateException("boom"));

            ILoggingEvent event = logs.only();
            assertThat(event.getLevel()).isEqualTo(Level.ERROR);
            assertThat(event.getThrowableProxy().getMessage()).isEqualTo("boom");
        }
    }

    @Test
    void inheritsTheCorrelationIdAndTheTenantFromTheMdc() {
        MDC.put("correlationId", "c-1");
        MDC.put("tenant", "acme");
        try (CapturedLogs logs = new CapturedLogs(LogTest.class, Level.DEBUG)) {
            Log.action("x").info();

            assertThat(logs.only().getMDCPropertyMap()).containsEntry("correlationId", "c-1")
                    .containsEntry("tenant", "acme");
        }
    }

    @Test
    void doesNotWriteBelowTheConfiguredLevel() {
        try (CapturedLogs logs = new CapturedLogs(LogTest.class, Level.WARN)) {
            Log.action("x").info();

            assertThat(logs.events()).isEmpty();
        }
    }

    @Test
    void rejectsAFieldNamedAction() {
        Log log = Log.action("x");

        assertThatThrownBy(() -> log.field("action", "y")).isInstanceOf(IllegalArgumentException.class);
    }
}
