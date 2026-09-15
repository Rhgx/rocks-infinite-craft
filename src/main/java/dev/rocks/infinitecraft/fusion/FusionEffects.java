package dev.rocks.infinitecraft.fusion;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class FusionEffects {
    private FusionEffects() {
    }

    public static void showSpecialParticles(ServerLevel world, Vec3 center) {
        for (int i = 0; i < 8; i++) {
            double angle = i * Math.PI / 2;
            sendParticleOutsideBlocks(world, i % 2 == 0 ? ParticleTypes.WITCH : ParticleTypes.END_ROD,
                    center.x + Math.cos(angle) * .3, center.y + .4 + i * .08,
                    center.z + Math.sin(angle) * .3);
        }
    }

    public static void showResultParticles(ServerLevel world, Vec3 center, boolean success) {
        // Different native textures and motion distinguish outcomes without colored dust.
        var particle = success ? ParticleTypes.HAPPY_VILLAGER : ParticleTypes.SMOKE;
        int count = success ? 4 : 3;
        for (int i = 0; i < count; i++) {
            double angle = 2 * Math.PI * i / count;
            sendParticleOutsideBlocks(world, particle, center.x + Math.cos(angle) * .2,
                    center.y + .5 + (success ? (i % 2) * .15 : 0), center.z + Math.sin(angle) * .2);
        }
    }

    public static void sendParticleOutsideBlocks(ServerLevel world, ParticleOptions particle,
            double x, double y, double z) {
        // Keep spawn positions outside blocks, with room for the visible particle. No downward launch velocity.
        if (world.noBlockCollision(null, new AABB(x - .15, y - .15, z - .15,
                x + .15, y + .15, z + .15)))
            world.sendParticles(particle, x, y, z, 0, 0, 0, 0, 0);
    }

}
