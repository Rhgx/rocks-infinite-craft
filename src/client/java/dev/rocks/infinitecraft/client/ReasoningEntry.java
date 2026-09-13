package dev.rocks.infinitecraft.client;

import dev.rocks.infinitecraft.provider.ProviderConfig;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;
import net.minecraft.client.gui.GuiGraphicsExtractor;

final class ReasoningEntry extends SelectionEntry<String> {
    private final Supplier<List<String>> supported;
    private List<String> options = List.of();

    ReasoningEntry(String initial, Supplier<List<String>> supported) {
        super("Reasoning", initial, "low", List.of(), value ->
                value.equals("xhigh") ? "Extra high" : value.substring(0, 1).toUpperCase(Locale.ROOT) + value.substring(1));
        this.supported = supported;
    }

    private void refresh() {
        var levels = supported.get();
        // Custom IDs can use the CLI's effort values when no catalog entry is available.
        var available = new ArrayList<>(levels == null ? ProviderConfig.REASONING_LEVELS : levels);
        available.add("default");
        if (!available.equals(options)) {
            options = List.copyOf(available);
            setOptions(options);
            if (!options.contains(super.getValue())) setValue(options.contains("low") ? "low" : "default");
        }
    }

    @Override public String getValue() { refresh(); return super.getValue(); }

    @Override public void extractRenderState(GuiGraphicsExtractor graphics, int index, int y, int x,
            int width, int height, int mouseX, int mouseY, boolean hovered, float delta) {
        refresh();
        super.extractRenderState(graphics, index, y, x, width, height, mouseX, mouseY, hovered, delta);
    }
}
