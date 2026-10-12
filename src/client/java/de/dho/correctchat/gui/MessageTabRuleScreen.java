package de.dho.correctchat.gui;

import de.dho.correctchat.CorrectChatClient;
import de.dho.correctchat.config.CorrectChatConfig;
import de.dho.correctchat.core.MessageRuleSuggestion;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/** Review screen for a suggested routing rule. Nothing is saved before Apply is pressed. */
final class MessageTabRuleScreen extends Screen {
    enum Proposal { PLAYER, FORMAT, EXACT }

    private final Screen parent;
    private final Screen chatScreen;
    private final MessageRuleSuggestion suggestion;
    private final CorrectChatConfig draft;
    private final String serverKey;
    private final List<CorrectChatConfig.ChatTab> targets = new ArrayList<>();
    private Proposal proposal;
    private int targetIndex;
    private EditBox tabName;
    private EditBox rule;
    private String tabNameValue;
    private String ruleValue;
    private String error = "";

    MessageTabRuleScreen(Screen parent, Screen chatScreen, MessageRuleSuggestion suggestion, Proposal proposal) {
        super(Component.literal("Tab-Regel prüfen"));
        this.parent = parent;
        this.chatScreen = chatScreen;
        this.suggestion = suggestion;
        this.proposal = proposal;
        this.draft = CorrectChatClient.config().copy();
        this.serverKey = CorrectChatClient.workspace().currentServerKey();
        this.tabNameValue = suggestion.suggestedTabName();
        this.ruleValue = proposedRule();
        refreshTargets();
    }

    @Override protected void init() {
        int x = width / 2 - 180;
        int w = 360;
        int y = 52;
        tabName = new EditBox(font, x + 104, y, w - 104, 20, Component.literal("Tabname"));
        tabName.setMaxLength(64); tabName.setValue(tabNameValue); tabName.active = targetIndex == 0;
        addRenderableWidget(tabName); y += 25;
        addRenderableWidget(Button.builder(Component.literal("Vorschlag: " + proposalName()), button -> {
            storeFields();
            proposal = Proposal.values()[(proposal.ordinal() + 1) % Proposal.values().length];
            ruleValue = proposedRule();
            rebuildWidgets();
        }).bounds(x, y, w, 20).build()); y += 25;
        rule = new EditBox(font, x, y, w, 20, Component.literal("Regulärer Ausdruck"));
        rule.setMaxLength(1024); rule.setValue(ruleValue); addRenderableWidget(rule); y += 25;
        addRenderableWidget(Button.builder(Component.literal("Ziel: " + targetName()), button -> {
            storeFields();
            targetIndex = (targetIndex + 1) % (targets.size() + 1);
            rebuildWidgets();
        }).bounds(x, y, w, 20).build());

        int bottom = height - 28;
        addRenderableWidget(Button.builder(Component.literal("Regel übernehmen").withStyle(ChatFormatting.GREEN), button -> apply())
                .bounds(width / 2 - 104, bottom, 100, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Abbrechen"), button -> onClose())
                .bounds(width / 2 + 4, bottom, 100, 20).build());
    }

    private void apply() {
        try {
            storeFields();
            Pattern.compile(ruleValue);
            if (ruleValue.isBlank()) throw new IllegalArgumentException("Die Regel darf nicht leer sein");
            if (targetIndex == 0) {
                CorrectChatConfig.ChatTab tab = new CorrectChatConfig.ChatTab();
                tab.name = tabNameValue.isBlank() ? suggestion.suggestedTabName() : tabNameValue;
                tab.filters.add(new CorrectChatConfig.TabFilter(CorrectChatConfig.TabType.REGEX, ruleValue));
                draft.tabsFor(serverKey).add(tab);
                draft.setPrimaryTabFor(serverKey, tab.id);
            } else {
                CorrectChatConfig.ChatTab tab = targets.get(targetIndex - 1);
                boolean duplicate = tab.filters.stream().anyMatch(filter ->
                        filter.type == CorrectChatConfig.TabType.REGEX && filter.value.equals(ruleValue));
                if (!duplicate) tab.filters.add(new CorrectChatConfig.TabFilter(CorrectChatConfig.TabType.REGEX, ruleValue));
                draft.setPrimaryTabFor(serverKey, tab.id);
            }
            CorrectChatClient.applyConfig(draft);
            minecraft.gui.hud.getChat().addClientSystemMessage(
                    Component.literal("Tab-Regel gespeichert: " + targetName()).withStyle(ChatFormatting.GREEN));
            minecraft.gui.setScreen(chatScreen);
        } catch (PatternSyntaxException exception) {
            error = "Ungültiger regulärer Ausdruck: " + exception.getDescription();
        } catch (IllegalArgumentException exception) {
            error = exception.getMessage();
        }
    }

    private void storeFields() {
        if (tabName != null) tabNameValue = tabName.getValue().trim();
        if (rule != null) ruleValue = rule.getValue().trim();
    }

    private void refreshTargets() {
        targets.clear();
        for (CorrectChatConfig.ChatTab tab : draft.tabsFor(serverKey)) {
            if (!tab.hasType(CorrectChatConfig.TabType.ALL)) targets.add(tab);
        }
        targetIndex = Math.min(targetIndex, targets.size());
    }

    private String targetName() { return targetIndex == 0 ? "Neuer Tab" : targets.get(targetIndex - 1).name; }
    private String proposalName() {
        return switch (proposal) {
            case PLAYER -> suggestion.player().isEmpty() ? "Exakte Nachricht" : "Spieler " + suggestion.player();
            case FORMAT -> "Gleiches Format";
            case EXACT -> "Exakte Nachricht";
        };
    }
    private String proposedRule() {
        return switch (proposal) {
            case PLAYER -> suggestion.player().isEmpty() ? Pattern.quote(suggestion.message()) : suggestion.playerRegex();
            case FORMAT -> suggestion.formatRegex();
            case EXACT -> Pattern.quote(suggestion.message());
        };
    }

    @Override public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        int x = width / 2 - 180;
        graphics.centeredText(font, title, width / 2, 12, 0xFFFFFFFF);
        graphics.centeredText(font, Component.literal("Die vorgeschlagene Regex kann vor dem Speichern bearbeitet werden."),
                width / 2, 25, 0xFF9DA6A1);
        graphics.text(font, Component.literal("Tabname"), x, 58, 0xFFE0E0E0, true);
        graphics.text(font, Component.literal("Regel testet: " + suggestion.message()), x, 154, 0xFFAAAAAA, false);
        if (!error.isEmpty()) graphics.centeredText(font, Component.literal(error), width / 2, height - 42, 0xFFFF6060);
    }

    @Override public void onClose() { minecraft.gui.setScreen(parent); }
    @Override public boolean isPauseScreen() { return false; }
}
