package dev.rocks.infinitecraft.fusion;

import dev.rocks.infinitecraft.InfiniteCraftMod;
import net.minecraft.world.entity.item.ItemEntity;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

/** Transient intent belongs to the dropped entity, never to the item's components. */
public final class FusionDrops {
    private static final Set<ItemEntity> SINGLES = Collections.newSetFromMap(new WeakHashMap<>());

    private FusionDrops() {}

    public static void mark(ItemEntity entity, boolean random, boolean trace) {
        if (entity != null && !random && trace && InfiniteCraftMod.groundFusionEnabled()
                && entity.getItem().getCount() == 1) SINGLES.add(entity);
    }

    public static boolean intentional(ItemEntity entity) {
        return entity.level() instanceof net.minecraft.server.level.ServerLevel
                && InfiniteCraftMod.groundFusionEnabled() && entity.getItem().getCount() == 1 && SINGLES.contains(entity);
    }

    public static void clear() { SINGLES.clear(); }
}
