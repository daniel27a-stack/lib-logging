package cl.pirca.lib.logging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.qos.logback.classic.Level;
import org.junit.jupiter.api.Test;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.stereotype.Service;

class MethodLoggingAspectTest {

    @Service
    static class Sample {
        String hello(String name) {
            return "hi " + name;
        }

        void fail() {
            throw new IllegalStateException("nope");
        }
    }

    private static Sample proxied() {
        AspectJProxyFactory factory = new AspectJProxyFactory(new Sample());
        factory.setProxyTargetClass(true);
        factory.addAspect(new MethodLoggingAspect());
        return factory.getProxy();
    }

    @Test
    void logsEntryAndExitWithDurationAtDebug() {
        try (CapturedLogs logs = new CapturedLogs(Sample.class, Level.DEBUG)) {
            assertThat(proxied().hello("ana")).isEqualTo("hi ana");

            assertThat(logs.events()).extracting(e -> e.getFormattedMessage())
                    .hasSize(2)
                    .first().isEqualTo("-> hello");
            assertThat(logs.events().get(1).getFormattedMessage()).matches("<- hello in \\d+ ms");
        }
    }

    @Test
    void logsNothingAtInfo() {
        try (CapturedLogs logs = new CapturedLogs(Sample.class, Level.INFO)) {
            assertThat(proxied().hello("ana")).isEqualTo("hi ana");

            assertThat(logs.events()).isEmpty();
        }
    }

    @Test
    void logsTheExitOfAFailingMethodAndRethrowsWithoutATrace() {
        try (CapturedLogs logs = new CapturedLogs(Sample.class, Level.DEBUG)) {
            Sample sample = proxied();
            assertThatThrownBy(sample::fail).isInstanceOf(IllegalStateException.class);

            assertThat(logs.events().get(1).getFormattedMessage())
                    .matches("<- fail threw java.lang.IllegalStateException in \\d+ ms");
            assertThat(logs.events().get(1).getThrowableProxy()).isNull();
        }
    }
}
