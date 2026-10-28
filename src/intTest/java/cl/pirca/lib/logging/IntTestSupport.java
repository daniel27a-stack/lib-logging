package cl.pirca.lib.logging;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.springframework.boot.logging.LoggingSystem;

/** Calls the test application over HTTP. */
final class IntTestSupport {

    private IntTestSupport() {
    }

    /**
     * Spring Boot configures the logging system once per JVM, so the first context decides the format
     * for the others. A test that asserts on the format calls this before its context starts.
     */
    static void resetLoggingSystem() {
        LoggingSystem.get(IntTestSupport.class.getClassLoader()).cleanUp();
        // Boot keeps the structured format of the previous context in a system property.
        System.clearProperty("CONSOLE_LOG_STRUCTURED_FORMAT");
    }

    static HttpResponse<String> get(int port, String path, String... headers) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path));
        for (int i = 0; i < headers.length; i += 2) {
            request.header(headers[i], headers[i + 1]);
        }
        return HttpClient.newHttpClient().send(request.build(), HttpResponse.BodyHandlers.ofString());
    }
}
