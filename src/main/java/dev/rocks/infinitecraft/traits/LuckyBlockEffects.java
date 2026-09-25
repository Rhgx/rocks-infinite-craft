package dev.rocks.infinitecraft.traits;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.game.ClientboundSetPlayerTeamPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.TeamColor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
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
    // Heard within 32 blocks, on the Jukebox/Note Blocks volume slider.
    private static final float MUSIC_VOLUME = 2;

    record Note(Holder<SoundEvent> sound, float pitch) {}

    /** One loop of the Super Star theme as note block sounds, indexed by server tick. */
    static final List<List<Note>> STAR_THEME = starTheme();

    private static List<List<Note>> starTheme() {
        // Two bars of sixteenth notes: a chord stabbing on the beat with a lone fill note between,
        // over a walking bass. The second bar repeats the rhythm one chord lower.
        int[] chordSteps = {0, 2, 4, 7, 9, 12, 14};
        int[] fillSteps = {6, 11, 13};
        int[] bassSteps = {0, 4, 7, 12, 14};
        int[][] chords = {{65, 69, 72}, {64, 67, 71}}; // F4 A4 C5, then E4 G4 B4 (MIDI numbers)
        int[] fills = {62, 60}; // D4, then C4
        int[][] bass = {{38, 45, 50, 45, 50}, {36, 43, 48, 43, 48}}; // D2 A2 D3 A2 D3, then C2 G2 C3 G2 C3
        var ticks = new ArrayList<List<Note>>();
        for (int tick = 0; tick < 48; tick++) ticks.add(new ArrayList<>());
        for (int bar = 0; bar < 2; bar++) {
            int offset = bar * 16;
            for (int step : chordSteps) for (int note : chords[bar]) add(ticks, offset + step, SoundEvents.NOTE_BLOCK_BIT, note, 66);
            for (int step : fillSteps) add(ticks, offset + step, SoundEvents.NOTE_BLOCK_BIT, fills[bar], 66);
            for (int i = 0; i < bassSteps.length; i++) add(ticks, offset + bassSteps[i], SoundEvents.NOTE_BLOCK_BASS, bass[bar][i], 42);
        }
        return ticks.stream().map(List::copyOf).toList();
    }

    /** A sixteenth note lasts 1.5 ticks; each instrument plays its base note (F#4 or F#2) at pitch 1. */
    private static void add(List<List<Note>> ticks, int step, Holder<SoundEvent> sound, int note, int base) {
        ticks.get(step * 3 / 2).add(new Note(sound, (float) Math.pow(2, (note - base) / 12.0)));
    }

    private static final class Glow {
        final PlayerTeam team;
        final Set<ServerPlayer> viewers = Collections.newSetFromMap(new IdentityHashMap<>());
        final int start;
        int until;

        Glow(ServerPlayer player) {
            start = player.tickCount;
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
                for (var note : STAR_THEME.get(Math.floorMod(player.tickCount - glow.start, STAR_THEME.size()))) {
                    player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                            note.sound(), SoundSource.RECORDS, MUSIC_VOLUME, note.pitch());
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

    /** A rainbow glow and the Super Star theme for as long as the player is immortal. */
    static void starPower(ServerPlayer player) {
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
