package dev.rocks.infinitecraft.client;

import dev.rocks.infinitecraft.fusion.CrafterStatePayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

/** Latest state of the Fusion Crafter this client has open. */
public final class CrafterStateClient {
    private static CrafterStatePayload state;

    private CrafterStateClient() {}

    static void initialize() {
        ClientPlayNetworking.registerGlobalReceiver(CrafterStatePayload.TYPE, (payload, context) -> state = payload);
    }

    /** Null until the server reports on this menu, e.g. on servers without the mod. */
    public static CrafterStatePayload state(int containerId) {
        var current = state;
        return current != null && current.containerId() == containerId ? current : null;
    }
}
