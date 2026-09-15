package dev.rocks.infinitecraft.discovery;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.item.ItemStack;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

/** Encodes and atomically persists a world's discovery collection. */
final class DiscoveryStore {
    record Stored(DiscoveryCollection.Entry entry, Set<UUID> owners, String encoded) {
    }

    private final Path file;
    private final RegistryOps<JsonElement> ops;
    private List<String> pendingSave;
    private boolean saving;

    DiscoveryStore(Path file, HolderLookup.Provider registries) {
        this.file = file;
        ops = registries.createSerializationContext(JsonOps.INSTANCE);
    }

    List<Stored> load() throws IOException {
        if (!Files.exists(file)) return List.of();
        try {
            var stored = new ArrayList<Stored>();
            for (var element : JsonParser.parseString(Files.readString(file)).getAsJsonArray()) {
                JsonObject value = element.getAsJsonObject();
                String discoverer = value.get("discoverer").getAsString();
                var discoverers = new ArrayList<String>();
                if (value.has("discoverers")) {
                    value.getAsJsonArray("discoverers").forEach(name -> discoverers.add(name.getAsString()));
                }
                var entry = new DiscoveryCollection.Entry(
                        decode(value, "first"),
                        decode(value, "second"),
                        decode(value, "result"),
                        discoverer,
                        stored.size() + 1,
                        discoverers);
                var owners = new HashSet<UUID>();
                if (value.has("players")) {
                    value.getAsJsonArray("players").forEach(id -> owners.add(UUID.fromString(id.getAsString())));
                }
                stored.add(new Stored(entry, owners, value.toString()));
            }
            return List.copyOf(stored);
        } catch (RuntimeException invalid) {
            throw new IOException("Invalid discovery collection; file left unchanged", invalid);
        }
    }

    String encode(DiscoveryCollection.Entry entry, Set<UUID> owners) {
        var value = new JsonObject();
        value.add("first", ItemStack.CODEC.encodeStart(ops, entry.first()).getOrThrow());
        value.add("second", ItemStack.CODEC.encodeStart(ops, entry.second()).getOrThrow());
        value.add("result", ItemStack.CODEC.encodeStart(ops, entry.result()).getOrThrow());
        value.addProperty("discoverer", entry.discoverer());

        var names = new JsonArray();
        entry.discoverers().forEach(names::add);
        value.add("discoverers", names);

        var players = new JsonArray();
        owners.stream().map(UUID::toString).sorted().forEach(players::add);
        value.add("players", players);
        return value.toString();
    }

    synchronized void saveAsync(List<String> snapshot, Executor worker, Consumer<IOException> onError) {
        pendingSave = snapshot;
        if (saving) return;
        saving = true;
        try {
            worker.execute(() -> drainSaves(onError));
        } catch (RuntimeException error) {
            saving = false;
            throw error;
        }
    }

    private void drainSaves(Consumer<IOException> onError) {
        while (true) {
            List<String> next;
            synchronized (this) {
                next = pendingSave;
                pendingSave = null;
                if (next == null) {
                    saving = false;
                    return;
                }
            }
            try {
                save(next);
            } catch (IOException error) {
                onError.accept(error);
            }
        }
    }

    void save(List<String> snapshot) throws IOException {
        Path directory = file.toAbsolutePath().getParent();
        Files.createDirectories(directory);
        Path temporary = Files.createTempFile(directory, "discoveries-", ".tmp");
        try {
            try (var writer = Files.newBufferedWriter(temporary)) {
                writer.write('[');
                for (int index = 0; index < snapshot.size(); index++) {
                    if (index > 0) writer.write(',');
                    writer.write(snapshot.get(index));
                }
                writer.write(']');
            }
            Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private ItemStack decode(JsonObject value, String key) {
        return ItemStack.CODEC.parse(ops, value.get(key)).getOrThrow();
    }
}
