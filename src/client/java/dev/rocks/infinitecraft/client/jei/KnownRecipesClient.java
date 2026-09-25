package dev.rocks.infinitecraft.client.jei;

import dev.rocks.infinitecraft.discovery.DiscoveryCollection;
import dev.rocks.infinitecraft.discovery.KnownRecipes;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Discoveries received from the server, keyed by id. Free of JEI classes so it loads without JEI. */
public final class KnownRecipesClient {
    private static final Map<Integer, DiscoveryCollection.Entry> known = new LinkedHashMap<>();
    private static Runnable listener = () -> {};

    private KnownRecipesClient() {}

    public static void initialize() {
        // Without a receiver the server never sends the list, so clients without JEI skip the traffic.
        if (!FabricLoader.getInstance().isModLoaded("jei")) return;
        ClientPlayNetworking.registerGlobalReceiver(KnownRecipes.TYPE, (payload, context) -> {
            if (payload.replace()) known.clear();
            payload.entries().forEach(entry -> known.put(entry.id(), entry));
            listener.run();
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> known.clear());
    }

    static List<DiscoveryCollection.Entry> all() {
        return List.copyOf(known.values());
    }

    static void listen(Runnable onChange) {
        listener = onChange;
    }
}
