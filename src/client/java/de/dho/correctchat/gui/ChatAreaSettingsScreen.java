package de.dho.correctchat.gui;

import de.dho.correctchat.CorrectChatClient;
import de.dho.correctchat.config.CorrectChatConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/** Context editor opened by the green plus button in a chat area. */
public final class ChatAreaSettingsScreen extends Screen {
    private final Screen parent;
    private final CorrectChatConfig draft;
    private final String serverKey;
    private int windowIndex;
    private int tabIndex;
    private final List<Label> labels = new ArrayList<>();
    private EditBox tabName;
    private EditBox filter;
    private EditBox windowName;
    private EditBox chatWidth;
    private EditBox chatHeight;
    private String widthValue;
    private String heightValue;
    private CorrectChatConfig.TabType draftFilterType = CorrectChatConfig.TabType.CONTAINS;
    private String draftFilterValue = "";
    private int filterPage;
    private String error = "";
    private int filterHelpY;
    private int activeFiltersY;

    public ChatAreaSettingsScreen(Screen parent, int windowIndex) {
        super(Component.literal(windowIndex < 0 ? "Hauptchat einstellen" : "Chatfenster einstellen"));
        this.parent = parent;
        this.draft = CorrectChatClient.config().copy();
        this.serverKey = CorrectChatClient.workspace().currentServerKey();
        this.windowIndex = windowIndex >= 0 && windowIndex < windows().size() ? windowIndex : -1;
        String activeId = this.windowIndex < 0 ? draft.primaryTabFor(serverKey) : windows().get(this.windowIndex).activeTabId;
        this.tabIndex = indexOfTab(activeId);
        if (this.windowIndex < 0) {
            this.widthValue = Integer.toString(CorrectChatClient.workspace().primaryChatWidth(Minecraft.getInstance().getWindow().getGuiScaledWidth()));
            this.heightValue = Integer.toString(CorrectChatClient.workspace().primaryChatHeight());
        } else {
            this.widthValue = Integer.toString(window().width);
            this.heightValue = Integer.toString(window().height);
        }
    }

