package dev.rocks.infinitecraft;

import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ServerInitializationTest {
    @Test void registersServerEntrypointWithoutClientUiDependencies() {
        assertEquals(EnvType.SERVER, FabricLoader.getInstance().getEnvironmentType());
        assertFalse(FabricLoader.getInstance().isModLoaded("cloth-config"));
        assertFalse(FabricLoader.getInstance().isModLoaded("modmenu"));
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        assertDoesNotThrow(() -> new InfiniteCraftMod().onInitialize());
    }
}
