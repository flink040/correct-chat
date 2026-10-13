package de.dho.correctchat.gui;

import de.dho.correctchat.core.Localizer;

import de.dho.correctchat.CorrectChatClient;
import de.dho.correctchat.config.ConfigManager;
import de.dho.correctchat.config.CorrectChatConfig;
import de.dho.correctchat.core.LegacyText;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

public final class CorrectChatConfigScreen extends Screen {
    private static final int ROW = 22;
    private static final int ACCENT = 0xFF71C9A3;
    private final Screen parent;
    private CorrectChatConfig draft;
    private final List<Label> labels = new ArrayList<>();
    private Section section = Section.GENERAL;
    private EditBox timeStyle, timeFormat, copyFormat, fontScale, maxLog, logRetention, maxResponses, responseWindow, endpoint;
    private EditBox twitchChannel, twitchUsername, twitchToken;
    private ColorDropdown colorDropdown;
    private String error = "";
    private String notice = "";
    private int appearancePreviewY = 249;
    private boolean compactNavigation;
    private int appearancePage;

    public CorrectChatConfigScreen(Screen parent, CorrectChatConfig draft) {
        super(Localizer.literal("Correct Chat"));
        this.parent = parent;
        this.draft = draft;
        Localizer.preview(draft.uiLanguage);
    }

    @Override
    protected void init() {
        labels.clear();
        clearFields();
        int contentWidth = Math.min(460, width - 24);
        int left = (width - contentWidth) / 2;
        int gap = 8;
        int navY = 37;
        compactNavigation = contentWidth < 450;
        if (compactNavigation) {
            int arrow = 34;
            addRenderableWidget(Button.builder(Localizer.literal("◀"), pressed -> switchSection(relativeSection(-1)))
                    .bounds(left, navY, arrow, 20).build());
            Button current = Button.builder(Localizer.literal(section.label).withStyle(ChatFormatting.GREEN), pressed -> { })
                    .bounds(left + arrow + 4, navY, contentWidth - arrow * 2 - 8, 20).build();
            current.active = false;
            addRenderableWidget(current);
            addRenderableWidget(Button.builder(Localizer.literal("▶"), pressed -> switchSection(relativeSection(1)))
                    .bounds(left + contentWidth - arrow, navY, arrow, 20).build());
        } else {
            int navWidth = (contentWidth - gap * (Section.values().length - 1)) / Section.values().length;
            for (int i = 0; i < Section.values().length; i++) {
                Section target = Section.values()[i];
                Component label = Localizer.literal(target.label);
                if (target == section) label = label.copy().withStyle(ChatFormatting.GREEN);
                Button button = Button.builder(label, pressed -> switchSection(target))
                        .bounds(left + i * (navWidth + gap), navY, navWidth, 20).build();
                button.active = target != section;
                addRenderableWidget(button);
            }
        }

        int top = 79;
        switch (section) {
            case GENERAL -> general(left, top, contentWidth);
            case APPEARANCE -> appearance(left, top, contentWidth);
            case AUTOMATION -> automation(left, top, contentWidth);
            case AD_BLOCKER -> adBlocker(left, top, contentWidth);
            case PRIVACY -> privacy(left, top, contentWidth);
            case TWITCH -> twitch(left, top, contentWidth);
        }

        int bottom = height - 28;
        addRenderableWidget(Button.builder(Localizer.literal("Speichern").withStyle(ChatFormatting.GREEN), button -> save())
                .bounds(width / 2 - 104, bottom, 100, 20).build());
        addRenderableWidget(Button.builder(Localizer.literal("Abbrechen"), button -> onClose())
                .bounds(width / 2 + 4, bottom, 100, 20).build());
    }

