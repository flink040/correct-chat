package de.dho.correctchat.gui;

import de.dho.correctchat.core.Localizer;

import de.dho.correctchat.config.CorrectChatConfig;
import de.dho.correctchat.core.AdvertisementFilter;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Edits an exact advertisement key or a simple * wildcard rule transactionally. */
final class AdvertisementRuleEditorScreen extends Screen {
    private final Screen parent;
    private final CorrectChatConfig config;
    private final int index;
    private EditBox pattern;
    private String error = "";

    AdvertisementRuleEditorScreen(Screen parent, CorrectChatConfig config, int index) {
        super(Localizer.literal(index < 0 ? "Werbemuster erstellen" : "Werbemuster bearbeiten"));
        this.parent = parent;
        this.config = config;
        this.index = index;
    }

    @Override protected void init() {
        int fieldWidth = Math.min(340, width - 24);
        int x = (width - fieldWidth) / 2;
        String current = index >= 0 && index < config.blockedAdvertisements.size()
                ? config.blockedAdvertisements.get(index) : "";
        pattern = new EditBox(font, x, 68, fieldWidth, 20, Localizer.literal("Muster"));
        pattern.setMaxLength(2048);
        pattern.setValue(current);
        addRenderableWidget(pattern);
        addRenderableWidget(Button.builder(Localizer.literal("Übernehmen").withStyle(ChatFormatting.GREEN), button -> save())
                .bounds(width / 2 - 104, height - 28, 100, 20).build());
        addRenderableWidget(Button.builder(Localizer.literal("Abbrechen"), button -> onClose())
                .bounds(width / 2 + 4, height - 28, 100, 20).build());
        setInitialFocus(pattern);
    }

    private void save() {
        String normalized = AdvertisementFilter.key(pattern.getValue());
        if (normalized.isBlank()) {
            error = "Das Muster darf nicht leer sein.";
            return;
        }
        for (int i = 0; i < config.blockedAdvertisements.size(); i++) {
            if (i != index && AdvertisementFilter.key(config.blockedAdvertisements.get(i)).equals(normalized)) {
                error = "Dieses Muster ist bereits vorhanden.";
                return;
            }
        }
        if (index >= 0 && index < config.blockedAdvertisements.size()) config.blockedAdvertisements.set(index, normalized);
        else config.blockedAdvertisements.add(normalized);
        onClose();
    }

    @Override public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        graphics.centeredText(font, title, width / 2, 18, 0xFFFFFFFF);
        graphics.centeredText(font, Localizer.literal("Exakter Text oder * als Platzhalter, z. B. [Werbung] * Rabatt"),
                width / 2, 40, 0xFFAAAAAA);
        graphics.text(font, Localizer.literal("Muster"), (width - Math.min(340, width - 24)) / 2, 56, 0xFFE0E0E0, true);
        if (!error.isEmpty()) graphics.centeredText(font, Localizer.literal(error), width / 2, 96, 0xFFFF6060);
    }

    @Override public void onClose() { minecraft.gui.setScreen(parent); }
    @Override public boolean isPauseScreen() { return false; }
}
