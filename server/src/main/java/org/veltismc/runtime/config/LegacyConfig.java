package org.veltismc.runtime.config;

import org.apache.logging.log4j.Logger;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Reports the retired Paper-style configuration files without reading, requiring
 * or deleting them.
 *
 * <p>An earlier Veltis build shipped a reflective mirror of Paper's
 * {@code paper-global.yml} and {@code paper-world-defaults.yml} that wrote
 * {@code config/veltis-global.yml} and
 * {@code config/veltis-world-defaults.yml} on first start. No runtime code ever
 * read a value from either file: the world engine's settings live in the
 * validated {@code org.veltismc.world.api.WorldConfig} record, and this mirror
 * was the only thing that ever consumed the YAML. It has been removed, along
 * with its parser.
 *
 * <p>An installation that already ran the old build must not be damaged by the
 * removal. This class reports each retired file once and leaves it exactly where
 * it is. Nothing is deleted, nothing must exist for the server to start, and no
 * setting is silently reinterpreted. There is deliberately no migration: not one
 * value in those files affected runtime behavior, so there is nothing to carry
 * forward, and the warning tells an operator the files are obsolete and safe to
 * remove.
 */
public final class LegacyConfig {

    /**
     * The retired files, as they may exist under {@code <home>/config}.
     *
     * <p>{@code veltis-world-defaults.yml} is the name the old loader actually
     * wrote. {@code veltis-world-defults.yml} is the misspelling that appears in
     * reports of it; it is listed too, so a file under either spelling is
     * reported rather than silently ignored.
     */
    private static final List<String> RETIRED_FILES = List.of(
            "veltis-global.yml",
            "veltis-world-defaults.yml",
            "veltis-world-defults.yml");

    private LegacyConfig() {}

    /**
     * The retired files that exist under {@code <home>/config}, in a stable
     * order. Empty when there are none, which is the normal case.
     */
    public static List<Path> present(Path homeDir) {
        var configDir = homeDir.resolve("config");
        var found = new ArrayList<Path>();
        for (var name : RETIRED_FILES) {
            var file = configDir.resolve(name);
            if (Files.isRegularFile(file)) {
                found.add(file);
            }
        }
        return found;
    }

    /**
     * Reports every retired file that exists and returns whether any was found.
     *
     * <p>Pure notification: the files are not opened, not parsed and not
     * deleted, and a damaged or unreadable one cannot stop the server from
     * starting.
     */
    public static boolean warnIfPresent(Path homeDir, Logger log) {
        var found = present(homeDir);
        if (found.isEmpty()) {
            return false;
        }
        log.warn("Ignoring {} retired Veltis configuration file(s) from an earlier"
                + " build; they are no longer read and can be deleted:",
                found.size());
        for (var file : found) {
            log.warn("  {} (obsolete; not read, not deleted)", file);
        }
        return true;
    }
}
