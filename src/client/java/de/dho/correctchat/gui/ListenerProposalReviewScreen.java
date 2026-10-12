package de.dho.correctchat.gui;

import de.dho.correctchat.CorrectChatClient;
import de.dho.correctchat.config.CorrectChatConfig;
import de.dho.correctchat.core.MessageRuleSuggestion;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Stages a suggested listener and requires an explicit final confirmation. */
final class ListenerProposalReviewScreen extends Screen {
    private final Screen parent;
    private final Screen chatScreen;
    private final CorrectChatConfig draft;
    private final CorrectChatConfig.ChatListener listener;

    ListenerProposalReviewScreen(Screen parent, Screen chatScreen, MessageRuleSuggestion suggestion) {
        super(Component.literal("Automatische Regel prüfen"));
        this.parent = parent;
        this.chatScreen = chatScreen;
        this.draft = CorrectChatClient.config().copy();
        this.listener = new CorrectChatConfig.ChatListener();
        listener.name = suggestion.suggestedRuleName();
        listener.matchType = CorrectChatConfig.MatchType.REGEX;
        listener.match = suggestion.formatRegex();
        listener.sound.enabled = true;
        draft.listeners.add(listener);
    }

    @Override protected void init() {
        int x = width / 2 - 155;
        int y = 72;
        addRenderableWidget(Button.builder(Component.literal("Regel bearbeiten …"),
                button -> minecraft.gui.setScreen(new ListenerEditorScreen(this, listener)))
                .bounds(x, y, 310, 20).build()); y += 26;
        addRenderableWidget(Button.builder(Component.literal("Geprüfte Regel speichern").withStyle(ChatFormatting.GREEN),
                button -> save()).bounds(x, y, 310, 20).build()); y += 26;
        addRenderableWidget(Button.builder(Component.literal("Verwerfen"), button -> onClose())
                .bounds(x, y, 310, 20).build());
    }

    private void save() {
        listener.normalize();
        CorrectChatClient.applyConfig(draft);
        minecraft.gui.hud.getChat().addClientSystemMessage(
                Component.literal("Automatische Regel gespeichert: " + listener.name).withStyle(ChatFormatting.GREEN));
        minecraft.gui.setScreen(chatScreen);
    }

    @Override public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        graphics.centeredText(font, title, width / 2, 12, 0xFFFFFFFF);
        graphics.centeredText(font, Component.literal("Noch nichts gespeichert – zuerst Muster und Aktion prüfen."),
                width / 2, 25, 0xFF9DA6A1);
        graphics.centeredText(font, Component.literal("Vorschlag: ähnliche Nachrichten erkennen und einen Glockenton abspielen."),
                width / 2, 42, 0xFFE0E0E0);
        graphics.centeredText(font, Component.literal("Name: " + listener.name), width / 2, 54, 0xFF71C9A3);
    }

    @Override public void onClose() { minecraft.gui.setScreen(parent); }
    @Override public boolean isPauseScreen() { return false; }
}