    private void general(int x, int y, int w) {
        int half = (w - 8) / 2;
        toggle(x, y, half, "Mod aktiviert", () -> draft.enabled, value -> draft.enabled = value);
        toggle(x + half + 8, y, half, "Befehlsschutz", () -> draft.antiCommandChoker, value -> draft.antiCommandChoker = value); y += ROW;
        addRenderableWidget(Button.builder(languageButtonLabel(), button -> {
            CorrectChatConfig.UiLanguage[] languages = CorrectChatConfig.UiLanguage.values();
            draft.uiLanguage = languages[(draft.uiLanguage.ordinal() + 1) % languages.length];
            Localizer.preview(draft.uiLanguage);
            rebuildWidgets();
        }).bounds(x, y, w, 20).build()); y += ROW;
        toggle(x, y, w, "Chat-Tabs und Zusatzfenster", () -> draft.chatTabsEnabled, value -> draft.chatTabsEnabled = value); y += ROW + 4;
        List<CorrectChatConfig.ChatTab> activeTabs = draft.tabsFor(CorrectChatClient.workspace().currentServerKey());
        List<CorrectChatConfig.ChatWindow> activeWindows = draft.windowsFor(CorrectChatClient.workspace().currentServerKey());
        addRenderableWidget(Button.builder(Localizer.literal("Tabs verwalten (" + activeTabs.size() + ")"),
                button -> open(new ChatTabsScreen(this, draft)))
                .bounds(x, y, half, 20).build());
        addRenderableWidget(Button.builder(Localizer.literal("Fenster verwalten (" + activeWindows.size() + ")"),
                button -> open(new ChatWindowsScreen(this, draft)))
                .bounds(x + half + 8, y, half, 20).build());
        y += ROW;
        addRenderableWidget(Button.builder(Localizer.literal("Serverprofile (" + draft.serverProfiles.size() + ")"),
                button -> open(new ServerProfilesScreen(this, draft))).bounds(x, y, w, 20).build());
        y += ROW + 4;
        labels.add(new Label(Localizer.literal("Settingsdatei"), x, y - 13));
        int third = (w - 12) / 3;
        addRenderableWidget(Button.builder(Localizer.literal("Importieren"), button -> importSettings())
                .bounds(x, y, third, 20).build());
        addRenderableWidget(Button.builder(Localizer.literal("Exportieren"), button -> exportSettings())
                .bounds(x + third + 6, y, third, 20).build());
        addRenderableWidget(Button.builder(Localizer.literal("Löschen").withStyle(ChatFormatting.RED),
                button -> confirmDeleteSettings()).bounds(x + (third + 6) * 2, y, w - (third + 6) * 2, 20).build());
    }

    private void appearance(int x, int y, int w) {
        int half = (w - 8) / 2;
        int right = x + half + 8;
        Component displayLabel = Localizer.literal("Darstellung");
        if (appearancePage == 0) displayLabel = displayLabel.copy().withStyle(ChatFormatting.GREEN);
        Button display = Button.builder(displayLabel, button -> switchAppearancePage(0))
                .bounds(x, y, half, 20).build();
        display.active = appearancePage != 0;
        addRenderableWidget(display);
        Component historyLabel = Localizer.literal("Verlauf");
        if (appearancePage == 1) historyLabel = historyLabel.copy().withStyle(ChatFormatting.GREEN);
        Button history = Button.builder(historyLabel, button -> switchAppearancePage(1))
                .bounds(right, y, half, 20).build();
        history.active = appearancePage != 1;
        addRenderableWidget(history);
        int controlsY = y + 24;

        if (appearancePage == 0) {
            colorDropdown = new ColorDropdown(x, controlsY + ROW * 3, half, draft.defaultChatColor);
            addWidget(colorDropdown);
            toggle(x, controlsY, half, "Zeitstempel", () -> draft.chatTime, value -> {
                draft.chatTime = value;
                if (timeFormat != null) timeFormat.active = value;
                if (timeStyle != null) timeStyle.active = value;
            });
            timeFormat = field(x, controlsY + ROW, half, "Zeitformat", draft.timeFormat, 100);
            timeFormat.active = draft.chatTime;
            timeStyle = field(x, controlsY + ROW * 2, half, "Darstellung", draft.timeStyle, 120);
            timeStyle.active = draft.chatTime;
            fontScale = field(x, controlsY + ROW * 4, half, "Schriftgröße (%)",
                    Integer.toString((int)Math.round(draft.primaryChatFontScalePercent > 0
                            ? draft.primaryChatFontScalePercent : CorrectChatClient.workspace().primaryChatScale() * 100.0D)), 64);

            toggle(right, controlsY, half, "Copy vor Nachricht", () -> draft.copyButton, value -> {
                draft.copyButton = value;
                if (copyFormat != null) copyFormat.active = value;
            });
            copyFormat = field(right, controlsY + ROW, half, "Kopier-Text", draft.copyFormat, 120);
            copyFormat.active = draft.copyButton;
            toggle(right, controlsY + ROW * 2, half, "Eingabe-Vorschau",
                    () -> draft.chatMessagePreview, value -> draft.chatMessagePreview = value);
            toggle(right, controlsY + ROW * 3, half, "Ungelesen-Zähler", () -> draft.unreadBadges, value -> draft.unreadBadges = value);
            toggle(right, controlsY + ROW * 4, half, "Erwähnungen", () -> draft.mentionHighlight, value -> draft.mentionHighlight = value);
            appearancePreviewY = controlsY + ROW * 5 + 5;
        } else {
            toggle(x, controlsY, w, "Chatlog speichern", () -> draft.chatLog, value -> {
                draft.chatLog = value;
                if (maxLog != null) maxLog.active = value;
            });
            maxLog = field(x, controlsY + ROW, w, "Max. gespeicherte Nachrichten",
                    Integer.toString(draft.maxStoredMessages), 90);
            maxLog.active = draft.chatLog;
            labels.add(new Label(Localizer.literal("Die Chat-Historie bleibt lokal und wird beim Neustart geleert."), x, controlsY + ROW * 2 + 5));
        }
    }

