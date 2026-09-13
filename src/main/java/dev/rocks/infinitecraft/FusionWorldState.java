package dev.rocks.infinitecraft;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** The host's toggle belongs to the world, including when it is explicitly disabled. */
final class FusionWorldState {
    static boolean load(Path file) throws IOException {
        if (!Files.exists(file)) return false;
        return switch (Files.readString(file).strip()) {
            case "true" -> true;
            case "false" -> false;
            default -> throw new IOException("Invalid fusion toggle in " + file);
        };
    }

    static void save(Path file, boolean enabled) throws IOException {
        Files.createDirectories(file.getParent());
        Path temporary = Files.createTempFile(file.getParent(), "fusion-state-", ".tmp");
        try {
            Files.writeString(temporary, Boolean.toString(enabled) + "\n");
            try {
                Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException error) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
}
