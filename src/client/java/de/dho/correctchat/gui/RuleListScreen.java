package de.dho.correctchat.gui;

import de.dho.correctchat.core.Localizer;

import de.dho.correctchat.config.CorrectChatConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

final class RuleListScreen extends Screen {
    private final Screen parent;
    private final CorrectChatConfig config;
    private final boolean replacements;
    private int page;

    RuleListScreen(Screen parent, CorrectChatConfig config, boolean replacements) {
        super(Localizer.literal(replacements ? "Textersetzungen" : "Chat-Listener"));
        this.parent = parent;
        this.config = config;
        this.replacements = replacements;
    }

    @Override
    protected void init() {
        int rows = rowsPerPage();
        int size = size();
        int pages = Math.max(1, (size + rows - 1) / rows);
        page = Math.min(page, pages - 1);
        int from = page * rows;
        int to = Math.min(size, from + rows);
        for (int i = from; i < to; i++) {
            int index = i;
            int y = 34 + (i - from) * 24;
            String name = displayName(i);
            addRenderableWidget(Button.builder(Component.literal(name), button -> openEditor(index))
                    .bounds(width / 2 - 150, y, 220, 20).build());
            addRenderableWidget(Button.builder(Localizer.literal("Löschen").withStyle(ChatFormatting.RED), button -> {
                remove(index);
                rebuildWidgets();
            }).bounds(width / 2 + 74, y, 76, 20).build());
        }
        int bottom = height - 28;
        addRenderableWidget(Button.builder(Localizer.literal(replacements ? "+ Ersetzung" : "+ Listener").withStyle(ChatFormatting.GREEN), button -> add())
                .bounds(width / 2 - 150, bottom, 92, 20).build());
        Button previous = Button.builder(Localizer.literal("◀"), button -> { page = Math.max(0, page - 1); rebuildWidgets(); })
                .bounds(width / 2 - 52, bottom, 34, 20).build();
        previous.active = page > 0;
        addRenderableWidget(previous);
        Button indicator = Button.builder(Localizer.literal((page + 1) + "/" + pages), button -> { }).bounds(width / 2 - 14, bottom, 48, 20).build();
        indicator.active = false;
        addRenderableWidget(indicator);
        Button next = Button.builder(Localizer.literal("▶"), button -> { page = Math.min(pages - 1, page + 1); rebuildWidgets(); })
                .bounds(width / 2 + 38, bottom, 34, 20).build();
        next.active = page < pages - 1;
        addRenderableWidget(next);
        addRenderableWidget(Button.builder(Localizer.literal("Zurück"), button -> onClose()).bounds(width / 2 + 78, bottom, 72, 20).build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        graphics.centeredText(font, title, width / 2, 13, 0xFFFFFFFF);
        graphics.centeredText(font, Localizer.literal("Eintrag auswählen, um ihn zu bearbeiten"), width / 2, 24, 0xFFAAAAAA);
        if (size() == 0) {
            graphics.centeredText(font, Localizer.literal(replacements ? "Noch keine Ersetzungen angelegt" : "Noch keine Listener angelegt"), width / 2, height / 2 - 8, 0xFFE0E0E0);
            graphics.centeredText(font, Localizer.literal("Unten kannst du den ersten Eintrag erstellen."), width / 2, height / 2 + 5, 0xFF999999);
        }
    }

    @Override public void onClose() { minecraft.gui.setScreen(parent); }
    @Override public boolean isPauseScreen() { return false; }

    private int rowsPerPage() { return Math.max(1, (height - 72) / 24); }
    private int size() { return replacements ? config.replacements.size() : config.listeners.size(); }
    private String displayName(int index) {
        String name = replacements ? config.replacements.get(index).name : config.listeners.get(index).name;
        if (name == null || name.isBlank()) name = replacements ? "Ersetzung " + (index + 1) : "Listener " + (index + 1);
        return (index + 1) + ". " + name;
    }
    private void remove(int index) {
        if (replacements) config.replacements.remove(index); else config.listeners.remove(index);
    }
    private void add() {
        if (replacements) {
            CorrectChatConfig.TextReplacement entry = new CorrectChatConfig.TextReplacement();
            minecraft.gui.setScreen(new ReplacementEditorScreen(this, entry, config.replacements::add));
        } else {
            CorrectChatConfig.ChatListener entry = new CorrectChatConfig.ChatListener();
            minecraft.gui.setScreen(new ListenerEditorScreen(this, entry, config.listeners::add));
        }
    }
    private void openEditor(int index) {
        if (replacements) minecraft.gui.setScreen(new ReplacementEditorScreen(this, config.replacements.get(index),
                saved -> config.replacements.set(index, saved)));
        else minecraft.gui.setScreen(new ListenerEditorScreen(this, config.listeners.get(index),
                saved -> config.listeners.set(index, saved)));
    }
}