    private void automation(int x, int y, int w) {
        int half = (w - 8) / 2;
        toggle(x, y, half, "Textersetzungen", () -> draft.textReplacement, value -> draft.textReplacement = value);
        toggle(x + half + 8, y, half, "Chat-Listener", () -> draft.chatListeners, value -> draft.chatListeners = value); y += ROW;
        addRenderableWidget(Button.builder(Localizer.literal("Ersetzungen verwalten (" + draft.replacements.size() + ")"),
                button -> open(new RuleListScreen(this, draft, true))).bounds(x, y, half, 20).build());
        addRenderableWidget(Button.builder(Localizer.literal("Listener verwalten (" + draft.listeners.size() + ")"),
                button -> open(new RuleListScreen(this, draft, false))).bounds(x + half + 8, y, half, 20).build()); y += ROW;
        addRenderableWidget(Button.builder(Localizer.literal("Alarme verwalten (" + draft.alerts.size() + ")"),
                button -> open(new AlertListScreen(this, draft))).bounds(x, y, half, 20).build());
        addRenderableWidget(Button.builder(Localizer.literal("Eigene Sounds stoppen"), button -> {
            if (CorrectChatClient.engine() != null) CorrectChatClient.engine().stopCustomSounds();
            notice = "Laufende und wartende eigene Sounds wurden gestoppt.";
        }).bounds(x + half + 8, y, half, 20).build()); y += ROW;
        toggle(x, y, w, "Listener-Antispam", () -> draft.antiSpam, value -> {
            draft.antiSpam = value;
            if (maxResponses != null) maxResponses.active = value;
            if (responseWindow != null) responseWindow.active = value;
        }); y += ROW;
        y += 8;
        maxResponses = field(x, y, half, "Antworten", Integer.toString(draft.maxResponses), 64);
        responseWindow = field(x + half + 8, y, half, "Zeitraum (Min.)", Integer.toString(draft.responseWindowMinutes), 64);
        maxResponses.active = draft.antiSpam;
        responseWindow.active = draft.antiSpam;
    }

    private void adBlocker(int x, int y, int w) {
        addRenderableWidget(Button.builder(
                Localizer.literal("Ausgeblendete Werbung verwalten (" + draft.blockedAdvertisements.size() + ")"),
                button -> open(new AdBlockerScreen(this, draft))).bounds(x, y, w, 20).build());
        labels.add(new Label(Localizer.literal("Per Rechtsklick markierte Nachrichten werden in allen Tabs ausgeblendet."), x, y + 28));
        labels.add(new Label(Localizer.literal("Hier kannst du einzelne Nachrichten wieder freischalten."), x, y + 42));
    }

