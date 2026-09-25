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
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ItemSupplier;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrowableItemProjectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/** Server-side effects for traits that react to melee and projectile hits. */
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
        if (!applyingAreaDamage && !blocked && damage > 0 && target.isAlive()
                && source.getEntity() instanceof LivingEntity attacker && attacker != target) {
            var applied = new HashSet<String>();
            for (var slot : List.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET)) {
                for (var id : ItemTraits.inherited(target.getItemBySlot(slot))) {
                    if (applied.add(id) && TraitRegistry.get(id) instanceof ReactiveArmorTrait trait) {
                        trait.onHit(target, attacker);
                    }
                }
            }
        }
        // Snowballs, eggs and pearls still hit when their native damage is zero.
        boolean harmlessHit = baseDamage == 0 && source.getDirectEntity() instanceof ThrowableItemProjectile;
        if (!blocked && (damage > 0 || harmlessHit)) applyTraits(target, source, damage);
    }

    private static void applyTraits(LivingEntity target, DamageSource source, float damage) {
        if (applyingAreaDamage || !(source.getEntity() instanceof ServerPlayer attacker)
                || target == attacker || !(target.level() instanceof ServerLevel level)) return;

        List<String> traits;
        if (source.getDirectEntity() == attacker) {
            traits = ItemTraits.inherited(attacker.getItemInHand(InteractionHand.MAIN_HAND));
        } else if (source.getDirectEntity() instanceof Projectile projectile) {
            traits = projectileTraits(projectile);
        } else {
            return;
        }
        if (traits.contains("incendiary") && level.getRandom().nextFloat() < TraitSettings.chance("incendiary")) target.igniteForSeconds(4);
        if (traits.contains("vampiric") && level.getRandom().nextFloat() < TraitSettings.chance("vampiric")) heal(level, attacker, damage);
        if (target.isAlive()) {
            if (traits.contains("launching") && level.getRandom().nextFloat() < TraitSettings.chance("launching")) target.addEffect(new MobEffectInstance(
                    MobEffects.LEVITATION, 20, 0), attacker);
            if (traits.contains("frostbite") && level.getRandom().nextFloat() < TraitSettings.chance("frostbite")) target.addEffect(new MobEffectInstance(
                    MobEffects.SLOWNESS, 60, 0), attacker);
            if (traits.contains("revealing") && level.getRandom().nextFloat() < TraitSettings.chance("revealing")) target.addEffect(new MobEffectInstance(
                    MobEffects.GLOWING, 100, 0), attacker);
        }

        int tick = level.getServer().getTickCount();
        CombatHistory history = HISTORY.computeIfAbsent(attacker, ignored -> new CombatHistory());
        if (damage > 0) history.hits.addLast(new Hit(tick, damage));
        while (!history.hits.isEmpty() && history.hits.getFirst().tick() < tick - DAMAGE_WINDOW_TICKS) {
            history.hits.removeFirst();
        }
        float recentDamage = (float) history.hits.stream().mapToDouble(Hit::damage).sum();
        if (traits.contains("explosive") && history.lastExplosionTick != tick
                && level.getRandom().nextFloat() < Math.min(1, explosionChance(recentDamage) * TraitSettings.chance("explosive") / .1F)) {
            history.lastExplosionTick = tick;
            explode(level, attacker, target);
        }
    }

    private static List<String> projectileTraits(Projectile projectile) {
        // These stacks belong to the projectile and survive hand changes and world reloads.
        if (projectile instanceof AbstractArrow arrow) {
            ItemStack weapon = arrow.getWeaponItem();
            return ItemTraits.inherited(arrow.getPickupItemStackOrigin(), weapon == null ? ItemStack.EMPTY : weapon);
        }
        if (projectile instanceof ItemSupplier supplied) return ItemTraits.inherited(supplied.getItem());
        return List.of();
    }

    static float explosionChance(float recentDamage) {
        return 0.1F * (1 + 4 * Math.clamp(recentDamage / 800F, 0, 1));
    }

    private static void heal(ServerLevel level, ServerPlayer attacker, float damage) {
        float amount = healingAmount(damage, attacker.getMaxHealth() - attacker.getHealth());
        if (amount <= 0) return;
        attacker.heal(amount);
        level.sendParticles(ParticleTypes.HEART, attacker.getX(), attacker.getY() + 1, attacker.getZ(),
                2, 0.25, 0.25, 0.25, 0);
    }

    static float healingAmount(float damage, float missingHealth) {
        return damage <= 0 ? 0 : Math.min(missingHealth, Math.clamp(damage * 0.25F, 1, 4));
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
