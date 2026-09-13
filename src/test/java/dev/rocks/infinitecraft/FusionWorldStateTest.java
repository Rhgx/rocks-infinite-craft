package dev.rocks.infinitecraft;

import java.nio.file.Files;
import java.nio.file.Path;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class FusionWorldStateTest {
    @TempDir Path directory;

    @Test void remembersBothToggleStatesWithoutAffectingOtherWorlds() throws IOException {
        Path first = directory.resolve("first/fusion-enabled.json");
        Path second = directory.resolve("second/fusion-enabled.json");
        assertFalse(FusionWorldState.load(first));
        FusionWorldState.save(first, true);
        assertTrue(FusionWorldState.load(first));
        assertFalse(FusionWorldState.load(second));
        FusionWorldState.save(first, false);
        assertFalse(FusionWorldState.load(first));
        Files.writeString(first, "broken");
        assertThrows(IOException.class, () -> FusionWorldState.load(first));
        assertEquals("broken", Files.readString(first));
    }
}