    private void privacy(int x, int y, int w) {
        toggle(x, y, w, "Links vor dem Senden prüfen", () -> draft.linkFilter, value -> draft.linkFilter = value); y += ROW;
        addRenderableWidget(Button.builder(Localizer.literal("Erlaubte URLs verwalten (" + draft.linkExceptions.size() + ")"),
                button -> open(new LinkExceptionsScreen(this, draft))).bounds(x, y, w, 20).build()); y += ROW + 4;
        int half = (w - 8) / 2;
        addRenderableWidget(Button.builder(Localizer.literal("Chatlog-Format: " + draft.chatLogFormat), button -> {
            draft.chatLogFormat = draft.chatLogFormat == CorrectChatConfig.ChatLogFormat.JSON
                    ? CorrectChatConfig.ChatLogFormat.TEXT : CorrectChatConfig.ChatLogFormat.JSON;
            button.setMessage(Localizer.literal("Chatlog-Format: " + draft.chatLogFormat));
        }).bounds(x, y, half, 20).build());
        logRetention = field(x + half + 8, y, half, "Max. Dateien", Integer.toString(draft.chatLogRetention), 70); y += ROW + 4;
        toggle(x, y, w, "Externen Chatlog-Upload erlauben", () -> draft.uploadEnabled, value -> {
            draft.uploadEnabled = value;
            if (endpoint != null) endpoint.active = value;
        }); y += ROW;
        endpoint = field(x, y, w, "Upload-URL", draft.uploadEndpoint, 300);
        endpoint.active = draft.uploadEnabled;
    }

    private void twitch(int x, int y, int w) {
        toggle(x, y, w, "Twitch-Chat verbinden", () -> draft.twitchEnabled, value -> draft.twitchEnabled = value); y += ROW;
        twitchChannel = field(x, y, w, "Kanal", draft.twitchChannel, 260); y += ROW;
        twitchUsername = field(x, y, w, "Twitch-Benutzer", draft.twitchUsername, 260); y += ROW;
        twitchToken = field(x, y, w, "Neuer OAuth-Token", "", 260); y += ROW;
        addRenderableWidget(Button.builder(Localizer.literal(draft.twitchOauthToken.isBlank()
                        ? "Kein Token gespeichert · nur Lesen"
                        : "Token gespeichert · Klicken zum Löschen"), button -> {
                    storeVisibleFields();
                    draft.twitchOauthToken = "";
                    rebuildWidgets();
                }).bounds(x, y, w, 20).build());
        y += ROW;
        Button credentialExport = Button.builder(
                Localizer.literal("Settings-Export mit OAuth-Token …").withStyle(ChatFormatting.RED),
                button -> confirmCredentialExport()).bounds(x, y, w, 20).build();
        credentialExport.active = !draft.twitchOauthToken.isBlank() || !twitchToken.getValue().isBlank();
        addRenderableWidget(credentialExport);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        int contentWidth = Math.min(460, width - 24);
        int left = (width - contentWidth) / 2;
        int gap = 8;
        int navWidth = (contentWidth - gap * (Section.values().length - 1)) / Section.values().length;
        int selectedX = compactNavigation ? left + 38 : left + section.ordinal() * (navWidth + gap);
        int selectedWidth = compactNavigation ? contentWidth - 76 : navWidth;
        graphics.centeredText(font, title, width / 2, 10, 0xFFFFFFFF);
        graphics.centeredText(font, Localizer.literal("F8 öffnen · Speichern übernimmt alle Änderungen"), width / 2, 22, 0xFF9DA6A1);
        graphics.fill(selectedX, 58, selectedX + selectedWidth, 60, ACCENT);
        graphics.fill(left, 63, left + contentWidth, 65, ACCENT);
        String heading = section == Section.TWITCH ? section.heading + " · " + twitchStatus() : section.heading;
        if (!error.isEmpty()) graphics.text(font, Localizer.literal(error), left, 68, 0xFFFF6B6B, true);
        else if (!notice.isEmpty()) graphics.text(font, Localizer.literal(notice), left, 68, ACCENT, true);
        else graphics.text(font, Localizer.literal(heading), left, 68, 0xFFFFFFFF, true);
        labels.forEach(label -> graphics.text(font, label.text, label.x, label.y + 6, 0xFFD9DFDC, true));
        if (section == Section.APPEARANCE) {
            renderPreviews(graphics, left, contentWidth);
            if (colorDropdown != null) colorDropdown.extractRenderState(graphics, mouseX, mouseY, delta);
        }
        if (section == Section.AUTOMATION) {
            helperText(graphics, Localizer.literal("Antwortlimit pro Listener und Zeitraum; Befehle (/) sind frei."), left + 4, 166, 0xFF9DA6A1);
        }
        if (section == Section.PRIVACY) {
            helperText(graphics, Localizer.literal("Lokale Exporte können private Nachrichten enthalten."), left + 4, 215, 0xFFFFC266);
            helperText(graphics, Localizer.literal("Externe Uploads senden den Inhalt an die angegebene URL."), left + 4, 227, 0xFFFFC266);
        }
        if (section == Section.TWITCH) {
            helperText(graphics, Localizer.literal("Ohne Token kann der Chat gelesen, aber nicht beschrieben werden."), left + 4, 219, 0xFF9DA6A1);
        }
    }