    @Override protected void init() {
        labels.clear();
        int x = width / 2 - 160;
        int w = 320;
        int y = 36;
        if (windowIndex >= 0) {
            windowName = field(x, y, w, "Fenstername", window().name); y += 24;
        } else {
            windowName = null;
        }

        int half = (w - 8) / 2;
        chatWidth = field(x, y, half, "Breite", widthValue);
        chatHeight = field(x + half + 8, y, half, "Höhe", heightValue); y += 24;

        addRenderableWidget(Button.builder(Component.literal("Aktiver Tab: " + tab().name), button -> {
            storeFields();
            tabIndex = (tabIndex + 1) % tabs().size();
            setActiveTab(tab().id);
            resetFilterDraft();
            rebuildWidgets();
        }).bounds(x, y, w, 20).build()); y += 24;

        tabName = field(x, y, w, "Tabname", tab().name); y += 24;
        addRenderableWidget(Button.builder(Component.literal("Filtertyp: " + ChatTabsScreen.typeName(draftFilterType)), button -> {
            storeDraftFilter();
            CorrectChatConfig.TabType[] values = CorrectChatConfig.TabType.values();
            draftFilterType = values[(draftFilterType.ordinal() + 1) % values.length];
            if (draftFilterType == CorrectChatConfig.TabType.MOD && draftFilterValue.isBlank()) draftFilterValue = "worldedit";
            rebuildWidgets();
        }).bounds(x, y, w, 20).build()); y += 24;
        filter = field(x, y, w, "Filter / Mod-ID", draftFilterValue); y += 24;
        if (draftFilterType == CorrectChatConfig.TabType.MOD) {
            addRenderableWidget(Button.builder(Component.literal("Mod auswählen: " + ChatTabsScreen.modPresetName(filter.getValue())), button -> {
                filter.setValue(ChatTabsScreen.nextModPreset(filter.getValue()));
                button.setMessage(Component.literal("Mod auswählen: " + ChatTabsScreen.modPresetName(filter.getValue())));
            }).bounds(x, y, w, 20).build());
            y += 24;
        }
        filterHelpY = y + 1;
        y += 18;

        addRenderableWidget(Button.builder(Component.literal("+ Filter hinzufügen").withStyle(ChatFormatting.GREEN), button -> addFilter())
                .bounds(x, y, w, 20).build());
        y += 27;
        activeFiltersY = y;
        y += 13;

        int actionsTop = height - 76;
        int availableRows = Math.max(1, (actionsTop - y) / 22);
        boolean needsPager = tab().filters.size() > availableRows;
        int pageSize = Math.max(1, availableRows - (needsPager ? 1 : 0));
        int pages = Math.max(1, (tab().filters.size() + pageSize - 1) / pageSize);
        filterPage = Math.min(filterPage, pages - 1);
        int from = filterPage * pageSize;
        int to = Math.min(tab().filters.size(), from + pageSize);
        for (int i = from; i < to; i++) {
            int index = i;
            CorrectChatConfig.TabFilter active = tab().filters.get(i);
            addRenderableWidget(Button.builder(Component.literal(ChatTabsScreen.filterLabel(active)), button -> { })
                    .bounds(x, y, w - 72, 20).build()).active = false;
            addRenderableWidget(Button.builder(Component.literal("Entfernen").withStyle(ChatFormatting.RED), button -> {
                tab().filters.remove(index);
                rebuildWidgets();
            }).bounds(x + w - 68, y, 68, 20).build());
            y += 22;
        }
        if (needsPager) {
            Button previousFilter = Button.builder(Component.literal("◀"), button -> { filterPage--; rebuildWidgets(); })
                    .bounds(x, y, 40, 20).build();
            previousFilter.active = filterPage > 0;
            addRenderableWidget(previousFilter);
            addRenderableWidget(Button.builder(Component.literal((filterPage + 1) + "/" + pages), button -> { })
                    .bounds(x + 44, y, w - 88, 20).build()).active = false;
            Button nextFilter = Button.builder(Component.literal("▶"), button -> { filterPage++; rebuildWidgets(); })
                    .bounds(x + w - 40, y, 40, 20).build();
            nextFilter.active = filterPage < pages - 1;
            addRenderableWidget(nextFilter);
        }

        addRenderableWidget(Button.builder(Component.literal("+ Neuer Tab").withStyle(ChatFormatting.GREEN), button -> {
            storeFields();
            CorrectChatConfig.ChatTab created = new CorrectChatConfig.ChatTab();
            tabs().add(created);
            tabIndex = tabs().size() - 1;
            setActiveTab(created.id);
            resetFilterDraft();
            rebuildWidgets();
        }).bounds(x, actionsTop, 154, 20).build());
        Button deleteTab = Button.builder(Component.literal("Tab löschen").withStyle(ChatFormatting.RED), button -> {
            if (tabs().size() <= 1) return;
            String removedId = tab().id;
            tabs().remove(tabIndex);
            tabIndex = Math.min(tabIndex, tabs().size() - 1);
            replaceRemovedTab(removedId, tab().id);
            resetFilterDraft();
            rebuildWidgets();
        }).bounds(x + 166, actionsTop, 154, 20).build();
        deleteTab.active = tabs().size() > 1;
        addRenderableWidget(deleteTab);

        addRenderableWidget(Button.builder(Component.literal("+ Neues Chatfenster").withStyle(ChatFormatting.GREEN), button -> {
            storeFields();
            CorrectChatConfig.ChatWindow created = new CorrectChatConfig.ChatWindow();
            created.name = "Chatfenster " + (windows().size() + 1);
            created.x = 16 + windows().size() * 18;
            created.y = 36 + windows().size() * 18;
            created.activeTabId = tab().id;
            windows().add(created);
            windowIndex = windows().size() - 1;
            widthValue = Integer.toString(created.width);
            heightValue = Integer.toString(created.height);
            rebuildWidgets();
        }).bounds(x, actionsTop + 24, 154, 20).build());
        if (windowIndex >= 0) {
            addRenderableWidget(Button.builder(Component.literal("Fenster löschen").withStyle(ChatFormatting.RED), button -> {
                windows().remove(windowIndex);
                windowIndex = -1;
                widthValue = Integer.toString(CorrectChatClient.workspace().primaryChatWidth(width));
                heightValue = Integer.toString(CorrectChatClient.workspace().primaryChatHeight());
                rebuildWidgets();
            }).bounds(x + 166, actionsTop + 24, 154, 20).build());
        }

        int bottom = height - 28;
        addRenderableWidget(Button.builder(Component.literal("Speichern"), button -> save())
                .bounds(width / 2 - 104, bottom, 100, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Abbrechen"), button -> onClose())
                .bounds(width / 2 + 4, bottom, 100, 20).build());
    }

    @Override public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        graphics.centeredText(font, title, width / 2, 12, 0xFFFFFFFF);
        graphics.centeredText(font, Component.literal("Lokale Einstellungen dieses Chats · Tabs gelten in allen Chatfenstern"), width / 2, 23, 0xFFAAAAAA);
        labels.forEach(label -> graphics.text(font, label.text, label.x, label.y + 6, 0xFFE0E0E0, true));
        graphics.centeredText(font, Component.literal(ChatTabsScreen.typeHelp(draftFilterType)), width / 2, filterHelpY, 0xFFAAAAAA);
        graphics.text(font, Component.literal("Aktive Filter (ODER)"), width / 2 - 160, activeFiltersY, 0xFFE0E0E0, true);
        if (tab().filters.isEmpty()) graphics.text(font, Component.literal("Noch keine Filter hinzugefügt."), width / 2 - 160, activeFiltersY + 14, 0xFFAAAAAA, false);
        if (!error.isEmpty()) graphics.centeredText(font, Component.literal(error), width / 2, height - 41, 0xFFFF6060);
    }

