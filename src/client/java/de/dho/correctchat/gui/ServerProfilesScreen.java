package de.dho.correctchat.gui;

import de.dho.correctchat.core.Localizer;

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
    private int page;

    ServerProfilesScreen(Screen parent, CorrectChatConfig config) {
        super(Localizer.literal("Serverprofile"));
        this.parent = parent;
        this.config = config;
    }

    @Override protected void init() {
        int contentWidth = Math.min(380, width - 24);
        int left = (width - contentWidth) / 2;
        int rows = Math.max(1, (height - 76) / 24);
        int pages = Math.max(1, (config.serverProfiles.size() + rows - 1) / rows);
        page = Math.min(page, pages - 1);
        int from = page * rows;
        int to = Math.min(config.serverProfiles.size(), from + rows);
        int y = 40;
        for (int i = from; i < to; i++) {
            int index = i;
            CorrectChatConfig.ServerProfile profile = config.serverProfiles.get(i);
            addRenderableWidget(Button.builder(Localizer.literal(profile.name + " · " + profile.server + " · "
                            + (profile.enabled ? "AN" : "AUS")).withStyle(profile.enabled ? ChatFormatting.GREEN : ChatFormatting.GRAY),
                    button -> {
                        profile.enabled = !profile.enabled;
                        rebuildWidgets();
                    }).bounds(left, y, contentWidth - 88, 20).build());
            addRenderableWidget(Button.builder(Localizer.literal("Löschen").withStyle(ChatFormatting.RED), button -> {
                config.serverProfiles.remove(index);
                rebuildWidgets();
            }).bounds(left + contentWidth - 82, y, 82, 20).build());
            y += 24;
        }

        int bottom = height - 28;
        String current = CorrectChatClient.workspace().currentServerKey();
        int addWidth = Math.min(150, Math.max(80, contentWidth - 194));
        Button add = Button.builder(Localizer.literal("+ Aktuellen Server"), button -> {
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
            profile.primaryTabIds = config.primaryTabIdsFor("").stream()
                    .map(tabIds::get).filter(java.util.Objects::nonNull)
                    .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
            profile.windows = new ArrayList<>();
            config.windowsFor("").forEach(window -> {
                CorrectChatConfig.ChatWindow copy = window.copy();
                copy.activeTabId = tabIds.getOrDefault(window.activeTabId, profile.primaryTabId);
                copy.tabIds = window.tabIds.stream().map(tabIds::get).filter(java.util.Objects::nonNull)
                        .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
                profile.windows.add(copy);
            });
            profile.normalize();
            config.serverProfiles.add(profile);
            rebuildWidgets();
        }).bounds(left, bottom, addWidth, 20).build();
        add.active = !current.isBlank() && config.serverProfiles.stream()
                .noneMatch(profile -> profile.server.equalsIgnoreCase(current));
        addRenderableWidget(add);
        int pagerX = left + addWidth + 4;
        Button previous = Button.builder(Localizer.literal("◀"), button -> { page--; rebuildWidgets(); })
                .bounds(pagerX, bottom, 30, 20).build();
        previous.active = page > 0;
        addRenderableWidget(previous);
        Button indicator = Button.builder(Localizer.literal((page + 1) + "/" + pages), button -> { })
                .bounds(pagerX + 34, bottom, 42, 20).build();
        indicator.active = false;
        addRenderableWidget(indicator);
        Button next = Button.builder(Localizer.literal("▶"), button -> { page++; rebuildWidgets(); })
                .bounds(pagerX + 80, bottom, 30, 20).build();
        next.active = page < pages - 1;
        addRenderableWidget(next);
        addRenderableWidget(Button.builder(Localizer.literal("Zurück"), button -> onClose())
                .bounds(left + contentWidth - 80, bottom, 80, 20).build());
    }

    @Override public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        graphics.centeredText(font, title, width / 2, 12, 0xFFFFFFFF);
        graphics.centeredText(font, Localizer.literal("Ein Profil übernimmt Tabs und Fenster automatisch auf dem passenden Server."),
                width / 2, 24, 0xFFAAAAAA);
        if (config.serverProfiles.isEmpty()) graphics.centeredText(font,
                Localizer.literal("Noch keine Serverprofile. Verbinde dich mit einem Server und lege eines an."),
                width / 2, 72, 0xFFAAAAAA);
    }

    @Override public void onClose() { minecraft.gui.setScreen(parent); }
    @Override public boolean isPauseScreen() { return false; }
}