    @Override public boolean isPauseScreen() { return false; }
    @Override public void onClose() {
        Localizer.clearPreview();
        minecraft.gui.setScreen(parent);
    }

    private Component languageButtonLabel() {
        return Localizer.literal("Sprache: " + Localizer.languageLabel(draft.uiLanguage));
    }

    private void switchSection(Section target) {
        if (target == section) return;
        try {
            storeVisibleFields();
            error = "";
            notice = "";
            section = target;
            rebuildWidgets();
        } catch (IllegalArgumentException exception) {
            error = exception.getMessage();
        }
    }

    private Section relativeSection(int offset) {
        Section[] sections = Section.values();
        return sections[Math.floorMod(section.ordinal() + offset, sections.length)];
    }

    private void switchAppearancePage(int target) {
        if (target == appearancePage) return;
        try {
            storeVisibleFields();
            appearancePage = target;
            error = "";
            notice = "";
            rebuildWidgets();
        } catch (IllegalArgumentException exception) { error = exception.getMessage(); }
    }

    private void save() {
        try {
            storeVisibleFields();
            draft.normalize();
            CorrectChatClient.applyConfig(draft);
            Localizer.clearPreview();
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

    private void importSettings() {
        try {
            storeVisibleFields();
            String selected = chooseSettingsFile(false);
            if (selected == null) return;
            draft = ConfigManager.importFrom(Path.of(selected));
            error = "";
            notice = "Settings importiert · mit Speichern übernehmen";
            rebuildWidgets();
        } catch (Exception exception) {
            notice = "";
            error = fileError("Import fehlgeschlagen", exception);
        }
    }

    private void exportSettings() {
        try {
            storeVisibleFields();
            exportSettingsToFile(false);
        } catch (Exception exception) {
            notice = "";
            error = fileError("Export fehlgeschlagen", exception);
        }
    }

    private void confirmCredentialExport() {
        try {
            storeVisibleFields();
            if (draft.twitchOauthToken.isBlank()) {
                error = "Kein OAuth-Token gespeichert";
                return;
            }
            minecraft.gui.setScreen(new ConfirmScreen(confirmed -> {
                minecraft.gui.setScreen(this);
                if (confirmed) exportSettingsToFile(true);
            }, Localizer.literal("OAuth-Token wirklich exportieren?"),
                    Localizer.literal("Die Exportdatei enthält danach Twitch-Zugangsdaten im Klartext und darf nicht geteilt werden."),
                    Localizer.literal("Token exportieren").withStyle(ChatFormatting.RED),
                    Localizer.literal("Abbrechen")));
        } catch (Exception exception) {
            notice = "";
            error = fileError("Export fehlgeschlagen", exception);
        }
    }

    private void exportSettingsToFile(boolean includeCredentials) {
        try {
            String selected = chooseSettingsFile(true);
            if (selected == null) return;
            Path destination = Path.of(selected);
            if (!destination.getFileName().toString().toLowerCase(java.util.Locale.ROOT).endsWith(".json")) {
                destination = destination.resolveSibling(destination.getFileName() + ".json");
            }
            ConfigManager.exportTo(draft, destination, includeCredentials);
            error = "";
            notice = "Settings exportiert: " + destination.getFileName()
                    + (includeCredentials ? " · enthält OAuth-Token" : " · ohne Zugangsdaten");
        } catch (Exception exception) {
            notice = "";
            error = fileError("Export fehlgeschlagen", exception);
        }
    }

    private void confirmDeleteSettings() {
        minecraft.gui.setScreen(new ConfirmScreen(confirmed -> {
            if (!confirmed) {
                minecraft.gui.setScreen(this);
                return;
            }
            try {
                draft = CorrectChatClient.resetConfig();
                error = "";
                notice = "Einstellungen gelöscht · Standardwerte wiederhergestellt";
            } catch (Exception exception) {
                notice = "";
                error = fileError("Einstellungen konnten nicht gelöscht werden", exception);
            }
            minecraft.gui.setScreen(this);
        }, Localizer.literal("Alle Einstellungen löschen?"),
                Localizer.literal("Tabs, Fenster, Regeln, Profile und Zugangsdaten werden dauerhaft auf Standardwerte zurückgesetzt."),
                Localizer.literal("Endgültig löschen"), Localizer.literal("Abbrechen")));
    }

    private static String chooseSettingsFile(boolean save) {
        String suggested = ConfigManager.path().resolveSibling("correct-chat-settings.json").toString();
        try (MemoryStack stack = MemoryStack.stackPush()) {
            PointerBuffer filters = stack.mallocPointer(1);
            filters.put(stack.UTF8("*.json")).flip();
            return save
                    ? TinyFileDialogs.tinyfd_saveFileDialog(Localizer.text("Correct-Chat-Settings exportieren"), suggested,
                    filters, Localizer.text("JSON-Dateien"))
                    : TinyFileDialogs.tinyfd_openFileDialog(Localizer.text("Correct-Chat-Settings importieren"), suggested,
                    filters, Localizer.text("JSON-Dateien"), false);
        }
    }

    private static String fileError(String prefix, Exception exception) {
        Throwable cause = exception;
        while (cause.getCause() != null && cause.getCause() != cause) cause = cause.getCause();
        String detail = cause.getMessage();
        if (detail == null || detail.isBlank()) detail = exception instanceof IOException ? "Dateifehler" : "Ungültige Settingsdatei";
        return prefix + ": " + detail;
    }

    private void storeVisibleFields() {
        if (timeStyle != null) draft.timeStyle = timeStyle.getValue();
        if (timeFormat != null) {
            draft.timeFormat = timeFormat.getValue();
            new java.text.SimpleDateFormat(draft.timeFormat);
        }
        if (copyFormat != null) draft.copyFormat = copyFormat.getValue();
        if (fontScale != null) {
            int value = integer(fontScale, "Schriftgröße");
            if (value < 25 || value > 200) {
                throw new IllegalArgumentException("Schriftgröße muss zwischen 25 und 200 % liegen");
            }
            draft.primaryChatFontScalePercent = value;
        }
        if (maxLog != null) draft.maxStoredMessages = integer(maxLog, "Maximale Nachrichten");
        if (logRetention != null) draft.chatLogRetention = integer(logRetention, "Maximale Chatlog-Dateien");
        if (maxResponses != null) draft.maxResponses = integer(maxResponses, "Antworten");
        if (responseWindow != null) draft.responseWindowMinutes = integer(responseWindow, "Zeitraum");
        if (endpoint != null) {
            draft.uploadEndpoint = endpoint.getValue().trim();
            if (draft.uploadEnabled && !draft.uploadEndpoint.isBlank()) {
                java.net.URI uploadUri;
                try {
                    uploadUri = java.net.URI.create(draft.uploadEndpoint);
                } catch (IllegalArgumentException exception) {
                    throw new IllegalArgumentException("Upload-URL ist ungültig");
                }
                if (!"https".equalsIgnoreCase(uploadUri.getScheme())) {
                    throw new IllegalArgumentException("Upload-URL muss HTTPS verwenden");
                }
            }
        }
        if (twitchChannel != null) draft.twitchChannel = twitchChannel.getValue();
        if (twitchUsername != null) draft.twitchUsername = twitchUsername.getValue();
        if (twitchToken != null && !twitchToken.getValue().isBlank()) draft.twitchOauthToken = twitchToken.getValue();
    }

    private void clearFields() {
        timeStyle = null; timeFormat = null; copyFormat = null; fontScale = null; maxLog = null; logRetention = null;
        maxResponses = null; responseWindow = null; endpoint = null;
        twitchChannel = null; twitchUsername = null; twitchToken = null;
        colorDropdown = null;
    }

    private void renderPreviews(GuiGraphicsExtractor graphics, int x, int w) {
        if (timeStyle == null || timeFormat == null || copyFormat == null) return;
        int y = appearancePreviewY;
        if (y + 19 >= height - 44) return;
        graphics.fill(x, y, x + w, y + 19, 0x900B1110);
        graphics.fill(x, y, x + 2, y + 19, ACCENT);
        String time;
        try {
            time = new java.text.SimpleDateFormat(timeFormat.getValue()).format(new java.util.Date());
        } catch (IllegalArgumentException exception) {
            time = "ungültiges Zeitformat";
        }
        Component preview = Localizer.literal("Vorschau: ")
                .append(LegacyText.parse(copyFormat.getValue()))
                .append(LegacyText.parse(timeStyle.getValue().replace("%time%", time)))
                .append(LegacyText.parse(draft.defaultChatColor.code + "<Spieler> Nachricht"));
        graphics.text(font, preview, x + 7, y + 6, 0xFFE8ECEA, false);
    }

    private void helperText(GuiGraphicsExtractor graphics, Component text, int x, int y, int color) {
        if (y + font.lineHeight < height - 44) graphics.text(font, text, x, y, color, false);
    }

    private static String twitchStatus() {
        return Localizer.text(CorrectChatClient.twitch() == null ? "nicht initialisiert" : CorrectChatClient.twitch().statusText());
    }

    private static Component chatColorLabel(CorrectChatConfig.ChatColor color) {
        String translated = Localizer.text(color.label);
        Component label = Localizer.literal(color.code.isEmpty() ? translated : color.code + " " + translated);
        return color.formatting == null ? label : label.copy().withStyle(color.formatting);
    }

    private final class ColorDropdown extends AbstractButton {
        private static final int OPTION_HEIGHT = 18;
        private static final int COLUMNS = 2;
        private boolean open;

        private ColorDropdown(int x, int y, int width, CorrectChatConfig.ChatColor initialColor) {
            super(x, y, width, 20, colorDropdownLabel(initialColor, false));
        }

        @Override
        public void onPress(InputWithModifiers input) {
            open = !open;
            setMessage(colorDropdownLabel(draft.defaultChatColor, open));
        }

        @Override
        public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
            if (open && isValidClickButton(event.buttonInfo())) {
                int option = optionAt(event.x(), event.y());
                if (option >= 0) {
                    playDownSound(minecraft.getSoundManager());
                    draft.defaultChatColor = CorrectChatConfig.ChatColor.values()[option];
                    setMessage(colorDropdownLabel(draft.defaultChatColor, false));
                    open = false;
                    return true;
                }
                if (!isMouseOver(event.x(), event.y())) {
                    open = false;
                    setMessage(colorDropdownLabel(draft.defaultChatColor, false));
                }
            }
            return super.mouseClicked(event, doubleClick);
        }

        @Override
        protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
            boolean headerHovered = isMouseOver(mouseX, mouseY);
            int border = open ? ACCENT : headerHovered ? 0xFF66736E : 0xFF3B4541;
            int headerBackground = !active ? 0xFF111614 : open ? 0xFF203D32 : headerHovered ? 0xFF29332F : 0xFF1B211F;
            int textColor = active ? 0xFFE8ECEA : 0xFF777E7B;

            graphics.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), border);
            graphics.fill(getX() + 1, getY() + 1, getX() + getWidth() - 1, getY() + getHeight() - 1, headerBackground);
            graphics.centeredText(font, getMessage(), getX() + getWidth() / 2, getY() + 6, textColor);
            if (!open) return;

