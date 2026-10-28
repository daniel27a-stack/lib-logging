package cl.pirca.lib.logging;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class LibraryAutoConfigurationTest {

    @Test
    void libraryInfoCarriesTheLibraryName() {
        LibraryInfo info = new LibraryAutoConfiguration().libraryInfo();

        assertEquals("lib-logging", info.name());
    }
}
