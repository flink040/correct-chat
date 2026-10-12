package de.dho.correctchat.gui;

import de.dho.correctchat.CorrectChatClient;
import de.dho.correctchat.config.CorrectChatConfig;
import de.dho.correctchat.core.LegacyText;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

public final class CorrectChatConfigScreen extends Screen {
    private static final int ROW = 24;
    private static final int ACCENT = 0xFF71C9A3;
    private final Screen parent;
    private final CorrectChatConfig draft;
    private final List<Label> labels = new ArrayList<>();
    private Section section = Section.GENERAL;
    private EditBox timeStyle, timeFormat, copyFormat, maxLog, logRetention, maxResponses, responseWindow, endpoint;
    private EditBox twitchChannel, twitchUsername, twitchToken;
    private String error = "";

    public CorrectChatConfigScreen(Screen parent, CorrectChatConfig draft) {
        super(Component.literal("Correct Chat"));
        this.parent = parent;
        this.draft = draft;
    }

    @Override
    protected void init() {
        labels.clear();
        clearFields();
        int contentWidth = Math.min(460, width - 24);
        int left = (width - contentWidth) / 2;
        int gap = 8;
        int navWidth = (contentWidth - gap * (Section.values().length - 1)) / Section.values().length;
        int navY = 37;

        for (int i = 0; i < Section.values().length; i++) {
            Section target = Section.values()[i];
            Component label = Component.literal(target.label);
            if (target == section) label = label.copy().withStyle(ChatFormatting.GREEN);
            Button button = Button.builder(label, pressed -> switchSection(target))
                    .bounds(left + i * (navWidth + gap), navY, navWidth, 20).build();
            button.active = target != section;
            addRenderableWidget(button);
        }

        int top = 79;
        switch (section) {
            case GENERAL -> general(left, top, contentWidth);
            case APPEARANCE -> appearance(left, top, contentWidth);
            case AUTOMATION -> automation(left, top, contentWidth);
            case PRIVACY -> privacy(left, top, contentWidth);
            case TWITCH -> twitch(left, top, contentWidth);
        }

        int bottom = height - 28;
        addRenderableWidget(Button.builder(Component.literal("Speichern").withStyle(ChatFormatting.GREEN), button -> save())
                .bounds(width / 2 - 104, bottom, 100, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Abbrechen"), button -> onClose())
                .bounds(width / 2 + 4, bottom, 100, 20).build());
    }

    private void general(int x, int y, int w) {
        toggle(x, y, w, "Mod aktiviert", () -> draft.enabled, value -> draft.enabled = value); y += ROW;
        toggle(x, y, w, "Tippfehler-Schutz für Befehle", () -> draft.antiCommandChoker, value -> draft.antiCommandChoker = value); y += ROW;
        toggle(x, y, w, "Chat-Tabs und Zusatzfenster", () -> draft.chatTabsEnabled, value -> draft.chatTabsEnabled = value); y += ROW + 4;
        int half = (w - 8) / 2;
        List<CorrectChatConfig.ChatTab> activeTabs = draft.tabsFor(CorrectChatClient.workspace().currentServerKey());
        List<CorrectChatConfig.ChatWindow> activeWindows = draft.windowsFor(CorrectChatClient.workspace().currentServerKey());
        addRenderableWidget(Button.builder(Component.literal("Tabs verwalten (" + activeTabs.size() + ")"),
                button -> open(new ChatTabsScreen(this, draft)))
                .bounds(x, y, half, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Fenster verwalten (" + activeWindows.size() + ")"),
                button -> open(new ChatWindowsScreen(this, draft)))
                .bounds(x + half + 8, y, half, 20).build());
        y += ROW;
        addRenderableWidget(Button.builder(Component.literal("Serverprofile (" + draft.serverProfiles.size() + ")"),
                button -> open(new ServerProfilesScreen(this, draft))).bounds(x, y, w, 20).build());
    }

    private void appearance(int x, int y, int w) {
        int half = (w - 8) / 2;
        toggle(x, y, half, "Zeitstempel", () -> draft.chatTime, value -> draft.chatTime = value);
        toggle(x + half + 8, y, half, "Copy vor Nachricht", () -> draft.copyButton, value -> draft.copyButton = value); y += ROW;
        toggle(x, y, w, "Formatierte Eingabe-Vorschau", () -> draft.chatMessagePreview, value -> draft.chatMessagePreview = value); y += ROW;
        toggle(x, y, half, "Ungelesen-Zähler", () -> draft.unreadBadges, value -> draft.unreadBadges = value);
        toggle(x + half + 8, y, half, "Erwähnungen", () -> draft.mentionHighlight, value -> draft.mentionHighlight = value); y += ROW;
        timeStyle = field(x, y, half, "Darstellung", draft.timeStyle, 120);
        copyFormat = field(x + half + 8, y, half, "Kopier-Text", draft.copyFormat, 120); y += ROW;
        timeFormat = field(x, y, half, "Zeitformat", draft.timeFormat, 100);
        toggle(x + half + 8, y, half, "Chatlog speichern", () -> draft.chatLog, value -> draft.chatLog = value); y += ROW;
        maxLog = field(x + half + 8, y, half, "Max. Nachrichten", Integer.toString(draft.maxStoredMessages), 74);
    }

    private void automation(int x, int y, int w) {
        int half = (w - 8) / 2;
        toggle(x, y, half, "Textersetzungen", () -> draft.textReplacement, value -> draft.textReplacement = value);
        toggle(x + half + 8, y, half, "Chat-Listener", () -> draft.chatListeners, value -> draft.chatListeners = value); y += ROW;
        addRenderableWidget(Button.builder(Component.literal("Ersetzungen verwalten (" + draft.replacements.size() + ")"),
                button -> open(new RuleListScreen(this, draft, true))).bounds(x, y, half, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Listener verwalten (" + draft.listeners.size() + ")"),
                button -> open(new RuleListScreen(this, draft, false))).bounds(x + half + 8, y, half, 20).build()); y += ROW;
        toggle(x, y, w, "Listener-Antispam", () -> draft.antiSpam, value -> draft.antiSpam = value); y += ROW;
        y += 18;
        maxResponses = field(x, y, half, "Antworten", Integer.toString(draft.maxResponses), 64);
        responseWindow = field(x + half + 8, y, half, "Zeitraum (Min.)", Integer.toString(draft.responseWindowMinutes), 64);
    }

    private void privacy(int x, int y, int w) {
        toggle(x, y, w, "Links vor dem Senden prüfen", () -> draft.linkFilter, value -> draft.linkFilter = value); y += ROW;
        addRenderableWidget(Button.builder(Component.literal("Erlaubte URLs verwalten (" + draft.linkExceptions.size() + ")"),
                button -> open(new LinkExceptionsScreen(this, draft))).bounds(x, y, w, 20).build()); y += ROW + 8;
        int half = (w - 8) / 2;
        addRenderableWidget(Button.builder(Component.literal("Chatlog-Format: " + draft.chatLogFormat), button -> {
            draft.chatLogFormat = draft.chatLogFormat == CorrectChatConfig.ChatLogFormat.JSON
                    ? CorrectChatConfig.ChatLogFormat.TEXT : CorrectChatConfig.ChatLogFormat.JSON;
            button.setMessage(Component.literal("Chatlog-Format: " + draft.chatLogFormat));
        }).bounds(x, y, half, 20).build());
        logRetention = field(x + half + 8, y, half, "Max. Dateien", Integer.toString(draft.chatLogRetention), 70); y += ROW + 8;
        toggle(x, y, w, "Externen Chatlog-Upload erlauben", () -> draft.uploadEnabled, value -> draft.uploadEnabled = value); y += ROW;
        endpoint = field(x, y, w, "Upload-URL", draft.uploadEndpoint, 300);
    }

    private void twitch(int x, int y, int w) {
        toggle(x, y, w, "Twitch-Chat verbinden", () -> draft.twitchEnabled, value -> draft.twitchEnabled = value); y += ROW;
        twitchChannel = field(x, y, w, "Kanal", draft.twitchChannel, 260); y += ROW;
        twitchUsername = field(x, y, w, "Twitch-Benutzer", draft.twitchUsername, 260); y += ROW;
        twitchToken = field(x, y, w, "Neuer OAuth-Token", "", 260); y += ROW;
        addRenderableWidget(Button.builder(Component.literal(draft.twitchOauthToken.isBlank()
                        ? "Kein Token gespeichert · nur Lesen"
                        : "Token gespeichert · Klicken zum Löschen"), button -> {
                    storeVisibleFields();
                    draft.twitchOauthToken = "";
                    rebuildWidgets();
                }).bounds(x, y, w, 20).build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        int contentWidth = Math.min(460, width - 24);
        int left = (width - contentWidth) / 2;
        int gap = 8;
        int navWidth = (contentWidth - gap * (Section.values().length - 1)) / Section.values().length;
        int selectedX = left + section.ordinal() * (navWidth + gap);
        graphics.centeredText(font, title, width / 2, 10, 0xFFFFFFFF);
        graphics.centeredText(font, Component.literal("F8 öffnen · Speichern übernimmt alle Änderungen"), width / 2, 22, 0xFF9DA6A1);
        graphics.fill(selectedX, 58, selectedX + navWidth, 60, ACCENT);
        graphics.fill(left, 63, left + contentWidth, 65, ACCENT);
        graphics.text(font, Component.literal(section.heading), left, 68, 0xFFFFFFFF, true);
        labels.forEach(label -> graphics.text(font, label.text, label.x, label.y + 6, 0xFFD9DFDC, true));
        if (section == Section.APPEARANCE) renderPreviews(graphics, left, contentWidth);
        if (section == Section.AUTOMATION) {
            graphics.text(font, Component.literal("Begrenzt Antworten pro Listener und Zeitraum; Befehle (/) sind frei."),
                    left + 4, 153, 0xFF9DA6A1, false);
        }
        if (section == Section.PRIVACY) {
            graphics.text(font, Component.literal("Lokale Exporte können private Nachrichten enthalten."),
                    left + 4, 215, 0xFFFFC266, false);
            graphics.text(font, Component.literal("Externe Uploads senden den Inhalt an die angegebene URL."),
                    left + 4, 227, 0xFFFFC266, false);
        }
        if (section == Section.TWITCH) {
            String state = CorrectChatClient.twitch() == null ? "nicht initialisiert" : CorrectChatClient.twitch().statusText();
            graphics.text(font, Component.literal("Status: " + state), left + 4, 205, 0xFF9DA6A1, false);
            graphics.text(font, Component.literal("Ohne Token kann der Chat gelesen, aber nicht beschrieben werden."),
                    left + 4, 219, 0xFF9DA6A1, false);
        }
        if (!error.isEmpty()) graphics.centeredText(font, Component.literal(error), width / 2, height - 41, 0xFFFF6B6B);
    }

    @Override public boolean isPauseScreen() { return false; }
    @Override public void onClose() { minecraft.gui.setScreen(parent); }

    private void switchSection(Section target) {
        if (target == section) return;
        try {
            storeVisibleFields();
            error = "";
            section = target;
            rebuildWidgets();
        } catch (IllegalArgumentException exception) {
            error = exception.getMessage();
        }
    }

    private void save() {
        try {
            storeVisibleFields();
            draft.normalize();
            CorrectChatClient.applyConfig(draft);
            minecraft.gui.setScreen(parent);
        } catch (IllegalArgumentException exception) {
            error = exception.getMessage();
        }
    }

    private void open(Screen screen) {
        try {
            storeVisibleFields();
            error = "";
            minecraft.gui.setScreen(screen);
        } catch (IllegalArgumentException exception) {
            error = exception.getMessage();
        }
    }

    private void storeVisibleFields() {
        if (timeStyle != null) draft.timeStyle = timeStyle.getValue();
        if (timeFormat != null) {
            draft.timeFormat = timeFormat.getValue();
            new java.text.SimpleDateFormat(draft.timeFormat);
        }
        if (copyFormat != null) draft.copyFormat = copyFormat.getValue();
        if (maxLog != null) draft.maxStoredMessages = integer(maxLog, "Maximale Nachrichten");
        if (logRetention != null) draft.chatLogRetention = integer(logRetention, "Maximale Chatlog-Dateien");
        if (maxResponses != null) draft.maxResponses = integer(maxResponses, "Antworten");
        if (responseWindow != null) draft.responseWindowMinutes = integer(responseWindow, "Zeitraum");
        if (endpoint != null) draft.uploadEndpoint = endpoint.getValue();
        if (twitchChannel != null) draft.twitchChannel = twitchChannel.getValue();
        if (twitchUsername != null) draft.twitchUsername = twitchUsername.getValue();
        if (twitchToken != null && !twitchToken.getValue().isBlank()) draft.twitchOauthToken = twitchToken.getValue();
    }

    private void clearFields() {
        timeStyle = null; timeFormat = null; copyFormat = null; maxLog = null; logRetention = null;
        maxResponses = null; responseWindow = null; endpoint = null;
        twitchChannel = null; twitchUsername = null; twitchToken = null;
    }

    private void renderPreviews(GuiGraphicsExtractor graphics, int x, int w) {
        if (timeStyle == null || timeFormat == null || copyFormat == null) return;
        int y = 225;
        graphics.fill(x, y, x + w, y + 19, 0x900B1110);
        graphics.fill(x, y, x + 2, y + 19, ACCENT);
        String time;
        try {
            time = new java.text.SimpleDateFormat(timeFormat.getValue()).format(new java.util.Date());
        } catch (IllegalArgumentException exception) {
            time = "ungültiges Zeitformat";
        }
        Component preview = Component.literal("Vorschau: ")
                .append(LegacyText.parse(copyFormat.getValue()))
                .append(LegacyText.parse(timeStyle.getValue().replace("%time%", time)))
                .append(Component.literal("<Spieler> Nachricht"));
        graphics.text(font, preview, x + 7, y + 6, 0xFFE8ECEA, false);
    }

    private void toggle(int x, int y, int width, String label, BooleanSupplier getter, Consumer<Boolean> setter) {
        Button button = Button.builder(toggleLabel(label, getter.getAsBoolean()), pressed -> {
            boolean value = !getter.getAsBoolean();
            setter.accept(value);
            pressed.setMessage(toggleLabel(label, value));
        }).bounds(x, y, width, 20).build();
        addRenderableWidget(button);
    }

    private EditBox field(int x, int y, int width, String label, String value, int preferredInputWidth) {
        int inputWidth = Math.min(preferredInputWidth, Math.max(60, width - 92));
        EditBox box = new EditBox(font, x + width - inputWidth, y, inputWidth, 20, Component.literal(label));
        box.setMaxLength(512);
        box.setValue(value == null ? "" : value);
        addRenderableWidget(box);
        labels.add(new Label(Component.literal(label), x, y));
        return box;
    }

    private static int integer(EditBox box, String name) {
        try { return Integer.parseInt(box.getValue().trim()); }
        catch (NumberFormatException exception) { throw new IllegalArgumentException(name + " muss eine ganze Zahl sein"); }
    }

    static Component toggleLabel(String name, boolean value) {
        return Component.literal(name + "  ").append(Component.literal(value ? "AN" : "AUS")
                .withStyle(value ? ChatFormatting.GREEN : ChatFormatting.GRAY));
    }

    private enum Section {
        GENERAL("Allgemein", "Grundfunktionen"),
        APPEARANCE("Anzeige", "Darstellung und Verlauf"),
        AUTOMATION("Automatik", "Regeln und automatische Antworten"),
        PRIVACY("Schutz", "Links und externe Dienste"),
        TWITCH("Twitch", "Twitch-Chat");

        private final String label;
        private final String heading;
        Section(String label, String heading) { this.label = label; this.heading = heading; }
    }

    private record Label(Component text, int x, int y) { }
}
