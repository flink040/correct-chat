package de.dho.correctchat;

import de.dho.correctchat.core.Localizer;

import de.dho.correctchat.config.ConfigManager;
import de.dho.correctchat.config.CorrectChatConfig;
import de.dho.correctchat.core.ChatEngine;
import de.dho.correctchat.core.ChatWorkspace;
import de.dho.correctchat.core.TwitchChatService;
import de.dho.correctchat.gui.CorrectChatConfigScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

import com.mojang.blaze3d.platform.InputConstants;

public final class CorrectChatClient implements ClientModInitializer {
    public static final String MOD_ID = "correctchat";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static CorrectChatConfig config;
    private static ChatEngine engine;
    private static ChatWorkspace workspace;
    private static TwitchChatService twitch;

    @Override
    public void onInitializeClient() {
        config = ConfigManager.load(LOGGER);
        workspace = new ChatWorkspace();
        workspace.register();
        twitch = new TwitchChatService();
        twitch.register();
        engine = new ChatEngine();
        engine.register();

        KeyMapping.Category category = KeyMapping.Category.register(net.minecraft.resources.Identifier.fromNamespaceAndPath(MOD_ID, "controls"));
        KeyMapping openConfig = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.correctchat.open_config", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_F8, category));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openConfig.consumeClick()) {
                client.gui.setScreen(new CorrectChatConfigScreen(client.gui.screen(), config.copy()));
            }
        });
        LOGGER.info("Correct Chat initialized for Minecraft 26.2");
    }

    public static CorrectChatConfig config() { return config; }
    public static ChatEngine engine() { return engine; }
    public static ChatWorkspace workspace() { return workspace; }
    public static TwitchChatService twitch() { return twitch; }

    public static void applyConfig(CorrectChatConfig updated) {
        updated.normalize();
        config = updated;
        ConfigManager.save(updated, LOGGER);
        notifyConfigChanged();
    }

    public static CorrectChatConfig resetConfig() throws IOException {
        CorrectChatConfig defaults = new CorrectChatConfig();
        defaults.normalize();
        ConfigManager.reset(defaults);
        config = defaults;
        notifyConfigChanged();
        return defaults.copy();
    }

    private static void notifyConfigChanged() {
        if (engine != null) engine.onConfigChanged();
        if (workspace != null) workspace.onConfigChanged();
        if (twitch != null) twitch.onConfigChanged();
    }

    public static Component status(boolean enabled) {
        return Localizer.literal(enabled ? "AN" : "AUS");
    }
}
