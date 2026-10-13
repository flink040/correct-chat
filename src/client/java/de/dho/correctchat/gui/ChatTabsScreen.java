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

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

final class ChatTabsScreen extends Screen {
    private final Screen parent;
    private final CorrectChatConfig config;
    private final List<CorrectChatConfig.ChatTab> tabs;
    private int page;

    ChatTabsScreen(Screen parent, CorrectChatConfig config) {
        super(Localizer.literal("Chat-Tabs"));
        this.parent = parent;
        this.config = config;
        this.tabs = config.tabsFor(CorrectChatClient.workspace().currentServerKey());
    }

    @Override protected void init() {
        int rows = Math.max(1, (height - 72) / 24);
        int pages = Math.max(1, (tabs.size() + rows - 1) / rows);
        page = Math.min(page, pages - 1);
        int from = page * rows;
        int to = Math.min(tabs.size(), from + rows);
        for (int i = from; i < to; i++) {
            int index = i;
            CorrectChatConfig.ChatTab tab = tabs.get(i);
            int y = 34 + (i - from) * 24;
            addRenderableWidget(Button.builder(Localizer.literal((i + 1) + ". " + tab.name + " · " + filterSummary(tab)),
                    button -> minecraft.gui.setScreen(new Editor(this, tab, saved -> tabs.set(index, saved))))
                    .bounds(width / 2 - 150, y, 220, 20).build());
            Button remove = Button.builder(Localizer.literal("Löschen").withStyle(ChatFormatting.RED), button -> {
                if (tabs.size() > 1) {
                    tabs.remove(index);
                    config.normalize();
                    rebuildWidgets();
                }
            }).bounds(width / 2 + 74, y, 76, 20).build();
            remove.active = tabs.size() > 1;
            addRenderableWidget(remove);
        }
        int bottom = height - 28;
        addRenderableWidget(Button.builder(Localizer.literal("+ Neuer Tab").withStyle(ChatFormatting.GREEN), button -> {
            CorrectChatConfig.ChatTab tab = new CorrectChatConfig.ChatTab();
            minecraft.gui.setScreen(new Editor(this, tab, tabs::add));
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
        graphics.centeredText(font, Localizer.literal("Tabs erscheinen über der Chat-Eingabe und in jedem Zusatzfenster."), width / 2, 24, 0xFFAAAAAA);
    }
    @Override public void onClose() { minecraft.gui.setScreen(parent); }
    @Override public boolean isPauseScreen() { return false; }

    static String typeName(CorrectChatConfig.TabType type) {
        return Localizer.text(switch (type) {
            case ALL -> "Alle Nachrichten";
            case PLAYER -> "Spieler";
            case SYSTEM -> "System";
            case TWITCH -> "Twitch-Chat";
            case CONTAINS -> "Enthält";
            case EQUALS -> "Entspricht";
            case REGEX -> "Regulärer Ausdruck";
            case MOD -> "Mod";
        });
    }

    static String typeHelp(CorrectChatConfig.TabType type) {
        return Localizer.text(switch (type) {
            case ALL -> "Zeigt jede Chatnachricht.";
            case PLAYER -> "Zeigt Nachrichten von Spielern.";
            case SYSTEM -> "Zeigt System- und Servernachrichten.";
            case TWITCH -> "Zeigt nur Nachrichten und Statusmeldungen des Twitch-Kanals.";
            case CONTAINS -> "Groß-/Kleinschreibung wird ignoriert.";
            case EQUALS -> "Die gesamte Nachricht muss dem Filterwert entsprechen.";
            case REGEX -> "Sucht mit einem regulären Ausdruck nach einem Treffer.";
            case MOD -> "Installierte Mod durchsuchen oder eine eigene Mod-ID eintragen.";
        });
    }

    static String filterSummary(CorrectChatConfig.ChatTab tab) {
        if (tab.filters.isEmpty()) return "Keine Filter";
        if (tab.filters.size() == 1) return typeName(tab.filters.getFirst().type);
        return tab.filters.size() + " Filter · " + ruleName(tab.filterRule);
    }

    static String filterLabel(CorrectChatConfig.TabFilter filter) {
        String value = filter.value.isBlank() ? "" : ": " + filter.value;
        return typeName(filter.type) + value;
    }

    static boolean needsFilterValue(CorrectChatConfig.TabType type) {
        return type == CorrectChatConfig.TabType.CONTAINS
                || type == CorrectChatConfig.TabType.EQUALS
                || type == CorrectChatConfig.TabType.REGEX
                || type == CorrectChatConfig.TabType.MOD;
    }

    static String ruleName(CorrectChatConfig.FilterRule rule) {
        return Localizer.text(rule == CorrectChatConfig.FilterRule.AND ? "Und-Regel" : "Oder-Regel");
    }

    private static final class Editor extends Screen {
        private final Screen parent;
        private final CorrectChatConfig.ChatTab tab;
        private final Consumer<CorrectChatConfig.ChatTab> commit;
        private final List<Label> labels = new ArrayList<>();
        private EditBox name;
        private EditBox filter;
        private Button type;
        private CorrectChatConfig.TabType draftType = CorrectChatConfig.TabType.CONTAINS;
        private String draftValue = "";
        private int filterPage;
        private int helpY;
        private int activeFiltersY;
        private int editorPage;
        private String error = "";

        Editor(Screen parent, CorrectChatConfig.ChatTab tab, Consumer<CorrectChatConfig.ChatTab> commit) {
            super(Localizer.literal("Chat-Tab bearbeiten"));
            this.parent = parent;
            this.tab = tab.copy();
            this.commit = commit;
        }
        @Override protected void init() {
            labels.clear();
            name = null;
            filter = null;
            int w = Math.min(300, width - 24), x = (width - w) / 2, y = 46;
            pageButton(x, 22, w / 2 - 2, 0, "Filter hinzufügen");
            pageButton(x + w / 2 + 2, 22, w / 2 - 2, 1, "Aktive Filter (" + tab.filters.size() + ")");
            if (editorPage == 0) {
                name = field(x, y, w, "Name", tab.name); y += 22;
                type = addRenderableWidget(Button.builder(typeLabel(), button -> {
                    storeDraft();
                    CorrectChatConfig.TabType[] values = CorrectChatConfig.TabType.values();
                    draftType = values[(draftType.ordinal() + 1) % values.length];
                    if (draftType == CorrectChatConfig.TabType.MOD && draftValue.isBlank()) draftValue = "worldedit";
                    rebuildWidgets();
                }).bounds(x, y, w, 20).build()); y += 22;
                filter = field(x, y, w, "Filter / Mod-ID", draftValue); y += 22;
                if (draftType == CorrectChatConfig.TabType.MOD) {
                    addRenderableWidget(Button.builder(Localizer.literal("Installierte Mods durchsuchen …"), button -> {
                        storeDraft();
                        minecraft.gui.setScreen(new ModFilterPickerScreen(this, selected -> {
                            draftValue = selected.id();
                            error = selected.messageHints() == 0 ? "" : selected.messageHints() + " Nachrichtenmuster aus der JAR erkannt.";
                        }));
                    }).bounds(x, y, w, 20).build());
                    y += 22;
                }
                addRenderableWidget(Button.builder(Localizer.literal("Verknüpfung: " + ruleName(tab.filterRule)), button -> {
                    storeDraft();
                    tab.filterRule = tab.filterRule == CorrectChatConfig.FilterRule.OR
                            ? CorrectChatConfig.FilterRule.AND : CorrectChatConfig.FilterRule.OR;
                    rebuildWidgets();
                }).bounds(x, y, w, 20).build());
                y += 22;
                helpY = y;
                y += draftType == CorrectChatConfig.TabType.REGEX ? 25 : 14;
                addRenderableWidget(Button.builder(Localizer.literal("+ Filter hinzufügen").withStyle(ChatFormatting.GREEN), button -> addFilter())
                        .bounds(x, y, w, 20).build());
                activeFiltersY = -1;
            } else {
                helpY = -1;
                activeFiltersY = y;
                y += 16;
                int availableRows = Math.max(1, (height - 50 - y) / 22);
                boolean needsPager = tab.filters.size() > availableRows;
                int pageSize = Math.max(1, availableRows - (needsPager ? 1 : 0));
                int pages = Math.max(1, (tab.filters.size() + pageSize - 1) / pageSize);
                filterPage = Math.min(filterPage, pages - 1);
                int from = filterPage * pageSize;
                int to = Math.min(tab.filters.size(), from + pageSize);
                for (int i = from; i < to; i++) {
                    int index = i;
                    CorrectChatConfig.TabFilter active = tab.filters.get(i);
                    addRenderableWidget(Button.builder(Localizer.literal(filterLabel(active)), button -> { })
                            .bounds(x, y, w - 70, 20).build()).active = false;
                    addRenderableWidget(Button.builder(Localizer.literal("Entfernen").withStyle(ChatFormatting.RED), button -> {
                        tab.filters.remove(index);
                        rebuildWidgets();
                    }).bounds(x + w - 66, y, 66, 20).build());
                    y += 22;
                }
                if (needsPager) {
                    Button previous = Button.builder(Localizer.literal("◀"), button -> { filterPage--; rebuildWidgets(); })
                            .bounds(x, y, 40, 20).build();
                    previous.active = filterPage > 0;
                    addRenderableWidget(previous);
                    addRenderableWidget(Button.builder(Localizer.literal((filterPage + 1) + "/" + pages), button -> { })
                            .bounds(x + 44, y, w - 88, 20).build()).active = false;
                    Button next = Button.builder(Localizer.literal("▶"), button -> { filterPage++; rebuildWidgets(); })
                            .bounds(x + w - 40, y, 40, 20).build();
                    next.active = filterPage < pages - 1;
                    addRenderableWidget(next);
                }
            }
            int bottom = height - 28;
            addRenderableWidget(Button.builder(Localizer.literal("Übernehmen"), button -> save()).bounds(width / 2 - 104, bottom, 100, 20).build());
            addRenderableWidget(Button.builder(Localizer.literal("Zurück"), button -> onClose()).bounds(width / 2 + 4, bottom, 100, 20).build());
        }
        @Override public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
            super.extractRenderState(graphics, mouseX, mouseY, delta);
            graphics.centeredText(font, title, width / 2, 9, 0xFFFFFFFF);
            labels.forEach(label -> graphics.text(font, label.text, label.x, label.y + 6, 0xFFE0E0E0, true));
            String help = draftType == CorrectChatConfig.TabType.REGEX
                    ? RegexExplainer.explain(filter == null ? draftValue : filter.getValue())
                    : typeHelp(draftType);
            List<FormattedCharSequence> helpLines = font.split(Localizer.literal(help), 300);
            for (int i = 0; helpY >= 0 && i < Math.min(draftType == CorrectChatConfig.TabType.REGEX ? 2 : 1, helpLines.size()); i++) {
                graphics.centeredText(font, helpLines.get(i), width / 2, helpY + i * (font.lineHeight + 1), 0xFFAAAAAA);
            }
            if (activeFiltersY >= 0) {
                int left = (width - Math.min(300, width - 24)) / 2;
                graphics.text(font, Localizer.literal("Aktive Filter · " + ruleName(tab.filterRule)), left, activeFiltersY, 0xFFE0E0E0, true);
                if (tab.filters.isEmpty()) graphics.text(font, Localizer.literal("Noch keine Filter hinzugefügt."), left, activeFiltersY + 14, 0xFFAAAAAA, false);
            }
            if (!error.isEmpty()) graphics.centeredText(font, Localizer.literal(error), width / 2, height - 41, 0xFFFF6060);
        }
        @Override public void onClose() { minecraft.gui.setScreen(parent); }
        @Override public boolean isPauseScreen() { return false; }
        private void save() { storeFields(); commit.accept(tab); onClose(); }
        private void storeFields() { if (name != null) tab.name = name.getValue(); tab.normalize(); }
        private void storeDraft() { storeFields(); if (filter != null) draftValue = filter.getValue(); }
        private void switchPage(int target) {
            if (target == editorPage) return;
            if (editorPage == 0) storeDraft();
            editorPage = target;
            error = "";
            rebuildWidgets();
        }
        private void pageButton(int x, int y, int w, int target, String label) {
            Component text = Localizer.literal(label);
            if (editorPage == target) text = text.copy().withStyle(ChatFormatting.GREEN);
            Button button = Button.builder(text, ignored -> switchPage(target))
                    .bounds(x, y, w, 20).build();
            button.active = editorPage != target;
            addRenderableWidget(button);
        }
        private void addFilter() {
            storeDraft();
            String value = needsFilterValue(draftType) ? draftValue.trim() : "";
            if (needsFilterValue(draftType) && value.isEmpty()) {
                error = "Bitte einen Filterwert eingeben.";
                return;
            }
            boolean duplicate = tab.filters.stream().anyMatch(existing -> existing.type == draftType
                    && existing.value.equalsIgnoreCase(value));
            if (duplicate) {
                error = "Dieser Filter ist bereits aktiv.";
                return;
            }
            tab.filters.add(new CorrectChatConfig.TabFilter(draftType, value));
            draftValue = "";
            error = "";
            filterPage = Integer.MAX_VALUE;
            rebuildWidgets();
        }
        private Component typeLabel() { return Localizer.literal("Filtertyp: " + typeName(draftType)); }
        private EditBox field(int x, int y, int w, String label, String value) {
            EditBox box = new EditBox(font, x + 90, y, w - 90, 20, Localizer.literal(label));
            box.setMaxLength(512); box.setValue(value); addRenderableWidget(box);
            labels.add(new Label(Localizer.literal(label), x, y)); return box;
        }
        private record Label(Component text, int x, int y) { }
    }
}
