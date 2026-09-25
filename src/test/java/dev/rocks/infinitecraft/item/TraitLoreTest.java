package dev.rocks.infinitecraft.item;

import net.minecraft.SharedConstants;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TraitLoreTest {
    @Test void olderHintLinesTakeTheCurrentIconAndColor() {
        var stack = new ItemStack(Items.BREAD);
        stack.set(DataComponents.LORE, new ItemLore(List.of(Component.literal("♥ Hearty"),
                Component.literal("Edible"), Component.literal("Custom text"))));
        TraitLore.add(stack, List.of("hearty_food"), Map.of("hearty_food", "consumed"));
        var lines = stack.get(DataComponents.LORE).lines();
        assertEquals(List.of("✚ Hearty", TraitLore.EDIBLE, "Custom text"),
                lines.stream().map(Component::getString).toList());
        assertEquals(TraitLore.EDIBLE_COLOR, lines.get(1).getStyle().getColor().getValue());
    }

    @BeforeAll static void bootstrap() {
        SharedConstants.tryDetectVersion(); Bootstrap.bootStrap();
    }
}
