package dev.rocks.infinitecraft.item;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.rocks.infinitecraft.core.PairKey;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public final class ComponentPairKey {
    /** A world-seeded coin toss stays the same when ingredients are swapped or a request is retried. */
    public static boolean firstWins(JsonElement first, JsonElement second, long worldSeed) {
        String a = canonical(first).toString(), b = canonical(second).toString();
        boolean firstIsLower = a.compareTo(b) <= 0;
        String pair = firstIsLower ? a + "\n" + b : b + "\n" + a;
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest((worldSeed + "\n" + pair).getBytes(StandardCharsets.UTF_8));
            return firstIsLower == ((hash[0] & 1) == 0);
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException(error);
        }
    }
    public static String of(String firstId, String secondId, JsonElement first, JsonElement second) {
        String a = canonical(first).toString(), b = canonical(second).toString();
        String pair = a.compareTo(b) <= 0 ? a + "\n" + b : b + "\n" + a;
        try {
            return PairKey.of(firstId, secondId) + "#" + HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(pair.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException(error);
        }
    }

    private static JsonElement canonical(JsonElement value) {
        if (value.isJsonObject()) {
            JsonObject sorted = new JsonObject();
            value.getAsJsonObject().keySet().stream().sorted().forEach(key -> sorted.add(key, canonical(value.getAsJsonObject().get(key))));
            return sorted;
        }
        if (value.isJsonArray()) {
            JsonArray array = new JsonArray();
            value.getAsJsonArray().forEach(entry -> array.add(canonical(entry)));
            return array;
        }
        return value;
    }
}
