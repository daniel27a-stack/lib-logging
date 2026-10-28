package cl.pirca.lib.logging.testapp;

import jakarta.annotation.PreDestroy;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.async.DeferredResult;

/** Controllers that return Callable, CompletableFuture and DeferredResult, each finishing after {@link #DELAY_MS}. */
@RestController
public class AsyncController {

    public static final long DELAY_MS = 400;

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);

    @GetMapping("/async-line/sync")
    public String sync() {
        return "sync";
    }

    @GetMapping("/async-line/callable/{outcome}")
    public Callable<String> callable(@PathVariable("outcome") String outcome) {
        return () -> {
            Thread.sleep(DELAY_MS);
            return result(outcome);
        };
    }

    @GetMapping("/async-line/future/{outcome}")
    public CompletableFuture<String> future(@PathVariable("outcome") String outcome) {
        return CompletableFuture.supplyAsync(() -> result(outcome),
                CompletableFuture.delayedExecutor(DELAY_MS, TimeUnit.MILLISECONDS));
    }

    @GetMapping("/async-line/deferred/{outcome}")
    public DeferredResult<String> deferred(@PathVariable("outcome") String outcome) {
        DeferredResult<String> deferred = new DeferredResult<>();
        scheduler.schedule(() -> {
            try {
                deferred.setResult(result(outcome));
            } catch (RuntimeException e) {
                deferred.setErrorResult(e);
            }
        }, DELAY_MS, TimeUnit.MILLISECONDS);
        return deferred;
    }

    private static String result(String outcome) {
        return switch (outcome) {
            case "fail" -> throw new IllegalStateException("async boom");
            case "notfound" -> throw new TestFailure(404, "async not found");
            default -> "done";
        };
    }

    @PreDestroy
    void close() {
        scheduler.shutdownNow();
    }
}
