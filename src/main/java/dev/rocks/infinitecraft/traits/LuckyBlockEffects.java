package dev.rocks.infinitecraft.traits;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.game.ClientboundSetPlayerTeamPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.TeamColor;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Temporary vanilla-client visuals, without changing the server's real scoreboard teams. */
final class LuckyBlockEffects {
    private static final TeamColor[] COLORS = {
            TeamColor.RED, TeamColor.GOLD, TeamColor.YELLOW,
            TeamColor.GREEN, TeamColor.AQUA, TeamColor.LIGHT_PURPLE
    };
    private static final Map<ServerPlayer, Integer> ROCKETS = new IdentityHashMap<>();
    private static final Map<ServerPlayer, Glow> GLOWS = new IdentityHashMap<>();

    private static final class Glow {
        final PlayerTeam team;
        final Set<ServerPlayer> viewers = Collections.newSetFromMap(new IdentityHashMap<>());
        int until;

        Glow(ServerPlayer player) {
            // An isolated scoreboard means setters cannot broadcast or mutate actual teams.
            team = new PlayerTeam(new Scoreboard(), "ric_star_" + player.getUUID());
            team.getPlayers().add(player.getScoreboardName());
        }
    }

    private LuckyBlockEffects() {}

    static void initialize() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            ROCKETS.entrySet().removeIf(entry -> {
                var player = entry.getKey();
                if (!player.isAlive() || player.tickCount >= entry.getValue()
                        || server.getPlayerList().getPlayer(player.getUUID()) != player) return true;
                if (player.tickCount % 2 == 0) {
                    player.level().sendParticles(ParticleTypes.FIREWORK,
                            player.getX(), player.getY() + .1, player.getZ(), 4, .15, .04, .15, .025);
                }
                return false;
            });
            GLOWS.entrySet().removeIf(entry -> {
                var player = entry.getKey();
                var glow = entry.getValue();
                if (!player.isAlive() || player.tickCount >= glow.until
                        || server.getPlayerList().getPlayer(player.getUUID()) != player) {
                    for (var viewer : glow.viewers) {
                        if (server.getPlayerList().getPlayer(viewer.getUUID()) != viewer) continue;
                        viewer.connection.send(ClientboundSetPlayerTeamPacket.createRemovePacket(glow.team));
                        var actual = player.getTeam();
                        if (actual != null) viewer.connection.send(ClientboundSetPlayerTeamPacket.createPlayerPacket(
                                actual, player.getScoreboardName(), ClientboundSetPlayerTeamPacket.Action.ADD));
                    }
                    return true;
                }
                if (player.tickCount % 4 != 0) return false;
                copySettings(player.getTeam(), glow.team);
                glow.team.setColor(Optional.of(COLORS[player.tickCount / 4 % COLORS.length]));
                glow.viewers.removeIf(viewer -> server.getPlayerList().getPlayer(viewer.getUUID()) != viewer);
                for (var viewer : server.getPlayerList().getPlayers()) {
                    boolean added = glow.viewers.add(viewer);
                    viewer.connection.send(ClientboundSetPlayerTeamPacket.createAddOrModifyPacket(glow.team, added));
                    // Reassert membership if a real scoreboard update arrived during the effect.
                    if (!added) viewer.connection.send(ClientboundSetPlayerTeamPacket.createPlayerPacket(
                            glow.team, player.getScoreboardName(), ClientboundSetPlayerTeamPacket.Action.ADD));
                }
                return false;
            });
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            ROCKETS.clear();
            GLOWS.clear();
        });
    }

    static void rocket(ServerPlayer player) {
        ROCKETS.put(player, player.tickCount + 60);
    }

    static void rainbow(ServerPlayer player) {
        GLOWS.computeIfAbsent(player, Glow::new).until = player.tickCount + 600;
    }

    static void copySettings(PlayerTeam original, PlayerTeam display) {
        var source = original == null ? new PlayerTeam(new Scoreboard(), "defaults") : original;
        display.setPlayerPrefix(source.getPlayerPrefix());
        display.setPlayerSuffix(source.getPlayerSuffix());
        display.setNameTagVisibility(source.getNameTagVisibility());
        display.setDeathMessageVisibility(source.getDeathMessageVisibility());
        display.setCollisionRule(source.getCollisionRule());
        display.setAllowFriendlyFire(source.isAllowFriendlyFire());
        display.setSeeFriendlyInvisibles(source.canSeeFriendlyInvisibles());
    }
}
