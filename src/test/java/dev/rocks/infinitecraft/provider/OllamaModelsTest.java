package dev.rocks.infinitecraft.provider;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class OllamaModelsTest {
    @Test void fetchesDistinctSortedNamesWithNoCredentials() throws Exception {
        AtomicReference<String> method = new AtomicReference<>();
        AtomicReference<String> authorization = new AtomicReference<>();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/tags", exchange -> {
            method.set(exchange.getRequestMethod());
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            byte[] body = "{\"models\":[{\"name\":\"zeta:7b\"},{\"name\":\"org/alpha:latest\"},{\"name\":\"zeta:7b\"}]}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body); exchange.close();
        });
        server.start();
        try {
            assertEquals(List.of("org/alpha:latest", "zeta:7b"), OllamaModels.fetch(url(server) + "/"));
            assertEquals("GET", method.get());
            assertNull(authorization.get());
        } finally { server.stop(0); }
    }

    @Test void rejectsInvalidResponsesAndUnsafeUrlsWithoutLeakingData() throws Exception {
        for (String body : List.of("not json secret-token", "{}", "{\"models\":[{\"name\":123}]}",
                "{\"models\":[{\"name\":\"bad secret-token\"}]}", "{\"models\":[{}]}")) {
            var failure = assertThrows(IOException.class, () -> fetchResponse(200, body));
            assertEquals("Ollama returned invalid model data", failure.getMessage());
        }
        assertEquals(List.of(), fetchResponse(200, "{\"models\":[]}"));
        assertTrue(assertThrows(IOException.class, () -> fetchResponse(302, "redirect secret")).getMessage().contains("HTTP 302"));
        assertTrue(assertThrows(IOException.class, () -> fetchResponse(500, "secret")).getMessage().contains("HTTP 500"));
        assertThrows(IOException.class, () -> fetchResponse(200, " ".repeat(1_048_577)));
        for (String url : List.of("http://example.com", "http://secret@localhost", "http://localhost?secret=token")) {
            assertEquals("Invalid Ollama URL", assertThrows(IOException.class, () -> OllamaModels.fetch(url)).getMessage());
        }
    }

    private static List<String> fetchResponse(int status, String body) throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/tags", exchange -> {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Location", "/redirected");
            exchange.sendResponseHeaders(status, bytes.length);
            try { exchange.getResponseBody().write(bytes); } finally { exchange.close(); }
        });
        server.start();
        try { return OllamaModels.fetch(url(server)); }
        finally { server.stop(0); }
    }

    private static String url(HttpServer server) { return "http://127.0.0.1:" + server.getAddress().getPort(); }
}
