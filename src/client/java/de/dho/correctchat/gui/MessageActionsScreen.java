package de.dho.correctchat.gui;

import de.dho.correctchat.CorrectChatClient;
import de.dho.correctchat.config.CorrectChatConfig;
import de.dho.correctchat.core.MessageRuleSuggestion;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;

/** Context menu opened by right-clicking a concrete chat message. */
public final class MessageActionsScreen extends Screen {
    private final Screen chatScreen;
    private final MessageRuleSuggestion suggestion;

    public MessageActionsScreen(Screen chatScreen, Component message) {
        super(Component.literal("Nachrichtenaktion"));
        this.chatScreen = chatScreen;
        this.suggestion = MessageRuleSuggestion.from(message.getString());
    }

    @Override protected void init() {
        int x = width / 2 - 155;
        int w = 310;
        int y = 76;
        if (!suggestion.player().isEmpty()) {
            addRenderableWidget(Button.builder(
                    Component.literal("Spieler " + suggestion.player() + " ignorieren …").withStyle(ChatFormatting.RED),
                    button -> confirmIgnore()).bounds(x, y, w, 20).build());
            y += 24;
            addRenderableWidget(Button.builder(Component.literal("Nachrichten von " + suggestion.player() + " zu Tab …"),
                    button -> minecraft.gui.setScreen(new MessageTabRuleScreen(this, chatScreen, suggestion,
                            MessageTabRuleScreen.Proposal.PLAYER))).bounds(x, y, w, 20).build());
            y += 24;
        }
        addRenderableWidget(Button.builder(Component.literal("Nachrichten dieser Art zu Tab …"),
                button -> minecraft.gui.setScreen(new MessageTabRuleScreen(this, chatScreen, suggestion,
                        MessageTabRuleScreen.Proposal.FORMAT))).bounds(x, y, w, 20).build());
        y += 24;
        addRenderableWidget(Button.builder(Component.literal("Automatische Regel vorschlagen …"),
                button -> minecraft.gui.setScreen(new ListenerProposalReviewScreen(this, chatScreen, suggestion)))
                .bounds(x, y, w, 20).build());
        y += 24;
        addRenderableWidget(Button.builder(Component.literal("Nachricht kopieren"), button -> {
            minecraft.keyboardHandler.setClipboard(suggestion.message());
            minecraft.gui.setScreen(chatScreen);
        }).bounds(x, y, w, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Zurück"), button -> onClose())
                .bounds(width / 2 - 50, height - 28, 100, 20).build());
    }

    private void confirmIgnore() {
        String player = suggestion.player();
        minecraft.gui.setScreen(new ConfirmScreen(confirmed -> {
            if (confirmed) {
                CorrectChatConfig updated = CorrectChatClient.config().copy();
                boolean exists = updated.ignoredPlayers.stream().anyMatch(value -> value.equalsIgnoreCase(player));
                if (!exists) updated.ignoredPlayers.add(player);
                CorrectChatClient.applyConfig(updated);
                minecraft.gui.hud.getChat().addClientSystemMessage(
                        Component.literal(player + " wird von Correct Chat lokal ausgeblendet.").withStyle(ChatFormatting.GREEN));
                minecraft.gui.setScreen(chatScreen);
            } else {
                minecraft.gui.setScreen(this);
            }
        }, Component.literal("Spieler lokal ignorieren?"),
                Component.literal("Nachrichten von " + player + " werden anhand des erkannten Namens ausgeblendet."),
                Component.literal("Ignorieren"), Component.literal("Abbrechen")));
    }

    @Override public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        graphics.centeredText(font, title, width / 2, 12, 0xFFFFFFFF);
        graphics.centeredText(font, Component.literal("Vorschläge werden erst nach Prüfung und Bestätigung gespeichert."),
                width / 2, 24, 0xFF9DA6A1);
        List<FormattedCharSequence> preview = font.split(Component.literal(suggestion.message()), Math.min(420, width - 32));
        int y = 39;
        for (int i = 0; i < Math.min(2, preview.size()); i++) {
            graphics.centeredText(font, preview.get(i), width / 2, y, 0xFFE8ECEA);
            y += font.lineHeight + 2;
        }
        String detected = suggestion.player().isEmpty() ? "Kein Spieler sicher erkannt" : "Erkannter Spieler: " + suggestion.player();
        graphics.centeredText(font, Component.literal(detected), width / 2, 64, 0xFF71C9A3);
    }

    @Override public void onClose() { minecraft.gui.setScreen(chatScreen); }
    @Override public boolean isPauseScreen() { return false; }
}
