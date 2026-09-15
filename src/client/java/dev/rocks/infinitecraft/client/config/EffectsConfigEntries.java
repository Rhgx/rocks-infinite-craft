package dev.rocks.infinitecraft.client.config;

import dev.rocks.infinitecraft.ModConfig;
import me.shedaniel.clothconfig2.api.ConfigCategory;

final class EffectsConfigEntries {
    private EffectsConfigEntries() {
    }

    static void add(ConfigCategory category, ConfigEntries fields, ModConfig config) {
        fields.section(category, "Particles");
        fields.toggle(category, "Combining particles", config.combiningParticles, true,
                value -> config.combiningParticles = value);
        fields.toggle(category, "Failure particles", config.failureParticles, true,
                value -> config.failureParticles = value);
        fields.toggle(category, "Success particles", config.successParticles, true,
                value -> config.successParticles = value);

        fields.section(category, "Sound");
        fields.toggle(category, "Success sound", config.successSound, true, value -> config.successSound = value);
        fields.toggle(category, "Failure sound", config.failureSound, true, value -> config.failureSound = value);

        fields.section(category, "Messages");
        fields.toggle(category, "Join message", config.joinMessage, true, value -> config.joinMessage = value);
        fields.toggle(category, "First discovery messages", config.firstDiscoveryMessage, true,
                value -> config.firstDiscoveryMessage = value);
        fields.toggle(category, "Special discovery messages", config.specialDiscoveryMessage, true,
                value -> config.specialDiscoveryMessage = value);
        fields.toggle(category, "Discovery milestones", config.milestoneMessages, true,
                value -> config.milestoneMessages = value);
        fields.toggle(category, "Generation queue feedback", config.queueFeedback, true,
                value -> config.queueFeedback = value);
    }
}
