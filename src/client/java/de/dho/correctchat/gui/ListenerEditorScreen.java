package de.dho.correctchat.gui;

import de.dho.correctchat.core.Localizer;

import de.dho.correctchat.config.CorrectChatConfig;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

final class ListenerEditorScreen extends Screen {
    private final Screen parent;
    private final CorrectChatConfig.ChatListener entry;
    private final Consumer<CorrectChatConfig.ChatListener> commit;
    private final List<Label> labels = new ArrayList<>();
    private EditBox name, match, response, delay, server, blacklist, sound, volume, pitch, cooldown, group;
    private String error = "";
    private int page;

    ListenerEditorScreen(Screen parent, CorrectChatConfig.ChatListener entry,
                         Consumer<CorrectChatConfig.ChatListener> commit) {
        super(Localizer.literal("Chat-Listener bearbeiten"));
        this.parent = parent;
        this.entry = entry.copy();
        this.commit = commit;
    }

    @Override protected void init() {
        labels.clear();
        clearFields();
        int tabsWidth = Math.min(360, width - 24);
        int tabsX = (width - tabsWidth) / 2;
        pageButton(tabsX, 22, tabsWidth / 2 - 2, 0, "Regel & Antwort");
        pageButton(tabsX + tabsWidth / 2 + 2, 22, tabsWidth / 2 - 2, 1, "Sound & Cooldown");

        int w = Math.min(230, width / 2 - 12), left = width / 2 - w - 4, right = width / 2 + 4, top = 56;
        if (page == 0) {
            name = field(left, top, w, "Name", entry.name);
            match = field(left, top + 24, w, "Muster", entry.match);
            response = field(left, top + 48, w, "Antwort", entry.response);
            delay = field(left, top + 72, w, "Verzögerung (s)", Float.toString(entry.delaySeconds));
            cycle(left, top + 96, w, "Vergleich", entry.matchType, CorrectChatConfig.MatchType.values(), value -> entry.matchType = value);

            toggle(right, top, w, "Aktiv", entry.enabled, value -> entry.enabled = value);
            toggle(right, top + 24, w, "Nachricht ausblenden", entry.cancel, value -> entry.cancel = value);
            toggle(right, top + 48, w, "Groß/Klein beachten", entry.caseSensitive, value -> entry.caseSensitive = value);
            toggle(right, top + 72, w, "Servergebunden", entry.server.enabled, value -> {
                entry.server.enabled = value;
                if (server != null) server.active = value;
            });
            toggle(right, top + 96, w, "Im Einzelspieler", entry.server.singlePlayer, value -> entry.server.singlePlayer = value);
            server = field(right, top + 120, w, "Serveradresse", entry.server.address);
            server.active = entry.server.enabled;
        } else {
            blacklist = field(left, top, w, "Blacklist (Komma)", String.join(",", entry.blacklist));
            toggle(left, top + 24, w, "Sound", entry.sound.enabled, value -> {
                entry.sound.enabled = value;
                setSoundFieldsActive(value);
            });
            sound = field(left, top + 48, w, "Sound-ID", entry.sound.id);
            volume = field(left, top + 72, w, "Lautstärke %", Integer.toString(entry.sound.volume));
            pitch = field(left, top + 96, w, "Pitch", Float.toString(entry.sound.pitch));
            setSoundFieldsActive(entry.sound.enabled);

            toggle(right, top, w, "Cooldown", entry.cooldown.enabled, value -> {
                entry.cooldown.enabled = value;
                setCooldownFieldsActive(value);
            });
            cooldown = field(right, top + 24, w, "Cooldown (s)", Float.toString(entry.cooldown.seconds));
            cycle(right, top + 48, w, "Cooldown-Typ", entry.cooldown.type, CorrectChatConfig.CooldownType.values(), value -> entry.cooldown.type = value);
            group = field(right, top + 72, w, "Regex-Gruppe", Integer.toString(entry.cooldown.group));
            setCooldownFieldsActive(entry.cooldown.enabled);
        }

        int bottom = height - 28;
        addRenderableWidget(Button.builder(Localizer.literal("Übernehmen"), b -> save()).bounds(width / 2 - 104, bottom, 100, 20).build());
        addRenderableWidget(Button.builder(Localizer.literal("Zurück"), b -> onClose()).bounds(width / 2 + 4, bottom, 100, 20).build());
    }

