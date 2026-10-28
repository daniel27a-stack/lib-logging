package cl.pirca.lib.logging;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * At DEBUG, logs the entry and the exit (with duration) of every method of controller and service
 * beans. Spring AOP only intercepts calls that reach a proxied bean: a call from inside the same
 * class is not logged.
 */
@Aspect
public class MethodLoggingAspect {

    /**
     * Logs the call when the logger of the target class has DEBUG enabled.
     *
     * @param joinPoint the intercepted call
     * @return what the method returns
     * @throws Throwable what the method throws, unchanged
     */
    @Around("@within(org.springframework.web.bind.annotation.RestController)"
            + " || @within(org.springframework.stereotype.Controller)"
            + " || @within(org.springframework.stereotype.Service)")
    public Object log(ProceedingJoinPoint joinPoint) throws Throwable {
        Logger logger = LoggerFactory.getLogger(joinPoint.getTarget().getClass());
        if (!logger.isDebugEnabled()) {
            return joinPoint.proceed();
        }
        String method = joinPoint.getSignature().getName();
        logger.debug("-> {}", method);
        long start = System.nanoTime();
        try {
            Object result = joinPoint.proceed();
            logger.debug("<- {} in {} ms", method, millisSince(start));
            return result;
        } catch (Throwable e) {
            logger.debug("<- {} threw {} in {} ms", method, e.getClass().getName(), millisSince(start));
            throw e;
        }
    }

    private static long millisSince(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }
}
