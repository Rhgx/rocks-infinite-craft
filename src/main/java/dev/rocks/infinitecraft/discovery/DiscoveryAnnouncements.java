package dev.rocks.infinitecraft.discovery;

import dev.rocks.infinitecraft.item.ItemDataFusion;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

public final class DiscoveryAnnouncements {
    private DiscoveryAnnouncements() {
    }

    public static Component discoveryMessage(ItemStack output, String discoverer) {
        return discoveryMessage(output, discoverer, ItemDataFusion.specialIngredient(output));
    }

    public static Component discoveryMessage(ItemStack output, String discoverer, boolean special) {
        return Component.empty()
                .append(Component.literal(special ? "✧ " : "✦ ")
                        .withStyle(style -> style.withColor(special ? ChatFormatting.LIGHT_PURPLE : ChatFormatting.GOLD).withBold(true)))
                .append(Component.literal(discoverer).withStyle(ChatFormatting.WHITE))
                .append(Component.literal(" discovered ").withStyle(ChatFormatting.GRAY))
                .append(output.getDisplayName());
    }

    public static boolean isMilestone(int count) {
        return milestoneTier(count) >= 0;
    }

    public static int milestoneTier(int count) {
        int tier = 0;
        for (long scale = 10; scale <= count; scale *= 10) {
            if (count == scale) return tier;
            if (count == scale * 5 / 2) return tier + 1;
            if (count == scale * 5) return tier + 2;
            tier += 3;
        }
        return -1;
    }

    public static int milestoneExperience(int tier) {
        return Math.min(50 + Math.max(0, tier) * 50, 500);
    }

    public static Component milestoneMessage(int count, int tier, String discoverer) {
        var numberColor = tier >= 6 ? ChatFormatting.LIGHT_PURPLE
                : tier >= 4 ? ChatFormatting.GOLD
                : tier >= 2 ? ChatFormatting.AQUA : ChatFormatting.GREEN;
        var textColor = tier >= 6 ? ChatFormatting.GOLD
                : tier >= 4 ? ChatFormatting.YELLOW : ChatFormatting.GRAY;
        boolean bold = tier >= 6;
        // The count is the world total; kept short so the finder's XP suffix fits on one chat line.
        return Component.literal("★ Discovery ").withStyle(style -> style.withColor(textColor).withBold(bold))
                .append(Component.literal("#" + count).withStyle(style -> style.withColor(numberColor).withBold(true)))
                .append(Component.literal(" by " + discoverer).withStyle(style -> style
                        .withColor(ChatFormatting.GRAY).withBold(false)));
    }

}