    @Override public boolean isPauseScreen() { return false; }
    @Override public void onClose() { minecraft.gui.setScreen(parent); }

    private void save() {
        try {
            storeFields();
            int requestedWidth = number(widthValue, "Breite");
            int requestedHeight = number(heightValue, "Höhe");
            if (windowIndex < 0) {
                CorrectChatClient.workspace().setPrimaryChatSize(requestedWidth, requestedHeight, width, height);
            } else {
                window().width = requestedWidth;
                window().height = requestedHeight;
            }
            draft.normalize();
            CorrectChatClient.applyConfig(draft);
            minecraft.gui.setScreen(parent);
        } catch (IllegalArgumentException exception) {
            error = exception.getMessage();
        }
    }

    private void storeFields() {
        if (tabName != null) tab().name = tabName.getValue();
        if (windowName != null && windowIndex >= 0) window().name = windowName.getValue();
        if (chatWidth != null) widthValue = chatWidth.getValue();
        if (chatHeight != null) heightValue = chatHeight.getValue();
        tab().normalize();
    }

    private void storeDraftFilter() {
        storeFields();
        if (filter != null) draftFilterValue = filter.getValue();
    }

    private void addFilter() {
        storeDraftFilter();
        String value = ChatTabsScreen.needsFilterValue(draftFilterType) ? draftFilterValue.trim() : "";
        if (ChatTabsScreen.needsFilterValue(draftFilterType) && value.isEmpty()) {
            error = "Bitte einen Filterwert eingeben.";
            return;
        }
        boolean duplicate = tab().filters.stream().anyMatch(existing -> existing.type == draftFilterType
                && existing.value.equalsIgnoreCase(value));
        if (duplicate) {
            error = "Dieser Filter ist bereits aktiv.";
            return;
        }
        tab().filters.add(new CorrectChatConfig.TabFilter(draftFilterType, value));
        draftFilterValue = "";
        error = "";
        filterPage = Integer.MAX_VALUE;
        rebuildWidgets();
    }

    private void resetFilterDraft() {
        draftFilterType = CorrectChatConfig.TabType.CONTAINS;
        draftFilterValue = "";
        filterPage = 0;
        error = "";
    }

    private List<CorrectChatConfig.ChatTab> tabs() { return draft.tabsFor(serverKey); }
    private List<CorrectChatConfig.ChatWindow> windows() { return draft.windowsFor(serverKey); }
    private CorrectChatConfig.ChatTab tab() { return tabs().get(tabIndex); }
    private CorrectChatConfig.ChatWindow window() { return windows().get(windowIndex); }
    private int indexOfTab(String id) {
        for (int i = 0; i < tabs().size(); i++) if (tabs().get(i).id.equals(id)) return i;
        return 0;
    }
    private void setActiveTab(String id) {
        if (windowIndex < 0) draft.setPrimaryTabFor(serverKey, id);
        else window().activeTabId = id;
    }
    private void replaceRemovedTab(String removed, String replacement) {
        if (draft.primaryTabFor(serverKey).equals(removed)) draft.setPrimaryTabFor(serverKey, replacement);
        for (CorrectChatConfig.ChatWindow value : windows()) {
            if (value.activeTabId.equals(removed)) value.activeTabId = replacement;
        }
    }
    private EditBox field(int x, int y, int w, String label, String value) {
        EditBox box = new EditBox(font, x + 105, y, w - 105, 20, Component.literal(label));
        box.setMaxLength(512); box.setValue(value == null ? "" : value); addRenderableWidget(box);
        labels.add(new Label(Component.literal(label), x, y)); return box;
    }
    private static int number(String value, String name) {
        try { return Integer.parseInt(value.trim()); }
        catch (NumberFormatException exception) { throw new IllegalArgumentException(name + " muss eine ganze Zahl sein"); }
    }
    private record Label(Component text, int x, int y) { }
}
