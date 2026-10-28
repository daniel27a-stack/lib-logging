package cl.pirca.lib.logging.testapp;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class GreetingController {

    private final GreetingService service;

    public GreetingController(GreetingService service) {
        this.service = service;
    }

    @GetMapping("/greet/{name}")
    public String greet(@PathVariable("name") String name) {
        return service.greet(name);
    }

    @GetMapping("/async")
    public String async() throws Exception {
        return service.contextInAsyncMethod().get();
    }

    @GetMapping("/fail/{status}")
    public String fail(@PathVariable("status") int status) {
        throw new TestFailure(status, "failure " + status);
    }

    @GetMapping("/unhandled")
    public String unhandled() {
        throw new IllegalStateException("unhandled boom");
    }
}
