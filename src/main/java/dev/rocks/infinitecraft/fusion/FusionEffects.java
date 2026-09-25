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

    /** Two sparks orbit above a working crafter while enchant glyphs are drawn into its top face. */
    public static void showCrafterWorking(ServerLevel world, Vec3 top, int tick) {
        if (tick % 4 == 0) {
            for (int side = 0; side < 2; side++) {
                double angle = tick * .16 + side * Math.PI;
                sendParticleOutsideBlocks(world, ParticleTypes.ELECTRIC_SPARK,
                        top.x + Math.cos(angle) * .3, top.y + .2, top.z + Math.sin(angle) * .3);
            }
        }
        if (tick % 3 == 0) {
            // Enchant glyphs start at spawn + motion and fly back while sinking 1.2 blocks, landing on the top face.
            double angle = world.getRandom().nextDouble() * 2 * Math.PI;
            sendParticleOutsideBlocks(world, ParticleTypes.ENCHANT, top.x, top.y + 1.2, top.z,
                    Math.cos(angle), -.4, Math.sin(angle));
        }
    }

    public static void showCrafterResult(ServerLevel world, Vec3 top, boolean success, boolean special) {
        if (!success) {
            // The machine chokes: smoke spills off the top and a storm cloud hangs over it.
            for (int i = 0; i < 6; i++) {
                double angle = i * Math.PI / 3;
                sendParticleOutsideBlocks(world, ParticleTypes.SMOKE, top.x + Math.cos(angle) * .2, top.y + .15,
                        top.z + Math.sin(angle) * .2, Math.cos(angle) * .03, .02, Math.sin(angle) * .03);
            }
            sendParticleOutsideBlocks(world, ParticleTypes.ANGRY_VILLAGER, top.x, top.y + .6, top.z);
            return;
        }
        // A ring of light expands off the top face.
        int ring = special ? 12 : 8;
        for (int i = 0; i < ring; i++) {
            double angle = 2 * Math.PI * i / ring;
            sendParticleOutsideBlocks(world, ParticleTypes.END_ROD, top.x + Math.cos(angle) * .2, top.y + .2,
                    top.z + Math.sin(angle) * .2, Math.cos(angle) * .08, .01, Math.sin(angle) * .08);
        }
        if (!special) {
            sendParticleOutsideBlocks(world, ParticleTypes.HAPPY_VILLAGER, top.x, top.y + .5, top.z);
            return;
        }
        // Special results also get a fountain of totem confetti.
        var random = world.getRandom();
        for (int i = 0; i < 12; i++)
            sendParticleOutsideBlocks(world, ParticleTypes.TOTEM_OF_UNDYING, top.x, top.y + .3, top.z,
                    random.nextGaussian() * .08, .35 + random.nextDouble() * .15, random.nextGaussian() * .08);
    }

    public static void sendParticleOutsideBlocks(ServerLevel world, ParticleOptions particle,
            double x, double y, double z) {
        sendParticleOutsideBlocks(world, particle, x, y, z, 0, 0, 0);
    }

    public static void sendParticleOutsideBlocks(ServerLevel world, ParticleOptions particle,
            double x, double y, double z, double dx, double dy, double dz) {
        // Keep spawn positions outside blocks, with room for the visible particle.
        // A zero count makes the client treat the offsets as the particle's motion.
        if (world.noBlockCollision(null, new AABB(x - .15, y - .15, z - .15,
                x + .15, y + .15, z + .15)))
            world.sendParticles(particle, x, y, z, 0, dx, dy, dz, 1);
    }

}
