package dev.rocks.infinitecraft.client;

import dev.rocks.infinitecraft.provider.OllamaModels;
import dev.rocks.infinitecraft.provider.CodexModels;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;

/** Installed models are choices; custom IDs are entered in a separate field. */
final class ModelDropdownEntry extends SelectionEntry<String> {
    private final Supplier<String> ollamaUrl;
    private final Supplier<ProviderEntry.Provider> provider;
    private final Supplier<String> customValue;
    private final java.util.Map<ProviderEntry.Provider, String> selections = new java.util.EnumMap<>(ProviderEntry.Provider.class);
    private ProviderEntry.Provider selectedProvider;
    private String endpoint;
    private List<CodexModels.Model> codexModels = List.of();
    private long changedAt;
    private long revision;
    private boolean requested;
    private String status = "Choose a model or Custom...";

    ModelDropdownEntry(String initial, Supplier<ProviderEntry.Provider> provider, Supplier<String> ollamaUrl, Supplier<String> customValue) {
        super("Model", initial, "", initial.isEmpty() ? List.of("") : List.of(initial, ""),
                value -> value.isEmpty() ? "Custom..." : value);
        this.ollamaUrl = ollamaUrl;
        this.provider = provider;
        this.customValue = customValue;
        this.selectedProvider = provider.get();
    }

    boolean hasCatalog() {
        syncProvider();
        return selectedProvider == ProviderEntry.Provider.OLLAMA || selectedProvider == ProviderEntry.Provider.CODEX;
    }

    private void syncProvider() {
        var current = provider.get();
        if (current == selectedProvider) return;
        selections.put(selectedProvider, super.getValue());
        selectedProvider = current;
        setValue(selections.getOrDefault(current, ""));
        endpoint = null;
        revision++;
        requested = false;
        codexModels = List.of();
        models(List.of());
    }

    boolean isCustom() {
        return !hasCatalog() || super.getValue().isEmpty();
    }

    List<String> reasoningLevels() {
        String selected = getValue();
        return codexModels.stream().filter(model -> selected.isEmpty() ? model.isDefault() : model.id().equals(selected))
                .findFirst().map(CodexModels.Model::reasoningLevels).orElse(null);
    }

    @Override public String getValue() {
        return isCustom() ? customValue.get() : super.getValue();
    }

    @Override protected Tooltip tooltip() {
        return Tooltip.create(Component.literal(status));
    }

    private void models(List<String> discovered) {
        var options = new ArrayList<>(discovered);
        String selected = super.getValue();
        if (!selected.isEmpty() && !options.contains(selected)) options.addFirst(selected);
        options.add("");
        setOptions(options);
    }

    @Override public void extractRenderState(GuiGraphicsExtractor graphics, int index, int y, int x,
            int width, int height, int mouseX, int mouseY, boolean hovered, float delta) {
        boolean codex = provider.get() == ProviderEntry.Provider.CODEX;
        syncProvider();
        String current = codex ? "codex-cli" : ollamaUrl.get();
        if (!Objects.equals(current, endpoint)) {
            endpoint = current;
            revision++;
            changedAt = System.nanoTime();
            requested = false;
            models(List.of());
            codexModels = List.of();
            status = current == null ? "Choose Custom to enter a model ID." : "Loading models...";
        }
        if (current != null && !requested && System.nanoTime() - changedAt >= 400_000_000L) {
            requested = true;
            String query = current;
            long requestRevision = revision;
            var requestProvider = selectedProvider;
            Thread.startVirtualThread(() -> {
                List<String> found;
                String message;
                List<CodexModels.Model> catalog = List.of();
                try {
                    if (codex) {
                        catalog = CodexModels.fetch();
                        found = catalog.stream().map(CodexModels.Model::id).toList();
                    } else found = OllamaModels.fetch(query);
                    message = found.isEmpty() ? "No models found. Choose Custom to enter an ID." : "Choose a model or Custom...";
                } catch (Exception error) {
                    if (error instanceof InterruptedException) Thread.currentThread().interrupt();
                    found = List.of();
                    message = "Couldn't load models. Choose Custom to enter an ID.";
                }
                var result = found;
                var detail = message;
                var metadata = catalog;
                Minecraft.getInstance().execute(() -> {
                    if (requestRevision != revision || provider.get() != requestProvider
                            || !Objects.equals(query, codex ? "codex-cli" : ollamaUrl.get())) return;
                    models(result);
                    codexModels = metadata;
                    status = detail;
                });
            });
        }
        super.extractRenderState(graphics, index, y, x, width, height, mouseX, mouseY, hovered, delta);
    }
}
