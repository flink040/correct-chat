package de.dho.correctchat.gui;

import de.dho.correctchat.core.Localizer;

import de.dho.correctchat.config.CorrectChatConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

final class AlertListScreen extends Screen {
    private final Screen parent;
    private final CorrectChatConfig config;
    private int page;

    AlertListScreen(Screen parent, CorrectChatConfig config) {
        super(Localizer.literal("Chat-Alarme"));
        this.parent = parent;
        this.config = config;
    }

    @Override protected void init() {
        int rows = Math.max(1, (height - 72) / 24);
        int pages = Math.max(1, (config.alerts.size() + rows - 1) / rows);
        page = Math.min(page, pages - 1);
        int from = page * rows;
        int to = Math.min(config.alerts.size(), from + rows);
        for (int i = from; i < to; i++) {
            int index = i;
            CorrectChatConfig.ChatAlert alert = config.alerts.get(i);
            int y = 34 + (i - from) * 24;
            String name = alert.name == null || alert.name.isBlank() ? "Alarm " + (i + 1) : alert.name;
            addRenderableWidget(Button.builder(Localizer.literal((i + 1) + ". " + name), button ->
                    minecraft.gui.setScreen(AlertEditorScreen.manage(this, config, alert, false)))
                    .bounds(width / 2 - 150, y, 220, 20).build());
            addRenderableWidget(Button.builder(Localizer.literal("Löschen").withStyle(ChatFormatting.RED), button -> {
                config.alerts.remove(index);
                rebuildWidgets();
            }).bounds(width / 2 + 74, y, 76, 20).build());
        }

        int bottom = height - 28;
        addRenderableWidget(Button.builder(Localizer.literal("+ Alarm").withStyle(ChatFormatting.GREEN), button ->
                minecraft.gui.setScreen(AlertEditorScreen.manage(this, config,
                        new CorrectChatConfig.ChatAlert(), true))).bounds(width / 2 - 150, bottom, 92, 20).build());
        Button previous = Button.builder(Localizer.literal("◀"), button -> { page--; rebuildWidgets(); })
                .bounds(width / 2 - 52, bottom, 34, 20).build();
        previous.active = page > 0;
        addRenderableWidget(previous);
        Button indicator = Button.builder(Localizer.literal((page + 1) + "/" + pages), button -> { })
                .bounds(width / 2 - 14, bottom, 48, 20).build();
        indicator.active = false;
        addRenderableWidget(indicator);
        Button next = Button.builder(Localizer.literal("▶"), button -> { page++; rebuildWidgets(); })
                .bounds(width / 2 + 38, bottom, 34, 20).build();
        next.active = page < pages - 1;
        addRenderableWidget(next);
        addRenderableWidget(Button.builder(Localizer.literal("Zurück"), button -> onClose())
                .bounds(width / 2 + 78, bottom, 72, 20).build());
    }

    @Override public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        graphics.centeredText(font, title, width / 2, 13, 0xFFFFFFFF);
        graphics.centeredText(font, Localizer.literal("Alarm auswählen, um ihn zu bearbeiten"), width / 2, 24, 0xFFAAAAAA);
        if (config.alerts.isEmpty()) {
            graphics.centeredText(font, Localizer.literal("Noch keine Alarme angelegt"), width / 2, height / 2 - 8, 0xFFE0E0E0);
            graphics.centeredText(font, Localizer.literal("Erstelle einen per Rechtsklick auf eine Chatnachricht."),
                    width / 2, height / 2 + 5, 0xFF999999);
        }
    }

    @Override public void onClose() { minecraft.gui.setScreen(parent); }
    @Override public boolean isPauseScreen() { return false; }
}