    @Override public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float d) {
        super.extractRenderState(g, mx, my, d);
        g.centeredText(font, title, width / 2, 9, 0xFFFFFFFF);
        String help = page == 0 ? switch (entry.matchType) {
            case REGEX -> RegexExplainer.explain(match == null ? entry.match : match.getValue());
            case SIMPLIFIED_REGEX -> RegexExplainer.explainSimplified(match == null ? entry.match : match.getValue());
            default -> "Vergleich: " + (entry.matchType == CorrectChatConfig.MatchType.EQUALS
                    ? "Die gesamte Nachricht muss dem Muster entsprechen."
                    : "Die Nachricht muss den Suchtext enthalten.");
        } : "Sound und Cooldown werden erst nach einem passenden Treffer ausgeführt.";
        List<FormattedCharSequence> helpLines = font.split(Localizer.literal(help), Math.max(120, width - 24));
        if (!helpLines.isEmpty()) g.centeredText(font, helpLines.getFirst(), width / 2, 45, 0xFFAAAAAA);
        labels.forEach(label -> g.text(font, label.text, label.x, label.y + 6, 0xFFE0E0E0, true));
        if (!error.isEmpty()) g.centeredText(font, Localizer.literal(error), width / 2, height - 41, 0xFFFF6060);
    }
    @Override public void onClose() { minecraft.gui.setScreen(parent); }
    @Override public boolean isPauseScreen() { return false; }

    private void save() {
        try {
            storePage();
            entry.normalize(); commit.accept(entry); onClose();
        } catch (IllegalArgumentException exception) { error = exception.getMessage(); }
    }
    private void switchPage(int target) {
        if (target == page) return;
        try {
            storePage();
            page = target;
            error = "";
            rebuildWidgets();
        } catch (IllegalArgumentException exception) { error = exception.getMessage(); }
    }
    private void storePage() {
        if (page == 0) {
            entry.name = name.getValue(); entry.match = match.getValue(); entry.response = response.getValue();
            entry.delaySeconds = decimal(delay, "Verzögerung"); entry.server.address = server.getValue();
        } else {
            entry.blacklist = Arrays.stream(blacklist.getValue().split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
            entry.sound.id = sound.getValue(); entry.sound.volume = integer(volume, "Lautstärke"); entry.sound.pitch = decimal(pitch, "Pitch");
            entry.cooldown.seconds = decimal(cooldown, "Cooldown"); entry.cooldown.group = integer(group, "Regex-Gruppe");
        }
    }
    private void pageButton(int x, int y, int w, int target, String label) {
        Component text = Localizer.literal(label);
        if (page == target) text = text.copy().withStyle(net.minecraft.ChatFormatting.GREEN);
        Button button = Button.builder(text, b -> switchPage(target)).bounds(x, y, w, 20).build();
        button.active = page != target;
        addRenderableWidget(button);
    }
    private void clearFields() {
        name = match = response = delay = server = blacklist = sound = volume = pitch = cooldown = group = null;
    }
    private void setSoundFieldsActive(boolean active) {
        if (sound != null) sound.active = active;
        if (volume != null) volume.active = active;
        if (pitch != null) pitch.active = active;
    }
    private void setCooldownFieldsActive(boolean active) {
        if (cooldown != null) cooldown.active = active;
        if (group != null) group.active = active;
    }
    private EditBox field(int x, int y, int w, String label, String value) {
        int input = Math.min(126, w - 90); EditBox box = new EditBox(font, x + w - input, y, input, 20, Localizer.literal(label)); box.setMaxLength(512); box.setValue(value == null ? "" : value); addRenderableWidget(box); labels.add(new Label(Localizer.literal(label), x, y)); return box;
    }
    private void toggle(int x, int y, int w, String label, boolean current, Consumer<Boolean> setter) {
        final boolean[] state = {current}; addRenderableWidget(Button.builder(CorrectChatConfigScreen.toggleLabel(label, current), b -> { state[0] = !state[0]; setter.accept(state[0]); b.setMessage(CorrectChatConfigScreen.toggleLabel(label, state[0])); }).bounds(x, y, w, 20).build());
    }
    private <T extends Enum<T>> void cycle(int x, int y, int w, String label, T current, T[] values, Consumer<T> setter) {
        final int[] index = {Math.max(0, Arrays.asList(values).indexOf(current))};
        addRenderableWidget(Button.builder(Localizer.literal(label + ": " + values[index[0]].name()), b -> { index[0] = (index[0] + 1) % values.length; setter.accept(values[index[0]]); b.setMessage(Localizer.literal(label + ": " + values[index[0]].name())); }).bounds(x, y, w, 20).build());
    }
    private static int integer(EditBox field, String name) { try { return Integer.parseInt(field.getValue().trim()); } catch (NumberFormatException e) { throw new IllegalArgumentException(name + " ist keine Ganzzahl"); } }
    private static float decimal(EditBox field, String name) { try { return Float.parseFloat(field.getValue().trim().replace(',', '.')); } catch (NumberFormatException e) { throw new IllegalArgumentException(name + " ist keine Zahl"); } }
    private record Label(Component text, int x, int y) { }
}
