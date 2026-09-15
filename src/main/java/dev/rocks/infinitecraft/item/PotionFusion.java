package dev.rocks.infinitecraft.item;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.component.SuspiciousStewEffects;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/** Potion and stew effects share vanilla's consumable component listeners. */
public final class PotionFusion {
    private PotionFusion() {
    }

    public static List<List<String>> describe(ItemStack first, ItemStack second) {
        var descriptions = new ArrayList<List<String>>();
        for (var stack : List.of(first, second)) {
            var effects = new ArrayList<String>();
            stack.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY).getAllEffects()
                    .forEach(effect -> effects.add(describe(effect)));
            stack.getOrDefault(DataComponents.SUSPICIOUS_STEW_EFFECTS, SuspiciousStewEffects.EMPTY).effects()
                    .forEach(effect -> effects.add(describe(effect.createEffectInstance())));
            var contents = stack.get(DataComponents.POTION_CONTENTS);
            if (effects.isEmpty() && contents != null) effects.add(contents.potion()
                    .map(potion -> "Base potion: " + BuiltInRegistries.POTION.getKey(potion.value())).orElse("Empty potion") + "; no effects");
            descriptions.add(List.copyOf(effects));
        }
        return descriptions.stream().allMatch(List::isEmpty) ? List.of() : List.copyOf(descriptions);
    }

    public static Map<String, List<String>> options() {
        Map<String, List<String>> options = new TreeMap<>();
        for (var potion : BuiltInRegistries.POTION) {
            if (!potion.getEffects().isEmpty()) options.put(BuiltInRegistries.POTION.getKey(potion).toString(),
                    potion.getEffects().stream().map(PotionFusion::describe).toList());
        }
        return Map.copyOf(options);
    }

    public static boolean applyChoice(ItemStack result, String choice) {
        if (choice.isEmpty()) return true;
        if (!result.has(DataComponents.POTION_CONTENTS) && !result.is(Items.SUSPICIOUS_STEW)) return false;
        var id = Identifier.tryParse(choice);
        var potion = id == null ? null : BuiltInRegistries.POTION.get(id).orElse(null);
        if (potion == null || potion.value().getEffects().isEmpty()) return false;
        var contents = result.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY);
        var effects = new TreeMap<String, MobEffectInstance>();
        contents.getAllEffects().forEach(effect -> add(effects, effect));
        potion.value().getEffects().forEach(effect -> add(effects, effect));
        result.set(DataComponents.POTION_CONTENTS, new PotionContents(Optional.empty(), contents.customColor(),
                List.copyOf(effects.values()), contents.customName()));
        return true;
    }

    private static String describe(MobEffectInstance effect) {
        return BuiltInRegistries.MOB_EFFECT.getKey(effect.getEffect().value()) + " level " + (effect.getAmplifier() + 1)
                + (effect.isInfiniteDuration() ? ", infinite" : ", " + effect.getDuration() + " ticks");
    }

    public static boolean merge(ItemStack result, ItemStack first, ItemStack second) {
        var a = first.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY);
        var b = second.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY);
        var stewA = first.getOrDefault(DataComponents.SUSPICIOUS_STEW_EFFECTS, SuspiciousStewEffects.EMPTY);
        var stewB = second.getOrDefault(DataComponents.SUSPICIOUS_STEW_EFFECTS, SuspiciousStewEffects.EMPTY);
        if (a.equals(PotionContents.EMPTY) && b.equals(PotionContents.EMPTY)
                && stewA.effects().isEmpty() && stewB.effects().isEmpty()) return true;
        if (!result.has(DataComponents.POTION_CONTENTS) && !result.is(Items.SUSPICIOUS_STEW)) return false;

        PotionContents contents;
        if (stewA.effects().isEmpty() && stewB.effects().isEmpty()
                && (a.equals(b) || a.equals(PotionContents.EMPTY) || b.equals(PotionContents.EMPTY))) {
            contents = a.equals(PotionContents.EMPTY) ? b : a;
        } else {
            Map<String, MobEffectInstance> effects = new TreeMap<>();
            a.getAllEffects().forEach(effect -> add(effects, effect));
            b.getAllEffects().forEach(effect -> add(effects, effect));
            stewA.effects().forEach(effect -> add(effects, effect.createEffectInstance()));
            stewB.effects().forEach(effect -> add(effects, effect.createEffectInstance()));
            contents = new PotionContents(Optional.empty(), shared(a.customColor(), b.customColor()),
                    List.copyOf(effects.values()), shared(a.customName(), b.customName()));
        }
        result.set(DataComponents.POTION_CONTENTS, contents);
        // Stew entries cannot carry amplifiers. Store the combined effects in potion contents instead,
        // which vanilla also applies when eating stew, and clear the old entries to avoid double application.
        if (result.is(Items.SUSPICIOUS_STEW)) result.set(DataComponents.SUSPICIOUS_STEW_EFFECTS, SuspiciousStewEffects.EMPTY);
        return true;
    }

    private static <T> Optional<T> shared(Optional<T> a, Optional<T> b) {
        return a.isEmpty() ? b : b.isEmpty() || a.equals(b) ? a : Optional.empty();
    }

    private static void add(Map<String, MobEffectInstance> effects, MobEffectInstance incoming) {
        String id = BuiltInRegistries.MOB_EFFECT.getKey(incoming.getEffect().value()).toString();
        var previous = effects.get(id);
        if (previous == null || incoming.getAmplifier() > previous.getAmplifier()) {
            effects.put(id, new MobEffectInstance(incoming));
        } else if (incoming.getAmplifier() == previous.getAmplifier()) {
            int duration = incoming.isInfiniteDuration() || previous.isInfiniteDuration() ? MobEffectInstance.INFINITE_DURATION
                    : Math.max(incoming.getDuration(), previous.getDuration());
            effects.put(id, new MobEffectInstance(incoming.getEffect(), duration, incoming.getAmplifier(),
                    incoming.isAmbient() && previous.isAmbient(), incoming.isVisible() || previous.isVisible(),
                    incoming.showIcon() || previous.showIcon()));
        }
    }
}
