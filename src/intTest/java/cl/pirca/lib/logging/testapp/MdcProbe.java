package cl.pirca.lib.logging.testapp;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.slf4j.MDC;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Records the MDC seen by Spring MVC around the handler: {@code pre-handle} runs in the first dispatch and
 * {@code post-handle} in the async dispatch. Each entry is {@code phase|correlationId|tenant}.
 */
@Component
public class MdcProbe implements HandlerInterceptor {

    private final List<String> entries = new CopyOnWriteArrayList<>();

    @Override
    public boolean preHandle(jakarta.servlet.http.HttpServletRequest request,
            jakarta.servlet.http.HttpServletResponse response, Object handler) {
        record("pre-handle");
        return true;
    }

    @Override
    public void postHandle(jakarta.servlet.http.HttpServletRequest request,
            jakarta.servlet.http.HttpServletResponse response, Object handler,
            org.springframework.web.servlet.ModelAndView modelAndView) {
        record("post-handle");
    }

    private void record(String phase) {
        entries.add(phase + "|" + MDC.get("correlationId") + "|" + MDC.get("tenant"));
    }

    public List<String> entries() {
        return List.copyOf(entries);
    }

    /** Registers the probe as an interceptor. */
    @Configuration
    static class Registration implements WebMvcConfigurer {

        private final MdcProbe probe;

        Registration(MdcProbe probe) {
            this.probe = probe;
        }

        @Override
        public void addInterceptors(InterceptorRegistry registry) {
            registry.addInterceptor(probe).addPathPatterns("/async-line/**");
        }
    }
}
