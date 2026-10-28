package cl.pirca.lib.logging.testapp;

import cl.pirca.lib.logging.Log;
import cl.pirca.lib.tenant.TenantContext;
import java.util.concurrent.CompletableFuture;
import org.slf4j.MDC;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class GreetingService {

    public String greet(String name) {
        Log.action("greet").description("Greeting " + name).field("name", name).info();
        return "hello " + name;
    }

    @Async
    public CompletableFuture<String> contextInAsyncMethod() {
        return CompletableFuture.completedFuture(MDC.get("correlationId") + "|" + TenantContext.current().orElse("-"));
    }
}
