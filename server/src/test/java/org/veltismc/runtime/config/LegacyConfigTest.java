package org.veltismc.runtime.config;

import org.apache.logging.log4j.LogManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A fresh start must not create the retired configuration files, and an
 * installation that already has them must be reported without being touched.
 */
class LegacyConfigTest {

    @TempDir
    Path home;

    @Test
    void aFreshHomeIsNeverModified() {
        assertFalse(LegacyConfig.warnIfPresent(home, LogManager.getLogger(LegacyConfig.class)));
        assertFalse(Files.exists(home.resolve("config")),
                "fresh startup must not create a config directory for retired files");
    }

    @Test
    void retiredFilesAreReportedAndLeftInPlace() throws Exception {
        var config = Files.createDirectories(home.resolve("config"));
        var global = Files.writeString(config.resolve("veltis-global.yml"), "misc:\n  enable-nether: false\n");
        var world = Files.writeString(config.resolve("veltis-world-defaults.yml"), "chunks: {}\n");

        assertEquals(List.of(global, world), LegacyConfig.present(home));

        assertTrue(LegacyConfig.warnIfPresent(home, LogManager.getLogger(LegacyConfig.class)));
        assertTrue(Files.exists(global), "an operator's file must never be deleted by the server");
        assertTrue(Files.exists(world), "an operator's file must never be deleted by the server");
    }

    @Test
    void theMisspelledWorldDefaultsNameIsAlsoReported() throws Exception {
        var config = Files.createDirectories(home.resolve("config"));
        var misspelled = Files.writeString(config.resolve("veltis-world-defults.yml"), "");

        assertEquals(List.of(misspelled), LegacyConfig.present(home));
    }

    @Test
    void anUnrelatedFileInTheConfigDirectoryIsIgnored() throws Exception {
        var config = Files.createDirectories(home.resolve("config"));
        Files.writeString(config.resolve("other.yml"), "not Ours\n");

        assertEquals(List.of(), LegacyConfig.present(home));
    }
}
