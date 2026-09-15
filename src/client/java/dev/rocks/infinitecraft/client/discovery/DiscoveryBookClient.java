package dev.rocks.infinitecraft.client.discovery;

import dev.rocks.infinitecraft.discovery.DiscoveryScreenPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

public final class DiscoveryBookClient {
    private DiscoveryBookClient() {
    }

    public static void initialize() {
        ClientPlayNetworking.registerGlobalReceiver(DiscoveryScreenPayload.TYPE, (payload, context) ->
                context.client().setScreenAndShow(new DiscoveryBookScreen(payload.entries(), payload.personal())));
    }
}
