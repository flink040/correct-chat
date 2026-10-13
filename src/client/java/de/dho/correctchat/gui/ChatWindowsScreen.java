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

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

final class ChatWindowsScreen extends Screen {
    private final Screen parent;
    private final CorrectChatConfig config;
    private final List<CorrectChatConfig.ChatWindow> windows;
    private final List<CorrectChatConfig.ChatTab> tabs;
    private int page;

    ChatWindowsScreen(Screen parent, CorrectChatConfig config) {
        super(Localizer.literal("Chatfenster"));
        this.parent = parent;
        this.config = config;
        String serverKey = CorrectChatClient.workspace().currentServerKey();
        this.windows = config.windowsFor(serverKey);
        this.tabs = config.tabsFor(serverKey);
    }

    @Override protected void init() {
        int rows = Math.max(1, (height - 72) / 24);
        int pages = Math.max(1, (windows.size() + rows - 1) / rows);
        page = Math.min(page, pages - 1);
        int from = page * rows;
        int to = Math.min(windows.size(), from + rows);
        for (int i = from; i < to; i++) {
            int index = i;
            CorrectChatConfig.ChatWindow window = windows.get(i);
            int y = 34 + (i - from) * 24;
            addRenderableWidget(Button.builder(Localizer.literal((i + 1) + ". " + window.name + (window.visible ? "" : " (aus)")),
                    button -> minecraft.gui.setScreen(new Editor(this, window, tabs, saved -> windows.set(index, saved))))
                    .bounds(width / 2 - 150, y, 220, 20).build());
            addRenderableWidget(Button.builder(Localizer.literal("Löschen").withStyle(ChatFormatting.RED), button -> {
                windows.remove(index); rebuildWidgets();
            }).bounds(width / 2 + 74, y, 76, 20).build());
        }
        int bottom = height - 28;
        addRenderableWidget(Button.builder(Localizer.literal("+ Fenster").withStyle(ChatFormatting.GREEN), button -> {
            CorrectChatConfig.ChatWindow window = new CorrectChatConfig.ChatWindow();
            minecraft.gui.setScreen(new Editor(this, window, tabs, windows::add));
        }).bounds(width / 2 - 150, bottom, 92, 20).build());
        Button previous = Button.builder(Localizer.literal("◀"), button -> { page = Math.max(0, page - 1); rebuildWidgets(); })
                .bounds(width / 2 - 52, bottom, 34, 20).build();
        previous.active = page > 0;
        addRenderableWidget(previous);
        Button indicator = Button.builder(Localizer.literal((page + 1) + "/" + pages), button -> { })
                .bounds(width / 2 - 14, bottom, 48, 20).build();
        indicator.active = false;
        addRenderableWidget(indicator);
        Button next = Button.builder(Localizer.literal("▶"), button -> { page = Math.min(pages - 1, page + 1); rebuildWidgets(); })
                .bounds(width / 2 + 38, bottom, 34, 20).build();
        next.active = page < pages - 1;
        addRenderableWidget(next);
        addRenderableWidget(Button.builder(Localizer.literal("Zurück"), button -> onClose())
                .bounds(width / 2 + 78, bottom, 72, 20).build());
    }

