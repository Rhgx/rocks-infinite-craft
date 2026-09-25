package dev.rocks.infinitecraft.traits;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;

import java.util.Set;

/** The server spends one durability point per meal; clients use the vanilla durability bar. */
public final class NibbleableTrait extends FoodTrait {
    @Override public String id() { return "nibbleable"; }
    @Override public String hint() { return "🍖 Nibbleable"; }
    @Override public String description() { return "Edible for 3 to 8 meals, spending one durability per meal."; }
    @Override public StrengthRange range() { return new StrengthRange(3, 8, 5); }
    @Override public Set<DataComponentType<?>> components() {
        return Set.of(DataComponents.FOOD, DataComponents.CONSUMABLE, DataComponents.MAX_STACK_SIZE,
                DataComponents.MAX_DAMAGE, DataComponents.DAMAGE, DataComponents.UNBREAKABLE);
    }

    @Override
    public void apply(ItemStack output, double value) {
        super.apply(output, value);
        output.remove(DataComponents.UNBREAKABLE);
        output.set(DataComponents.MAX_STACK_SIZE, 1);
        output.set(DataComponents.MAX_DAMAGE, (int) Math.clamp(Math.round(value), 3, 8));
        output.set(DataComponents.DAMAGE, Math.min(output.getDamageValue(), output.getMaxDamage() - 1));
    }

    public static void spendBite(ItemStack stack) {
        // No Unbreaking roll: the bar counts meals, not equipment wear.
        int damage = stack.getDamageValue() + 1;
        if (damage >= stack.getMaxDamage()) stack.shrink(1);
        else stack.setDamageValue(damage);
    }
}
