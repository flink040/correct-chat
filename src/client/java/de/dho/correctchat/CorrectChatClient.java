package de.dho.correctchat;

import de.dho.correctchat.config.ConfigManager;
import de.dho.correctchat.config.CorrectChatConfig;
import de.dho.correctchat.core.ChatEngine;
import de.dho.correctchat.core.ChatWorkspace;
import de.dho.correctchat.core.TwitchChatService;
import de.dho.correctchat.gui.CorrectChatConfigScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
        registerChatMouseEvents();

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

    private static void registerChatMouseEvents() {
        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (!(screen instanceof ChatScreen)) return;

            // Fabric recreates a screen's event containers on every init/resize. Register on every
            // AFTER_INIT invocation as well; suppressing a repeated screen instance loses the hooks.
            ScreenMouseEvents.allowMouseDrag(screen).register((currentScreen, event, dragX, dragY) -> {
                if (workspace == null) return true;
                return !workspace.dragWindow(
                        event.x(), event.y(), client.getWindow().getGuiScaledWidth(), client.getWindow().getGuiScaledHeight());
            });
            ScreenMouseEvents.allowMouseRelease(screen).register((currentScreen, event) ->
                    workspace == null || !workspace.endWindowDrag());
        });
    }

    public static CorrectChatConfig config() { return config; }
    public static ChatEngine engine() { return engine; }
    public static ChatWorkspace workspace() { return workspace; }
    public static TwitchChatService twitch() { return twitch; }

    public static void applyConfig(CorrectChatConfig updated) {
        updated.normalize();
        config = updated;
        ConfigManager.save(updated, LOGGER);
        if (engine != null) engine.onConfigChanged();
        if (workspace != null) workspace.onConfigChanged();
        if (twitch != null) twitch.onConfigChanged();
    }

    public static Component status(boolean enabled) {
        return Component.literal(enabled ? "AN" : "AUS");
    }
}
