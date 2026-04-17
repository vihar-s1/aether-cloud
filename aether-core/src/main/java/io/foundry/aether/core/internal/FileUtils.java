/*
 * Copyright 2026 Foundry
 * SPDX-License-Identifier: Apache-2.0
 */

package io.foundry.aether.core.internal;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Predicate;

public final class FileUtils {

    private FileUtils() {}

    public static Path ensurePathExists(String... paths) throws IOException {
        String concatenatedPath = String.join(File.separator, paths);
        Path path = Path.of(concatenatedPath);
        Files.createDirectories(path.getParent());
        return path;
    }

    public static List<Path> filterFiles(Path path, Predicate<Path> filter) throws IOException {
        try (var stream = Files.walk(path)) {
            return stream.filter(Files::isRegularFile).filter(filter).toList();
        }
    }
}
