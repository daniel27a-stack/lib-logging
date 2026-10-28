package cl.pirca.lib.logging;

import java.util.Optional;

/**
 * Tells the logging filter which tenant the current request belongs to. The default implementation
 * has no tenant; when lib-tenant is on the classpath the auto-configuration reads it from there.
 */
@FunctionalInterface
public interface TenantResolver {

    /**
     * Returns the tenant of the current request, or empty when there is none.
     */
    Optional<String> current();
}
