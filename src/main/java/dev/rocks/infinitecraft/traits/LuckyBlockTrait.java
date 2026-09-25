package dev.rocks.infinitecraft.traits;

import dev.rocks.infinitecraft.item.ItemTraits;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.IdentityHashMap;

/** A server-side, single-use roll that works with ordinary vanilla item stacks. */
public final class LuckyBlockTrait implements TraitDefinition {
    public static final String ID = "lucky_block";
    private static final Map<ServerPlayer, Integer> IMMORTAL_UNTIL = new IdentityHashMap<>();
    private static final Map<ServerPlayer, Integer> LAST_USE = new IdentityHashMap<>();
    private static final ChatFormatting[] RAINBOW = {ChatFormatting.RED, ChatFormatting.GOLD, ChatFormatting.YELLOW,
            ChatFormatting.GREEN, ChatFormatting.AQUA, ChatFormatting.BLUE, ChatFormatting.LIGHT_PURPLE};

    @Override public String id() { return ID; }
    @Override public String hint() { return "♦ Lucky Block · Single use"; }
    @Override public int hintColor() { return 0xFFAA00; }
    @Override public String description() { return "Right-click to consume it and roll a random blessing or curse. Appears on 5% of recipes."; }
    @Override public Set<DataComponentType<?>> components() { return Set.of(); }
    @Override public void apply(ItemStack output, double value) {}

    /** Stable across launches, input order and candidate validation. Never reroll cached recipes. */
    public static boolean appears(String pair, long seed) {
        return Math.floorMod(UUID.nameUUIDFromBytes((seed + ":lucky_block:" + pair)
                .getBytes(StandardCharsets.UTF_8)).getLeastSignificantBits(), 100) < 5;
    }

    public static void initialize() {
        LuckyBlockEffects.initialize();
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (entity instanceof ServerPlayer player) forget(player);
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> forget(handler.player));
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            LAST_USE.clear();
            IMMORTAL_UNTIL.clear();
        });
        UseItemCallback.EVENT.register((player, level, hand) -> use(player, hand));
        UseBlockCallback.EVENT.register((player, level, hand, hit) -> use(player, hand));
        UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> use(player, hand));
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            if (!(entity instanceof ServerPlayer player)) return true;
            Integer until = IMMORTAL_UNTIL.get(player);
            if (until == null) return true;
            if (player.tickCount < until) return false;
            IMMORTAL_UNTIL.remove(player);
            return true;
        });
    }

    private static InteractionResult use(Player player, InteractionHand hand) {
        if (player.isSpectator()) return InteractionResult.PASS;
        ItemStack stack = player.getItemInHand(hand);
        if (!ItemTraits.inherited(stack).contains(ID)) return InteractionResult.PASS;
        if (!(player instanceof ServerPlayer serverPlayer)) return InteractionResult.SUCCESS;
        // Block/item packets or the other hand must not roll twice for one click.
        if (coolingDown(serverPlayer.tickCount, LAST_USE.get(serverPlayer))) return InteractionResult.SUCCESS_SERVER;
        LAST_USE.put(serverPlayer, serverPlayer.tickCount);
        stack.shrink(1);
        int outcome = outcome(serverPlayer.getRandom().nextInt(100));
        serverPlayer.sendSystemMessage(message(outcome, "♦ " + apply(serverPlayer, outcome)), true);
        serverPlayer.inventoryMenu.sendAllDataToRemote();
        return InteractionResult.SUCCESS_SERVER;
    }

    private static void forget(ServerPlayer player) {
        LAST_USE.remove(player);
        IMMORTAL_UNTIL.remove(player);
    }

    static boolean coolingDown(int tick, Integer previous) {
        // A reset player clock must never turn the brief duplicate-click guard into a lockout.
        return previous != null && tick >= previous && (long) tick - previous < 5;
    }

    /** Immortal and Unlucky get 4% each; the other eleven outcomes share the remaining 92%. */
    static int outcome(int roll) {
        if (roll < 0 || roll >= 100) throw new IllegalArgumentException("Roll must be 0 to 99");
        return roll < 4 ? 0 : roll < 8 ? 1 : 2 + (roll - 8) * 11 / 92;
    }

    /** Immortal is spelled out in rainbow colors, one per character, Unlucky in dark red, the rest in gold. */
    static Component message(int outcome, String text) {
        if (outcome == 1) return Component.literal(text).withStyle(ChatFormatting.DARK_RED);
        if (outcome != 0) return Component.literal(text).withStyle(ChatFormatting.GOLD);
        var message = Component.empty();
        int colored = 0;
        for (int i = 0; i < text.length(); i = text.offsetByCodePoints(i, 1)) {
            String character = text.substring(i, text.offsetByCodePoints(i, 1));
            // Spaces take no color, so the visible characters step through the rainbow evenly.
            message.append(character.isBlank() ? Component.literal(character)
                    : Component.literal(character).withStyle(RAINBOW[colored++ % RAINBOW.length]));
        }
        return message;
    }

    private static String apply(ServerPlayer player, int outcome) {
        return switch (outcome) {
            case 0 -> {
                IMMORTAL_UNTIL.put(player, player.tickCount + 600);
                effect(player, MobEffects.GLOWING, 30, 0);
                LuckyBlockEffects.starPower(player);
                yield "Immortal! 30 seconds.";
            }
            case 1 -> {
                IMMORTAL_UNTIL.remove(player);
                player.kill(player.level());
                yield "Unlucky.";
            }
            case 2 -> {
                player.heal(player.getMaxHealth());
                player.getFoodData().eat(20, 1);
                yield "Fresh start.";
            }
            case 3 -> { effect(player, MobEffects.STRENGTH, 30, 2); yield "Heavy hitter."; }
            case 4 -> { effect(player, MobEffects.SPEED, 30, 2); yield "Gotta go fast."; }
            case 5 -> { effect(player, MobEffects.INVISIBILITY, 30, 0); yield "Now you see me..."; }
            case 6 -> {
                var diamonds = new ItemStack(Items.DIAMOND, 3);
                if (!player.getInventory().add(diamonds)) player.drop(diamonds, false);
                yield "Jackpot!";
            }
            case 7 -> { player.giveExperiencePoints(100); yield "Enlightened."; }
            case 8 -> { effect(player, MobEffects.LEVITATION, 8, 1); yield "Going up."; }
            case 9 -> { effect(player, MobEffects.POISON, 15, 1); yield "Bad aftertaste."; }
            case 10 -> { effect(player, MobEffects.BLINDNESS, 15, 0); yield "Lights out."; }
            case 11 -> { effect(player, MobEffects.SLOWNESS, 20, 3); yield "Heavy feet."; }
            default -> {
                effect(player, MobEffects.LEVITATION, 3, 19);
                LuckyBlockEffects.rocket(player);
                yield "Liftoff!";
            }
        };
    }

    private static void effect(ServerPlayer player, Holder<MobEffect> effect, int seconds, int amplifier) {
        player.addEffect(new MobEffectInstance(effect, seconds * 20, amplifier));
    }
}
