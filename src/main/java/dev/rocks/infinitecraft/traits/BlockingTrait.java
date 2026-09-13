package dev.rocks.infinitecraft.traits;

import java.util.Set;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class BlockingTrait implements TraitDefinition {
    @Override
    public String id() {
        return "blocking";
    }

    @Override
    public String hint() {
        return "Protective";
    }

    @Override
    public Set<DataComponentType<?>> components() {
        return Set.of(DataComponents.BLOCKS_ATTACKS, DataComponents.MAX_DAMAGE, DataComponents.DAMAGE);
    }

    @Override
    public boolean supports(ItemStack base) {
        return TraitComponents.supportsUse(base) && !base.has(DataComponents.CONSUMABLE);
    }

    @Override
    public boolean validResult(ItemStack result) {
        if (!result.has(DataComponents.BLOCKS_ATTACKS)) {
            return true;
        }
        if (result.has(DataComponents.CONSUMABLE)) {
            return false;
        }
        return !TraitComponents.changed(result, DataComponents.BLOCKS_ATTACKS) || TraitComponents.supportsUse(result);
    }

    @Override
    public void apply(ItemStack output, double value) {
        var shield = new ItemStack(Items.SHIELD).get(DataComponents.BLOCKS_ATTACKS);
        if (shield == null) {
            throw new IllegalArgumentException("Shield blocking component is unavailable");
        }
        output.set(DataComponents.BLOCKS_ATTACKS, shield);
        TraitComponents.ensureDurability(output, 336);
    }
}
