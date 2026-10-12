package de.dho.correctchat.gui;

import de.dho.correctchat.config.CorrectChatConfig;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

final class ListenerEditorScreen extends Screen {
    private final Screen parent;
    private final CorrectChatConfig.ChatListener entry;
    private final List<Label> labels = new ArrayList<>();
    private EditBox name, match, response, delay, server, blacklist, sound, volume, pitch, cooldown, group;
    private String error = "";

    ListenerEditorScreen(Screen parent, CorrectChatConfig.ChatListener entry) {
        super(Component.literal("Chat-Listener bearbeiten"));
        this.parent = parent;
        this.entry = entry;
    }

    @Override protected void init() {
        labels.clear();
        int w = Math.min(230, width / 2 - 12), left = width / 2 - w - 4, right = width / 2 + 4, top = 34;
        name = field(left, top, w, "Name", entry.name); match = field(left, top + 22, w, "Muster", entry.match);
        response = field(left, top + 44, w, "Antwort", entry.response); delay = field(left, top + 66, w, "Verzögerung (s)", Float.toString(entry.delaySeconds));
        cycle(left, top + 88, w, "Vergleich", entry.matchType, CorrectChatConfig.MatchType.values(), value -> entry.matchType = value);
        toggle(left, top + 110, w, "Aktiv", entry.enabled, value -> entry.enabled = value);
        toggle(left, top + 132, w, "Nachricht ausblenden", entry.cancel, value -> entry.cancel = value);
        toggle(left, top + 154, w, "Servergebunden", entry.server.enabled, value -> entry.server.enabled = value);
        toggle(left, top + 176, w, "Im Einzelspieler", entry.server.singlePlayer, value -> entry.server.singlePlayer = value);
        server = field(left, top + 198, w, "Serveradresse", entry.server.address);

        blacklist = field(right, top, w, "Blacklist (Komma)", String.join(",", entry.blacklist));
        toggle(right, top + 22, w, "Sound", entry.sound.enabled, value -> entry.sound.enabled = value);
        sound = field(right, top + 44, w, "Sound-ID", entry.sound.id);
        volume = field(right, top + 66, w, "Lautstärke %", Integer.toString(entry.sound.volume));
        pitch = field(right, top + 88, w, "Pitch", Float.toString(entry.sound.pitch));
        toggle(right, top + 110, w, "Cooldown", entry.cooldown.enabled, value -> entry.cooldown.enabled = value);
        cooldown = field(right, top + 132, w, "Cooldown (s)", Float.toString(entry.cooldown.seconds));
        cycle(right, top + 154, w, "Cooldown-Typ", entry.cooldown.type, CorrectChatConfig.CooldownType.values(), value -> entry.cooldown.type = value);
        group = field(right, top + 176, w, "Regex-Gruppe", Integer.toString(entry.cooldown.group));

        int bottom = height - 28;
        addRenderableWidget(Button.builder(Component.literal("Übernehmen"), b -> save()).bounds(width / 2 - 104, bottom, 100, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Zurück"), b -> onClose()).bounds(width / 2 + 4, bottom, 100, 20).build());
    }

    @Override public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float d) {
        super.extractRenderState(g, mx, my, d);
        g.centeredText(font, title, width / 2, 13, 0xFFFFFFFF);
        g.centeredText(font, Component.literal("Simplified Regex: * = beliebig, % = Gruppe; &player = eigener Name"), width / 2, 24, 0xFFAAAAAA);
        labels.forEach(label -> g.text(font, label.text, label.x, label.y + 6, 0xFFE0E0E0, true));
        if (!error.isEmpty()) g.centeredText(font, Component.literal(error), width / 2, height - 41, 0xFFFF6060);
    }
    @Override public void onClose() { minecraft.gui.setScreen(parent); }
    @Override public boolean isPauseScreen() { return false; }

    private void save() {
        try {
            entry.name = name.getValue(); entry.match = match.getValue(); entry.response = response.getValue();
            entry.delaySeconds = decimal(delay, "Verzögerung"); entry.server.address = server.getValue();
            entry.blacklist = Arrays.stream(blacklist.getValue().split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
            entry.sound.id = sound.getValue(); entry.sound.volume = integer(volume, "Lautstärke"); entry.sound.pitch = decimal(pitch, "Pitch");
            entry.cooldown.seconds = decimal(cooldown, "Cooldown"); entry.cooldown.group = integer(group, "Regex-Gruppe");
            entry.normalize(); onClose();
        } catch (IllegalArgumentException exception) { error = exception.getMessage(); }
    }
    private EditBox field(int x, int y, int w, String label, String value) {
        int input = Math.min(126, w - 90); EditBox box = new EditBox(font, x + w - input, y, input, 20, Component.literal(label)); box.setMaxLength(512); box.setValue(value == null ? "" : value); addRenderableWidget(box); labels.add(new Label(Component.literal(label), x, y)); return box;
    }
    private void toggle(int x, int y, int w, String label, boolean current, Consumer<Boolean> setter) {
        final boolean[] state = {current}; addRenderableWidget(Button.builder(CorrectChatConfigScreen.toggleLabel(label, current), b -> { state[0] = !state[0]; setter.accept(state[0]); b.setMessage(CorrectChatConfigScreen.toggleLabel(label, state[0])); }).bounds(x, y, w, 20).build());
    }
    private <T extends Enum<T>> void cycle(int x, int y, int w, String label, T current, T[] values, Consumer<T> setter) {
        final int[] index = {Math.max(0, Arrays.asList(values).indexOf(current))};
        addRenderableWidget(Button.builder(Component.literal(label + ": " + values[index[0]].name()), b -> { index[0] = (index[0] + 1) % values.length; setter.accept(values[index[0]]); b.setMessage(Component.literal(label + ": " + values[index[0]].name())); }).bounds(x, y, w, 20).build());
    }
    private static int integer(EditBox field, String name) { try { return Integer.parseInt(field.getValue().trim()); } catch (NumberFormatException e) { throw new IllegalArgumentException(name + " ist keine Ganzzahl"); } }
    private static float decimal(EditBox field, String name) { try { return Float.parseFloat(field.getValue().trim().replace(',', '.')); } catch (NumberFormatException e) { throw new IllegalArgumentException(name + " ist keine Zahl"); } }
    private record Label(Component text, int x, int y) { }
}
