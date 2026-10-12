package de.dho.correctchat.gui;

import de.dho.correctchat.CorrectChatClient;
import de.dho.correctchat.config.CorrectChatConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Creates per-server copies of the tab/window workspace. */
final class ServerProfilesScreen extends Screen {
    private final Screen parent;
    private final CorrectChatConfig config;

    ServerProfilesScreen(Screen parent, CorrectChatConfig config) {
        super(Component.literal("Serverprofile"));
        this.parent = parent;
        this.config = config;
    }

    @Override protected void init() {
        int left = width / 2 - 190;
        int y = 40;
        for (int i = 0; i < config.serverProfiles.size() && y < height - 62; i++) {
            int index = i;
            CorrectChatConfig.ServerProfile profile = config.serverProfiles.get(i);
            addRenderableWidget(Button.builder(Component.literal(profile.name + " · " + profile.server + " · "
                            + (profile.enabled ? "AN" : "AUS")).withStyle(profile.enabled ? ChatFormatting.GREEN : ChatFormatting.GRAY),
                    button -> {
                        profile.enabled = !profile.enabled;
                        rebuildWidgets();
                    }).bounds(left, y, 292, 20).build());
            addRenderableWidget(Button.builder(Component.literal("Löschen").withStyle(ChatFormatting.RED), button -> {
                config.serverProfiles.remove(index);
                rebuildWidgets();
            }).bounds(left + 298, y, 82, 20).build());
            y += 24;
        }

        int bottom = height - 28;
        String current = CorrectChatClient.workspace().currentServerKey();
        Button add = Button.builder(Component.literal("+ Aktuellen Server"), button -> {
            CorrectChatConfig.ServerProfile profile = new CorrectChatConfig.ServerProfile();
            profile.server = current;
            profile.name = "singleplayer".equals(current) ? "Einzelspieler" : current;
            String globalPrimary = config.primaryTabFor("");
            profile.tabs = new ArrayList<>();
            Map<String, String> tabIds = new HashMap<>();
            config.tabsFor("").forEach(tab -> {
                CorrectChatConfig.ChatTab copy = tab.copy();
                copy.id = UUID.randomUUID().toString();
                tabIds.put(tab.id, copy.id);
                profile.tabs.add(copy);
            });
            profile.primaryTabId = tabIds.getOrDefault(globalPrimary, profile.tabs.getFirst().id);
            profile.windows = new ArrayList<>();
            config.windowsFor("").forEach(window -> {
                CorrectChatConfig.ChatWindow copy = window.copy();
                copy.activeTabId = tabIds.getOrDefault(window.activeTabId, profile.primaryTabId);
                profile.windows.add(copy);
            });
            profile.normalize();
            config.serverProfiles.add(profile);
            rebuildWidgets();
        }).bounds(width / 2 - 190, bottom, 184, 20).build();
        add.active = !current.isBlank() && config.serverProfiles.stream()
                .noneMatch(profile -> profile.server.equalsIgnoreCase(current));
        addRenderableWidget(add);
        addRenderableWidget(Button.builder(Component.literal("Zurück"), button -> onClose())
                .bounds(width / 2 + 6, bottom, 184, 20).build());
    }

    @Override public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        graphics.centeredText(font, title, width / 2, 12, 0xFFFFFFFF);
        graphics.centeredText(font, Component.literal("Ein Profil übernimmt Tabs und Fenster automatisch auf dem passenden Server."),
                width / 2, 24, 0xFFAAAAAA);
        if (config.serverProfiles.isEmpty()) graphics.centeredText(font,
                Component.literal("Noch keine Serverprofile. Verbinde dich mit einem Server und lege eines an."),
                width / 2, 72, 0xFFAAAAAA);
    }

    @Override public void onClose() { minecraft.gui.setScreen(parent); }
    @Override public boolean isPauseScreen() { return false; }
}
