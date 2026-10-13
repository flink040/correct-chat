package de.dho.correctchat.gui;

import de.dho.correctchat.core.Localizer;

import de.dho.correctchat.CorrectChatClient;
import de.dho.correctchat.config.CorrectChatConfig;
import de.dho.correctchat.core.AdvertisementFilter;
import de.dho.correctchat.core.MessageRuleSuggestion;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
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
    private final String exactMessage;

    public MessageActionsScreen(Screen chatScreen, Component message) {
        super(Localizer.literal("Nachrichtenaktion"));
        this.chatScreen = chatScreen;
        this.exactMessage = CorrectChatClient.workspace() == null
                ? message.getString() : CorrectChatClient.workspace().sourceText(message);
        Minecraft client = Minecraft.getInstance();
        List<String> knownPlayers = client.getConnection() == null ? List.of()
                : client.getConnection().getOnlinePlayers().stream()
                .map(info -> info.getProfile().name()).toList();
        this.suggestion = MessageRuleSuggestion.from(exactMessage, knownPlayers);
    }

    @Override protected void init() {
        int w = Math.min(310, width - 24);
        int x = (width - w) / 2;
        int y = 68;
        if (!suggestion.player().isEmpty()) {
            addRenderableWidget(Button.builder(
                    Localizer.literal("Spieler " + suggestion.player() + " ignorieren …").withStyle(ChatFormatting.RED),
                    button -> confirmIgnore()).bounds(x, y, w, 20).build());
            y += 20;
            addRenderableWidget(Button.builder(Localizer.literal("Nachrichten von " + suggestion.player() + " zu Tab …"),
                    button -> minecraft.gui.setScreen(new MessageTabRuleScreen(this, chatScreen, suggestion,
                            MessageTabRuleScreen.Proposal.PLAYER))).bounds(x, y, w, 20).build());
            y += 20;
        }
        addRenderableWidget(Button.builder(Localizer.literal("Generische Regex einem Tab zuordnen …"),
                button -> minecraft.gui.setScreen(new MessageTabRuleScreen(this, chatScreen, suggestion,
                        MessageTabRuleScreen.Proposal.FORMAT))).bounds(x, y, w, 20).build());
        y += 20;
        addRenderableWidget(Button.builder(Localizer.literal("Automatische Regel vorschlagen …"),
                button -> minecraft.gui.setScreen(new ListenerProposalReviewScreen(this, chatScreen, suggestion)))
                .bounds(x, y, w, 20).build());
        y += 20;
        addRenderableWidget(Button.builder(
                Localizer.literal("Alarm erstellen …").withStyle(ChatFormatting.GOLD),
                button -> minecraft.gui.setScreen(AlertEditorScreen.fromMessage(chatScreen, exactMessage)))
                .bounds(x, y, w, 20).build());
        y += 20;
        addRenderableWidget(Button.builder(
                Localizer.literal("Als Werbung markieren").withStyle(ChatFormatting.RED),
                button -> blockAdvertisement()).bounds(x, y, w, 20).build());
        y += 20;
        addRenderableWidget(Button.builder(Localizer.literal("Nachricht kopieren"), button -> {
            minecraft.keyboardHandler.setClipboard(suggestion.message());
            minecraft.gui.setScreen(chatScreen);
        }).bounds(x, y, w, 20).build());
        addRenderableWidget(Button.builder(Localizer.literal("Zurück"), button -> onClose())
                .bounds(width / 2 - 50, height - 28, 100, 20).build());
    }

    private void blockAdvertisement() {
        CorrectChatConfig updated = CorrectChatClient.config().copy();
        String advertisementKey = AdvertisementFilter.key(exactMessage);
        if (!advertisementKey.isBlank()
                && !AdvertisementFilter.isBlocked(updated.blockedAdvertisements, advertisementKey)) {
            updated.blockedAdvertisements.add(advertisementKey);
        }
        CorrectChatClient.applyConfig(updated);
        minecraft.gui.setScreen(chatScreen);
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
                        Localizer.literal(player + " wird von Correct Chat lokal ausgeblendet.").withStyle(ChatFormatting.GREEN));
                minecraft.gui.setScreen(chatScreen);
            } else {
                minecraft.gui.setScreen(this);
            }
        }, Localizer.literal("Spieler lokal ignorieren?"),
                Localizer.literal("Nachrichten von " + player + " werden anhand des erkannten Namens ausgeblendet."),
                Localizer.literal("Ignorieren"), Localizer.literal("Abbrechen")));
    }

    @Override public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        graphics.centeredText(font, title, width / 2, 12, 0xFFFFFFFF);
        graphics.centeredText(font, Localizer.literal("Vorschläge werden erst nach Prüfung und Bestätigung gespeichert."),
                width / 2, 24, 0xFF9DA6A1);
        List<FormattedCharSequence> preview = font.split(Component.literal(suggestion.message()), Math.min(420, width - 32));
        int y = 39;
        for (int i = 0; i < Math.min(1, preview.size()); i++) {
            graphics.centeredText(font, preview.get(i), width / 2, y, 0xFFE8ECEA);
            y += font.lineHeight + 2;
        }
        String detected = suggestion.player().isEmpty() ? "Kein Spieler sicher erkannt" : "Erkannter Spieler: " + suggestion.player();
        graphics.centeredText(font, Localizer.literal(detected), width / 2, 54, 0xFF71C9A3);
    }

    @Override public void onClose() { minecraft.gui.setScreen(chatScreen); }
    @Override public boolean isPauseScreen() { return false; }
}
