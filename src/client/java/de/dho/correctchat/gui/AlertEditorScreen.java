package de.dho.correctchat.gui;

import de.dho.correctchat.core.Localizer;

import de.dho.correctchat.CorrectChatClient;
import de.dho.correctchat.config.CorrectChatConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

/** Creates or edits one persistent local chat alarm. */
public final class AlertEditorScreen extends Screen {
    private final Screen parent;
    private final CorrectChatConfig config;
    private final CorrectChatConfig.ChatAlert alert;
    private final CorrectChatConfig.ChatAlert target;
    private final boolean newEntry;
    private final boolean applyImmediately;
    private final String preview;
    private final List<Label> labels = new ArrayList<>();
    private EditBox name, match, text, duration, cooldown, fontScale, sound, volume, pitch;
    private String error = "";
    private int page;

    static AlertEditorScreen manage(Screen parent, CorrectChatConfig config,
                                    CorrectChatConfig.ChatAlert alert, boolean newEntry) {
        return new AlertEditorScreen(parent, config, alert, newEntry, false, alert.match);
    }

    public static AlertEditorScreen fromMessage(Screen chatScreen, String message) {
        CorrectChatConfig copy = CorrectChatClient.config().copy();
        CorrectChatConfig.ChatAlert alert = new CorrectChatConfig.ChatAlert();
        String trigger = withoutTimestamp(message);
        alert.match = trigger;
        alert.name = "Alarm: " + shorten(trigger, 34);
        alert.text = "%message%";
        alert.sound.enabled = true;
        String server = CorrectChatClient.workspace() == null ? "" : CorrectChatClient.workspace().currentServerKey();
        if (!server.isBlank() && !"singleplayer".equalsIgnoreCase(server)) {
            alert.server.enabled = true;
            alert.server.address = stripPort(server);
        }
        return new AlertEditorScreen(chatScreen, copy, alert, true, true, message);
    }

    private AlertEditorScreen(Screen parent, CorrectChatConfig config, CorrectChatConfig.ChatAlert alert,
                              boolean newEntry, boolean applyImmediately, String preview) {
        super(Localizer.literal(newEntry ? "Alarm erstellen" : "Alarm bearbeiten"));
        this.parent = parent;
        this.config = config;
        this.target = newEntry ? null : alert;
        this.alert = alert.copy();
        this.newEntry = newEntry;
        this.applyImmediately = applyImmediately;
        this.preview = preview == null ? "" : preview;
    }

    @Override protected void init() {
        labels.clear();
        clearFields();
        int tabsWidth = Math.min(360, width - 24);
        int tabsX = (width - tabsWidth) / 2;
        pageButton(tabsX, 22, tabsWidth / 2 - 2, 0, "Auslöser & Anzeige");
        pageButton(tabsX + tabsWidth / 2 + 2, 22, tabsWidth / 2 - 2, 1, "Sound");
        int w = Math.min(250, width / 2 - 12);
        int left = width / 2 - w - 4;
        int right = width / 2 + 4;
        int top = 58;

        if (page == 0) {
            name = field(left, top, w, "Name", alert.name);
            match = field(left, top + 22, w, "Auslöser", alert.match);
            cycle(left, top + 44, w, "Abgleich", alert.matchType, CorrectChatConfig.MatchType.values(),
                    value -> alert.matchType = value, AlertEditorScreen::matchLabel);
            toggle(left, top + 66, w, "Groß/Klein beachten", alert.caseSensitive,
                    value -> alert.caseSensitive = value);
            text = field(left, top + 88, w, "Meldung", alert.text);

            cycle(right, top, w, "Anzeige", alert.display, CorrectChatConfig.AlertDisplay.values(),
                    value -> alert.display = value, AlertEditorScreen::displayLabel);
            cycle(right, top + 22, w, "Farbe", alert.color, usableColors(), value -> alert.color = value,
                    value -> Localizer.text(value.label));
            fontScale = field(right, top + 44, w, "Schriftgröße %", Integer.toString(alert.fontScalePercent));
            duration = field(right, top + 66, w, "Dauer (s)", Float.toString(alert.durationSeconds));
            cooldown = field(right, top + 88, w, "Cooldown (s)", Float.toString(alert.cooldownSeconds));
            toggle(right, top + 110, w, "Nur auf diesem Server", alert.server.enabled,
                    value -> alert.server.enabled = value);
            toggle(right, top + 132, w, "Aktiv", alert.enabled, value -> alert.enabled = value);
        } else {
            toggle(left, top, w, "Sound abspielen", alert.sound.enabled, value -> {
                alert.sound.enabled = value;
                setSoundFieldsActive(value);
            });
            sound = field(left, top + 22, w, "Sound-ID", alert.sound.id);
            volume = field(left, top + 44, w, "Lautstärke %", Integer.toString(alert.sound.volume));
            pitch = field(left, top + 66, w, "Pitch", Float.toString(alert.sound.pitch));
            setSoundFieldsActive(alert.sound.enabled);

            Component customSound = Localizer.literal(customSoundLabel());
            if (customSoundMissing()) customSound = customSound.copy().withStyle(ChatFormatting.RED);
            addRenderableWidget(Button.builder(customSound, button -> chooseCustomSound())
                    .bounds(right, top, w, 20).build());
            Button clearSound = Button.builder(Localizer.literal("Eigene Audiodatei entfernen"), button -> clearCustomSound())
                    .bounds(right, top + 22, w, 20).build();
            clearSound.active = !alert.sound.file.isBlank();
            addRenderableWidget(clearSound);
        }

        int bottom = height - 28;
        addRenderableWidget(Button.builder(Localizer.literal("Alarm speichern").withStyle(ChatFormatting.GREEN), b -> save())
                .bounds(width / 2 - 124, bottom, 120, 20).build());
        addRenderableWidget(Button.builder(Localizer.literal("Abbrechen"), b -> onClose())
                .bounds(width / 2 + 4, bottom, 120, 20).build());
    }

