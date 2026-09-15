package dev.rocks.infinitecraft.provider;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class ProviderConnectionTestTest {
    @Test void asyncTestUsesRealValidationAndAllowsOnlyOneRequest() throws Exception {
        var arrived = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/chat", exchange -> {
            arrived.countDown();
            try {
                if (!release.await(3, TimeUnit.SECONDS)) return;
                byte[] response = "{\"message\":{\"content\":\"{\\\"itemId\\\":\\\"minecraft:cobblestone\\\",\\\"count\\\":1}\"}}".getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, response.length);
                exchange.getResponseBody().write(response);
            } catch (InterruptedException error) { Thread.currentThread().interrupt(); }
            finally { exchange.close(); }
        });
        server.start();
        try {
            var config = new ProviderConfig("ollama", "http://127.0.0.1:" + server.getAddress().getPort(), "test-model", "", 3);
            var pending = ProviderConnectionTest.start(config);
            assertTrue(arrived.await(2, TimeUnit.SECONDS));
            assertFalse(pending.isDone(), "Caller must stay free while the response is pending");
            var busy = ProviderConnectionTest.start(config).get(1, TimeUnit.SECONDS);
            assertFalse(busy.success());
            assertTrue(busy.message().contains("already running"));
            release.countDown();
            assertTrue(pending.get(3, TimeUnit.SECONDS).success());
        } finally { release.countDown(); server.stop(0); }
    }

}