    @Override public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        graphics.centeredText(font, title, width / 2, 13, 0xFFFFFFFF);
        graphics.centeredText(font, Localizer.literal("Fenster lassen sich im Chat auch direkt verschieben und skalieren."), width / 2, 24, 0xFFAAAAAA);
        if (windows.isEmpty()) {
            graphics.centeredText(font, Localizer.literal("Noch keine Zusatzfenster angelegt"), width / 2, height / 2 - 8, 0xFFE0E0E0);
            graphics.centeredText(font, Localizer.literal("Der normale Minecraft-Chat bleibt weiterhin aktiv."), width / 2, height / 2 + 5, 0xFF999999);
        }
    }
    @Override public void onClose() { minecraft.gui.setScreen(parent); }
    @Override public boolean isPauseScreen() { return false; }

    private static final class Editor extends Screen {
        private final Screen parent;
        private final CorrectChatConfig.ChatWindow window;
        private final List<CorrectChatConfig.ChatTab> tabs;
        private final Consumer<CorrectChatConfig.ChatWindow> commit;
        private final List<Label> labels = new ArrayList<>();
        private EditBox name, x, y, widthBox, heightBox;
        private Button tabButton;
        private int tabIndex;
        private String error = "";

        Editor(Screen parent, CorrectChatConfig.ChatWindow window,
               List<CorrectChatConfig.ChatTab> tabs, Consumer<CorrectChatConfig.ChatWindow> commit) {
            super(Localizer.literal("Chatfenster bearbeiten"));
            this.parent = parent; this.window = window.copy(); this.commit = commit;
            if (this.window.tabIds.isEmpty()) this.tabs = tabs;
            else this.tabs = tabs.stream().filter(tab -> this.window.tabIds.contains(tab.id)).toList();
        }
        @Override protected void init() {
            labels.clear();
            tabIndex = Math.max(0, indexOfTab(window.activeTabId));
            int w = Math.min(300, width - 24), xPos = (width - w) / 2, yPos = 34;
            name = field(xPos, yPos, w, "Name", window.name); yPos += 24;
            toggle(xPos, yPos, w, "Sichtbar", window.visible); yPos += 24;
            x = field(xPos, yPos, w, "X", Integer.toString(window.x)); yPos += 24;
            y = field(xPos, yPos, w, "Y", Integer.toString(window.y)); yPos += 24;
            widthBox = field(xPos, yPos, w, "Breite", Integer.toString(window.width)); yPos += 24;
            heightBox = field(xPos, yPos, w, "Höhe", Integer.toString(window.height)); yPos += 24;
            tabButton = addRenderableWidget(Button.builder(tabLabel(), button -> {
                if (tabs.isEmpty()) return;
                tabIndex = (tabIndex + 1) % tabs.size(); button.setMessage(tabLabel());
            }).bounds(xPos, yPos, w, 20).build());
            tabButton.active = !tabs.isEmpty();
            int bottom = height - 28;
            addRenderableWidget(Button.builder(Localizer.literal("Übernehmen"), button -> save()).bounds(width / 2 - 104, bottom, 100, 20).build());
            addRenderableWidget(Button.builder(Localizer.literal("Zurück"), button -> onClose()).bounds(width / 2 + 4, bottom, 100, 20).build());
        }
        @Override public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
            super.extractRenderState(graphics, mouseX, mouseY, delta);
            graphics.centeredText(font, title, width / 2, 14, 0xFFFFFFFF);
            labels.forEach(label -> graphics.text(font, label.text, label.x, label.y + 6, 0xFFE0E0E0, true));
            if (!error.isEmpty()) graphics.centeredText(font, Localizer.literal(error), width / 2, height - 41, 0xFFFF6060);
        }
        @Override public void onClose() { minecraft.gui.setScreen(parent); }
        @Override public boolean isPauseScreen() { return false; }
        private void save() {
            try {
                window.name = name.getValue(); window.x = number(x, "X"); window.y = number(y, "Y");
                window.width = number(widthBox, "Breite"); window.height = number(heightBox, "Höhe");
                if (!tabs.isEmpty()) window.activeTabId = tabs.get(tabIndex).id;
                window.normalize(); commit.accept(window); onClose();
            } catch (IllegalArgumentException exception) { error = exception.getMessage(); }
        }
        private void toggle(int x, int y, int w, String label, boolean current) {
            addRenderableWidget(Button.builder(CorrectChatConfigScreen.toggleLabel(label, current), button -> {
                window.visible = !window.visible; button.setMessage(CorrectChatConfigScreen.toggleLabel(label, window.visible));
            }).bounds(x, y, w, 20).build());
        }
        private EditBox field(int x, int y, int w, String label, String value) {
            EditBox box = new EditBox(font, x + 90, y, w - 90, 20, Localizer.literal(label));
            box.setMaxLength(128); box.setValue(value); addRenderableWidget(box);
            labels.add(new Label(Localizer.literal(label), x, y)); return box;
        }
        private int indexOfTab(String id) {
            for (int i = 0; i < tabs.size(); i++) if (tabs.get(i).id.equals(id)) return i;
            return 0;
        }
        private Component tabLabel() { return Localizer.literal(tabs.isEmpty()
                ? "Kein Tab verfügbar" : "Aktiver Tab: " + tabs.get(tabIndex).name); }
        private static int number(EditBox box, String name) {
            try { return Integer.parseInt(box.getValue().trim()); }
            catch (NumberFormatException exception) { throw new IllegalArgumentException(name + " ist keine Ganzzahl"); }
        }
        private record Label(Component text, int x, int y) { }
    }
}
