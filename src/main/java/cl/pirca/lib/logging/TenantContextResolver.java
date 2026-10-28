package cl.pirca.lib.logging;

import cl.pirca.lib.tenant.TenantContext;
import java.util.Optional;

/**
 * Reads the tenant from lib-tenant. Only loaded when lib-tenant is on the classpath.
 */
class TenantContextResolver implements TenantResolver {

    @Override
    public Optional<String> current() {
        return TenantContext.current();
    }
}