            CorrectChatConfig.ChatColor[] colors = CorrectChatConfig.ChatColor.values();
            int rows = (colors.length + COLUMNS - 1) / COLUMNS;
            int popupY = popupY(rows);
            int cellWidth = getWidth() / COLUMNS;
            graphics.fill(getX() - 1, popupY - 1, getX() + getWidth() + 1,
                    popupY + rows * OPTION_HEIGHT + 1, 0xFF050807);

            for (int index = 0; index < colors.length; index++) {
                int column = index % COLUMNS;
                int row = index / COLUMNS;
                int optionX = getX() + column * cellWidth;
                int optionY = popupY + row * OPTION_HEIGHT;
                int optionWidth = column == COLUMNS - 1 ? getWidth() - cellWidth : cellWidth;
                boolean hovered = mouseX >= optionX && mouseX < optionX + optionWidth
                        && mouseY >= optionY && mouseY < optionY + OPTION_HEIGHT;
                int background = colors[index] == draft.defaultChatColor
                        ? 0xFF285643 : hovered ? 0xFF34433E : 0xFF171D1B;
                graphics.fill(optionX, optionY, optionX + optionWidth - 1, optionY + OPTION_HEIGHT - 1, background);
                graphics.text(font, chatColorLabel(colors[index]), optionX + 5, optionY + 5, 0xFFE8ECEA, false);
            }
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {
            defaultButtonNarrationText(output);
        }

