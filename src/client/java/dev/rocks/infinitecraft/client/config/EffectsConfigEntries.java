package dev.rocks.infinitecraft.client.config;

import dev.rocks.infinitecraft.ModConfig;
import me.shedaniel.clothconfig2.api.ConfigCategory;

final class EffectsConfigEntries {
    private EffectsConfigEntries() {
    }

    static void add(ConfigCategory category, ConfigEntries fields, ModConfig config) {
        fields.section(category, "Particles", "Shown above dropped items and Fusion Crafters.");
        fields.toggle(category, "While combining", config.combiningParticles, true,
                "Sparks and glyphs while a fusion is being generated.", value -> config.combiningParticles = value);
        fields.toggle(category, "On success", config.successParticles, true,
                "A burst of light when a fusion finishes.", value -> config.successParticles = value);
        fields.toggle(category, "On failure", config.failureParticles, true,
                "Smoke when a fusion fails or is blocked.", value -> config.failureParticles = value);

        fields.section(category, "Sound");
        fields.toggle(category, "Success sound", config.successSound, true,
                "A chime when a fusion finishes, or a bell for special results.", value -> config.successSound = value);
        fields.toggle(category, "Failure sound", config.failureSound, true,
                "A low note when a fusion fails.", value -> config.failureSound = value);

        fields.section(category, "Messages", "Chat and action bar messages.");
        fields.toggle(category, "Join message", config.joinMessage, true,
                "Tell players whether fusion is on when they join.", value -> config.joinMessage = value);
        fields.toggle(category, "First discoveries", config.firstDiscoveryMessage, true,
                "Tell everyone when a new item is discovered for the first time.", value -> config.firstDiscoveryMessage = value);
        fields.toggle(category, "Special discoveries", config.specialDiscoveryMessage, true,
                "Announce new special items.", value -> config.specialDiscoveryMessage = value);
        fields.toggle(category, "Milestones", config.milestoneMessages, true,
                "Announce discovery count milestones. Also controls the milestone XP reward.", value -> config.milestoneMessages = value);
        fields.toggle(category, "Queue progress", config.queueFeedback, true,
                "Show generation status and queue position in the action bar.", value -> config.queueFeedback = value);
    }
}
