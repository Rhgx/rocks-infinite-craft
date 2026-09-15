package dev.rocks.infinitecraft.traits;

import dev.rocks.infinitecraft.item.ItemTraits;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.WeakHashMap;

/** Server-side effects for traits that react to a successful melee hit. */
public final class CombatTraits {
    private static final int DAMAGE_WINDOW_TICKS = 400;
    private static final Map<ServerPlayer, CombatHistory> HISTORY = new WeakHashMap<>();
    private static boolean applyingAreaDamage;

    private record Hit(int tick, float damage) {
    }

    private static final class CombatHistory {
        final Deque<Hit> hits = new ArrayDeque<>();
        int lastExplosionTick = -1;
    }

    private CombatTraits() {
    }

    public static void initialize() {
        ServerLivingEntityEvents.AFTER_DAMAGE.register(CombatTraits::afterDamage);
        ServerLivingEntityEvents.AFTER_DEATH.register((target, source) ->
                applyTraits(target, source, Math.min(target.getMaxHealth(), 8)));
    }

    private static void afterDamage(LivingEntity target, DamageSource source,
            float baseDamage, float damage, boolean blocked) {
        if (!blocked && damage > 0) applyTraits(target, source, damage);
    }

    private static void applyTraits(LivingEntity target, DamageSource source, float damage) {
        if (applyingAreaDamage || !(source.getEntity() instanceof ServerPlayer attacker)
                || source.getDirectEntity() != attacker || !(target.level() instanceof ServerLevel level)) return;

        var traits = ItemTraits.inherited(attacker.getItemInHand(InteractionHand.MAIN_HAND));
        if (traits.contains("incendiary")) target.igniteForSeconds(4);
        if (traits.contains("vampiric")) heal(level, attacker, damage);

        int tick = level.getServer().getTickCount();
        CombatHistory history = HISTORY.computeIfAbsent(attacker, ignored -> new CombatHistory());
        history.hits.addLast(new Hit(tick, damage));
        while (!history.hits.isEmpty() && history.hits.getFirst().tick() < tick - DAMAGE_WINDOW_TICKS) {
            history.hits.removeFirst();
        }
        float recentDamage = (float) history.hits.stream().mapToDouble(Hit::damage).sum();
        if (traits.contains("explosive") && history.lastExplosionTick != tick
                && level.getRandom().nextFloat() < explosionChance(recentDamage)) {
            history.lastExplosionTick = tick;
            explode(level, attacker, target);
        }
    }

    static float explosionChance(float recentDamage) {
        return 0.1F * (1 + 4 * Math.clamp(recentDamage / 800F, 0, 1));
    }

    private static void heal(ServerLevel level, ServerPlayer attacker, float damage) {
        float amount = Math.min(attacker.getMaxHealth() - attacker.getHealth(), Math.clamp(damage * 0.25F, 1, 4));
        if (amount <= 0) return;
        attacker.heal(amount);
        level.sendParticles(ParticleTypes.HEART, attacker.getX(), attacker.getY() + 1, attacker.getZ(),
                2, 0.25, 0.25, 0.25, 0);
    }

    private static void explode(ServerLevel level, ServerPlayer attacker, LivingEntity target) {
        Vec3 center = target.position().add(0, target.getBbHeight() * 0.5, 0);
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, center.x, center.y, center.z, 1, 0, 0, 0, 0);
        level.playSound(null, center.x, center.y, center.z, SoundEvents.GENERIC_EXPLODE,
                SoundSource.PLAYERS, 0.8F, 1.1F);

        applyingAreaDamage = true;
        try {
            for (var nearby : level.getEntitiesOfClass(LivingEntity.class, target.getBoundingBox().inflate(3))) {
                if (nearby == attacker) continue;
                double distance = Math.sqrt(nearby.distanceToSqr(center));
                float damage = (float) Math.max(0, 6 * (1 - distance / 3));
                if (damage > 0) nearby.hurtServer(level, level.damageSources().playerAttack(attacker), damage);
            }
        } finally {
            applyingAreaDamage = false;
        }
    }
}