        private int optionAt(double mouseX, double mouseY) {
            CorrectChatConfig.ChatColor[] colors = CorrectChatConfig.ChatColor.values();
            int rows = (colors.length + COLUMNS - 1) / COLUMNS;
            int popupY = popupY(rows);
            if (mouseX < getX() || mouseX >= getX() + getWidth()
                    || mouseY < popupY || mouseY >= popupY + rows * OPTION_HEIGHT) return -1;
            int cellWidth = getWidth() / COLUMNS;
            int column = Math.min(COLUMNS - 1, (int)(mouseX - getX()) / cellWidth);
            int row = (int)(mouseY - popupY) / OPTION_HEIGHT;
            int index = row * COLUMNS + column;
            return index < colors.length ? index : -1;
        }

        private int popupY(int rows) {
            int below = getY() + getHeight() + 2;
            return below + rows * OPTION_HEIGHT <= height - 6
                    ? below : Math.max(6, getY() - rows * OPTION_HEIGHT - 2);
        }

    }

    private static Component colorDropdownLabel(CorrectChatConfig.ChatColor color, boolean open) {
        return Localizer.literal("Standardfarbe: ")
                .append(chatColorLabel(color))
                .append(Localizer.literal(open ? "  ▴" : "  ▾"));
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
        EditBox box = new EditBox(font, x + width - inputWidth, y, inputWidth, 20, Localizer.literal(label));
        box.setMaxLength(512);
        box.setValue(value == null ? "" : value);
        addRenderableWidget(box);
        labels.add(new Label(Localizer.literal(label), x, y));
        return box;
    }

    private static int integer(EditBox box, String name) {
        try { return Integer.parseInt(box.getValue().trim()); }
        catch (NumberFormatException exception) { throw new IllegalArgumentException(name + " muss eine ganze Zahl sein"); }
    }

    static Component toggleLabel(String name, boolean value) {
        return Localizer.literal(name + "  ").append(Localizer.literal(value ? "AN" : "AUS")
                .withStyle(value ? ChatFormatting.GREEN : ChatFormatting.GRAY));
    }

    private enum Section {
        GENERAL("Allgemein", "Grundfunktionen"),
        APPEARANCE("Anzeige", "Darstellung und Verlauf"),
        AUTOMATION("Automatik", "Regeln und automatische Antworten"),
        AD_BLOCKER("Ad-Blocker", "Ausgeblendete Werbung"),
        PRIVACY("Schutz", "Links und externe Dienste"),
        TWITCH("Twitch", "Twitch-Chat");

        private final String label;
        private final String heading;
        Section(String label, String heading) { this.label = label; this.heading = heading; }
    }

    private record Label(Component text, int x, int y) { }
}
