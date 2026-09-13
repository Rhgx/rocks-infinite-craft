package dev.rocks.infinitecraft.traits;

import net.minecraft.world.item.consume_effects.ConsumeEffect;
import net.minecraft.world.item.consume_effects.TeleportRandomlyConsumeEffect;

public final class TeleportFoodTrait extends FoodTrait {
    @Override
    public String id() {
        return "edible_teleport";
    }

    @Override
    public StrengthRange range() {
        return new StrengthRange(2, 16, 8);
    }

    @Override
    protected ConsumeEffect effect(double value) {
        return new TeleportRandomlyConsumeEffect((float) value);
    }

    @Override
    protected boolean replaces(ConsumeEffect effect) {
        return effect instanceof TeleportRandomlyConsumeEffect;
    }
}
