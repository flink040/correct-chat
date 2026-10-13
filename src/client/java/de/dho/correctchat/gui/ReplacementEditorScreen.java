package de.dho.correctchat.gui;

import de.dho.correctchat.core.Localizer;

import de.dho.correctchat.config.CorrectChatConfig;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

final class ReplacementEditorScreen extends Screen {
    private final Screen parent;
    private final CorrectChatConfig.TextReplacement entry;
    private final Consumer<CorrectChatConfig.TextReplacement> commit;
    private final List<Label> labels = new ArrayList<>();
    private EditBox name, find, replacement, server;

    ReplacementEditorScreen(Screen parent, CorrectChatConfig.TextReplacement entry,
                            Consumer<CorrectChatConfig.TextReplacement> commit) {
        super(Localizer.literal("Textersetzung bearbeiten"));
        this.parent = parent;
        this.entry = entry.copy();
        this.commit = commit;
    }

    @Override protected void init() {
        labels.clear();
        int w = Math.min(300, width - 24), x = (width - w) / 2, y = 38;
        name = field(x, y, w, "Name", entry.name); y += 22;
        find = field(x, y, w, "Suchen", entry.find); y += 22;
        replacement = field(x, y, w, "Ersetzen durch", entry.replacement); y += 22;
        toggle(x, y, w, "Aktiv", entry.enabled, value -> entry.enabled = value); y += 22;
        toggle(x, y, w, "Auch empfangen", entry.onReceive, value -> entry.onReceive = value); y += 22;
        toggle(x, y, w, "Servergebunden", entry.server.enabled, value -> entry.server.enabled = value); y += 22;
        toggle(x, y, w, "Im Einzelspieler", entry.server.singlePlayer, value -> entry.server.singlePlayer = value); y += 22;
        server = field(x, y, w, "Serveradresse", entry.server.address);
        bottomButtons();
    }

    @Override public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float d) {
        super.extractRenderState(g, mx, my, d);
        g.centeredText(font, title, width / 2, 14, 0xFFFFFFFF);
        labels.forEach(label -> g.text(font, label.text, label.x, label.y + 6, 0xFFE0E0E0, true));
    }
    @Override public void onClose() { minecraft.gui.setScreen(parent); }
    @Override public boolean isPauseScreen() { return false; }

    private void save() {
        entry.name = name.getValue(); entry.find = find.getValue(); entry.replacement = replacement.getValue(); entry.server.address = server.getValue();
        entry.normalize(); commit.accept(entry); onClose();
    }
    private void bottomButtons() {
        int y = height - 28;
        addRenderableWidget(Button.builder(Localizer.literal("Übernehmen"), b -> save()).bounds(width / 2 - 104, y, 100, 20).build());
        addRenderableWidget(Button.builder(Localizer.literal("Zurück"), b -> onClose()).bounds(width / 2 + 4, y, 100, 20).build());
    }
    private EditBox field(int x, int y, int w, String label, String value) {
        EditBox box = new EditBox(font, x + 112, y, w - 112, 20, Localizer.literal(label)); box.setMaxLength(512); box.setValue(value); addRenderableWidget(box); labels.add(new Label(Localizer.literal(label), x, y)); return box;
    }
    private void toggle(int x, int y, int w, String label, boolean current, java.util.function.Consumer<Boolean> setter) {
        final boolean[] state = {current};
        addRenderableWidget(Button.builder(CorrectChatConfigScreen.toggleLabel(label, current), b -> { state[0] = !state[0]; setter.accept(state[0]); b.setMessage(CorrectChatConfigScreen.toggleLabel(label, state[0])); }).bounds(x, y, w, 20).build());
    }
    private record Label(Component text, int x, int y) { }
}
