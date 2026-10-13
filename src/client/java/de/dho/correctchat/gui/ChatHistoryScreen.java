package de.dho.correctchat.gui;

import de.dho.correctchat.core.Localizer;

import de.dho.correctchat.CorrectChatClient;
import de.dho.correctchat.config.CorrectChatConfig;
import de.dho.correctchat.core.ChatWorkspace;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/** Searchable view over the in-memory Correct Chat history. */
public final class ChatHistoryScreen extends Screen {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault());
    private final Screen parent;
    private final List<CorrectChatConfig.ChatTab> tabs;
    private String selectedTabId;
    private EditBox query;
    private String queryValue = "";
    private List<ChatWorkspace.SearchResult> results = List.of();
    private int page;

    public ChatHistoryScreen(Screen parent, String selectedTabId) {
        super(Localizer.literal("Chatverlauf durchsuchen"));
        this.parent = parent;
        this.tabs = new ArrayList<>(CorrectChatClient.workspace().activeTabs());
        this.selectedTabId = selectedTabId;
    }

    @Override protected void init() {
        int contentWidth = Math.min(420, width - 24);
        int left = (width - contentWidth) / 2;
        int scopeWidth = Math.min(92, Math.max(70, contentWidth / 4));
        int searchWidth = 70;
        int queryWidth = Math.max(80, contentWidth - scopeWidth - searchWidth - 12);
        query = new EditBox(font, left, 34, queryWidth, 20, Localizer.literal("Suchtext"));
        query.setMaxLength(256);
        query.setValue(queryValue);
        addRenderableWidget(query);
        addRenderableWidget(Button.builder(Localizer.literal("Suchen"), button -> refresh())
                .bounds(left + queryWidth + 4, 34, searchWidth, 20).build());
        addRenderableWidget(Button.builder(Localizer.literal(scopeName()), button -> {
            int current = indexOfSelected();
            current = (current + 1) % (tabs.size() + 1);
            selectedTabId = current == 0 ? null : tabs.get(current - 1).id;
            button.setMessage(Localizer.literal(scopeName()));
            refresh();
        }).bounds(left + queryWidth + searchWidth + 8, 34, scopeWidth, 20).build());

        int bottom = height - 28;
        Button previous = Button.builder(Localizer.literal("◀"), button -> { page = Math.max(0, page - 1); rebuildWidgets(); })
                .bounds(width / 2 - 104, bottom, 42, 20).build();
        previous.active = page > 0;
        addRenderableWidget(previous);
        int pages = Math.max(1, (results.size() + rows() - 1) / rows());
        Button indicator = Button.builder(Localizer.literal((page + 1) + "/" + pages), button -> { })
                .bounds(width / 2 - 56, bottom, 52, 20).build();
        indicator.active = false;
        addRenderableWidget(indicator);
        Button next = Button.builder(Localizer.literal("▶"), button -> { page = Math.min(pages - 1, page + 1); rebuildWidgets(); })
                .bounds(width / 2 + 2, bottom, 42, 20).build();
        next.active = page + 1 < pages;
        addRenderableWidget(next);
        addRenderableWidget(Button.builder(Localizer.literal("Zurück"), button -> onClose())
                .bounds(width / 2 + 50, bottom, 104, 20).build());
        if (results.isEmpty()) refresh(false);
    }

    private void refresh() { refresh(true); }

    private void refresh(boolean rebuild) {
        queryValue = query == null ? queryValue : query.getValue();
        results = CorrectChatClient.workspace().search(queryValue, selectedTabId, 1000);
        page = 0;
        if (rebuild) rebuildWidgets();
    }

    @Override public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        graphics.centeredText(font, title, width / 2, 12, 0xFFFFFFFF);
        int contentWidth = Math.min(420, width - 24);
        int left = (width - contentWidth) / 2;
        int top = 63;
        int rowHeight = 18;
        int from = page * rows();
        int to = Math.min(results.size(), from + rows());
        graphics.enableScissor(left, top, left + contentWidth, height - 34);
        for (int i = from; i < to; i++) {
            ChatWorkspace.SearchResult result = results.get(i);
            int y = top + (i - from) * rowHeight;
            graphics.fill(left, y, left + contentWidth, y + 16, (i & 1) == 0 ? 0x70101010 : 0x70404040);
            Component line = Localizer.literal(TIME.format(Instant.ofEpochMilli(result.receivedAt())) + " ")
                    .withStyle(ChatFormatting.DARK_GRAY).append(result.component().copy());
            graphics.text(font, line, left + 4, y + 4, 0xFFFFFFFF, false);
        }
        graphics.disableScissor();
        if (results.isEmpty()) graphics.centeredText(font, Localizer.literal("Keine passenden Nachrichten."), width / 2, 82, 0xFFAAAAAA);
        graphics.text(font, Localizer.literal(results.size() + " Treffer · max. 1000"), left, height - 42, 0xFFAAAAAA, false);
    }

    private int rows() { return Math.max(1, (height - 105) / 18); }

    private int indexOfSelected() {
        if (selectedTabId == null) return 0;
        for (int i = 0; i < tabs.size(); i++) if (tabs.get(i).id.equals(selectedTabId)) return i + 1;
        return 0;
    }

    private String scopeName() {
        int index = indexOfSelected();
        return index == 0 ? "Alle Tabs" : tabs.get(index - 1).name;
    }

    @Override public void onClose() { minecraft.gui.setScreen(parent); }
    @Override public boolean isPauseScreen() { return false; }
}