    @Override public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        graphics.centeredText(font, title, width / 2, 9, 0xFFFFFFFF);
        List<FormattedCharSequence> lines = font.split(Component.literal(preview), Math.min(500, width - 28));
        if (!lines.isEmpty()) graphics.centeredText(font, lines.getFirst(), width / 2, 46, 0xFFE3E9E6);
        else graphics.centeredText(font, Localizer.literal("%message% fügt die ausgelöste Nachricht ein."),
                width / 2, 46, 0xFF9DA6A1);
        labels.forEach(label -> graphics.text(font, label.text, label.x, label.y + 6, 0xFFE0E0E0, true));
        if (!error.isEmpty()) graphics.centeredText(font, Localizer.literal(error), width / 2, height - 41, 0xFFFF6060);
    }

    private void save() {
        try {
            storePage();
            if (alert.match.isBlank()) throw new IllegalArgumentException("Der Auslöser darf nicht leer sein");
            if (alert.text.isBlank()) throw new IllegalArgumentException("Die Meldung darf nicht leer sein");
            if (!alert.sound.file.isBlank() && !Files.isRegularFile(Path.of(alert.sound.file))) {
                throw new IllegalArgumentException("Die ausgewählte Audiodatei fehlt");
            }
            alert.normalize();
            if (newEntry) config.alerts.add(alert);
            else {
                int index = config.alerts.indexOf(target);
                if (index >= 0) config.alerts.set(index, alert);
            }
            if (applyImmediately) CorrectChatClient.applyConfig(config);
            minecraft.gui.setScreen(parent);
        } catch (IllegalArgumentException exception) {
            error = exception.getMessage();
        }
    }

    private void switchPage(int targetPage) {
        if (targetPage == page) return;
        try {
            storePage();
            page = targetPage;
            error = "";
            rebuildWidgets();
        } catch (IllegalArgumentException exception) { error = exception.getMessage(); }
    }

    private void storePage() {
        if (page == 0) {
            alert.name = name.getValue().trim();
            alert.match = match.getValue();
            alert.text = text.getValue();
            alert.fontScalePercent = integer(fontScale, "Schriftgröße");
            alert.durationSeconds = decimal(duration, "Dauer");
            alert.cooldownSeconds = decimal(cooldown, "Cooldown");
        } else {
            alert.sound.id = sound.getValue();
            alert.sound.volume = integer(volume, "Lautstärke");
            alert.sound.pitch = decimal(pitch, "Pitch");
        }
    }

    private void pageButton(int x, int y, int w, int targetPage, String label) {
        Component text = Localizer.literal(label);
        if (page == targetPage) text = text.copy().withStyle(ChatFormatting.GREEN);
        Button button = Button.builder(text, ignored -> switchPage(targetPage))
                .bounds(x, y, w, 20).build();
        button.active = page != targetPage;
        addRenderableWidget(button);
    }

    private void clearFields() {
        name = match = text = duration = cooldown = fontScale = sound = volume = pitch = null;
    }

    private void setSoundFieldsActive(boolean active) {
        if (sound != null) sound.active = active;
        if (volume != null) volume.active = active;
        if (pitch != null) pitch.active = active;
    }

    @Override public void onClose() { minecraft.gui.setScreen(parent); }
    @Override public boolean isPauseScreen() { return false; }

    private EditBox field(int x, int y, int w, String label, String value) {
        int input = Math.min(145, w - 98);
        EditBox box = new EditBox(font, x + w - input, y, input, 20, Localizer.literal(label));
        box.setMaxLength(1024);
        box.setValue(value == null ? "" : value);
        addRenderableWidget(box);
        labels.add(new Label(Localizer.literal(label), x, y));
        return box;
    }

    private void toggle(int x, int y, int w, String label, boolean current, Consumer<Boolean> setter) {
        boolean[] state = {current};
        addRenderableWidget(Button.builder(CorrectChatConfigScreen.toggleLabel(label, current), button -> {
            state[0] = !state[0];
            setter.accept(state[0]);
            button.setMessage(CorrectChatConfigScreen.toggleLabel(label, state[0]));
        }).bounds(x, y, w, 20).build());
    }

    private <T extends Enum<T>> void cycle(int x, int y, int w, String label, T current, T[] values,
                                            Consumer<T> setter, java.util.function.Function<T, String> formatter) {
        int[] index = {Math.max(0, Arrays.asList(values).indexOf(current))};
        addRenderableWidget(Button.builder(Localizer.literal(label + ": " + formatter.apply(values[index[0]])), button -> {
            index[0] = (index[0] + 1) % values.length;
            setter.accept(values[index[0]]);
            button.setMessage(Localizer.literal(label + ": " + formatter.apply(values[index[0]])));
        }).bounds(x, y, w, 20).build());
    }

    private static CorrectChatConfig.ChatColor[] usableColors() {
        return Arrays.stream(CorrectChatConfig.ChatColor.values())
                .filter(value -> value != CorrectChatConfig.ChatColor.NONE).toArray(CorrectChatConfig.ChatColor[]::new);
    }

    private static String matchLabel(CorrectChatConfig.MatchType type) {
        return Localizer.text(switch (type) {
            case EQUALS -> "Exakt";
            case CONTAINS -> "Enthält";
            case REGEX -> "Regex";
            case SIMPLIFIED_REGEX -> "Einfaches Muster";
        });
    }

    private static String displayLabel(CorrectChatConfig.AlertDisplay display) {
        return Localizer.text(switch (display) {
            case TITLE -> "Titel";
            case BOSSBAR -> "Bossbar";
            case ACTIONBAR -> "Actionbar";
        });
    }

    private static int integer(EditBox field, String name) {
        try { return Integer.parseInt(field.getValue().trim()); }
        catch (NumberFormatException exception) { throw new IllegalArgumentException(name + " ist keine Ganzzahl"); }
    }

    private static float decimal(EditBox field, String name) {
        try { return Float.parseFloat(field.getValue().trim().replace(',', '.')); }
        catch (NumberFormatException exception) { throw new IllegalArgumentException(name + " ist keine Zahl"); }
    }

    private static String shorten(String value, int max) {
        String singleLine = value.replace('\n', ' ').replace('\r', ' ').trim();
        return singleLine.length() <= max ? singleLine : singleLine.substring(0, max - 1) + "…";
    }

    private String customSoundLabel() {
        if (alert.sound.file.isBlank()) return "Eigene Audiodatei auswählen …";
        try { return "Datei: " + shorten(Path.of(alert.sound.file).getFileName().toString(), 28); }
        catch (RuntimeException exception) { return "Eigene Audiodatei auswählen …"; }
    }

    private boolean customSoundMissing() {
        if (alert.sound.file.isBlank()) return false;
        try { return !Files.isRegularFile(Path.of(alert.sound.file)); }
        catch (RuntimeException exception) { return true; }
    }

    private void chooseCustomSound() {
        try {
            storePage();
        } catch (IllegalArgumentException exception) {
            error = exception.getMessage();
            return;
        }
        try (MemoryStack stack = MemoryStack.stackPush()) {
            PointerBuffer filters = stack.mallocPointer(3);
            filters.put(stack.UTF8("*.mp3"));
            filters.put(stack.UTF8("*.wav"));
            filters.put(stack.UTF8("*.ogg"));
            filters.flip();
            String selected = TinyFileDialogs.tinyfd_openFileDialog(
                    Localizer.text("Alarm-Sound auswählen"), alert.sound.file, filters, "MP3, WAV oder OGG", false);
            if (selected == null) return;
            Path path = Path.of(selected).toAbsolutePath().normalize();
            String lower = path.getFileName().toString().toLowerCase(java.util.Locale.ROOT);
            if (!Files.isRegularFile(path) || !(lower.endsWith(".mp3") || lower.endsWith(".wav") || lower.endsWith(".ogg"))) {
                error = "Bitte eine vorhandene MP3-, WAV- oder OGG-Datei auswählen";
                return;
            }
            alert.sound.file = path.toString();
            error = "";
            rebuildWidgets();
        }
    }

    private void clearCustomSound() {
        try {
            storePage();
            alert.sound.file = "";
            error = "";
            rebuildWidgets();
        } catch (IllegalArgumentException exception) { error = exception.getMessage(); }
    }

    private static String withoutTimestamp(String message) {
        if (message == null) return "";
        return message.replaceFirst(
                "(?iu)^\\s*(?:[┃|]\\s*)?(?:\\[|\\()?\\d{1,2}:\\d{2}(?::\\d{2})?(?:\\]|\\))?\\s*(?:»|›|→|-|[|:])\\s*",
                "").stripLeading();
    }

    private static String stripPort(String address) {
        int colon = address.lastIndexOf(':');
        return colon > 0 && address.indexOf(':') == colon ? address.substring(0, colon) : address;
    }

    private record Label(Component text, int x, int y) { }
}
