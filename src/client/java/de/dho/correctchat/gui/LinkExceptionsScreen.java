package de.dho.correctchat.gui;

import de.dho.correctchat.core.Localizer;

import de.dho.correctchat.config.CorrectChatConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

final class LinkExceptionsScreen extends Screen {
    private final Screen parent;
    private final CorrectChatConfig config;
    private final List<Row> visibleRows = new ArrayList<>();
    private int page;

    LinkExceptionsScreen(Screen parent, CorrectChatConfig config) {
        super(Localizer.literal("Erlaubte URLs"));
        this.parent = parent;
        this.config = config;
    }

    @Override
    protected void init() {
        visibleRows.clear();
        int rows = rowsPerPage();
        int pages = pageCount(rows);
        page = Math.min(page, pages - 1);
        int from = page * rows;
        int to = Math.min(config.linkExceptions.size(), from + rows);
        int contentWidth = Math.min(360, width - 24);
        int x = (width - contentWidth) / 2;

        for (int i = from; i < to; i++) {
            int index = i;
            int y = 40 + (i - from) * 24;
            EditBox field = new EditBox(font, x + 25, y, contentWidth - 105, 20,
                    Localizer.literal("Erlaubte URL " + (i + 1)));
            field.setMaxLength(512);
            field.setValue(config.linkExceptions.get(i));
            addRenderableWidget(field);
            visibleRows.add(new Row(i, field));
            addRenderableWidget(Button.builder(Localizer.literal("Löschen").withStyle(ChatFormatting.RED), button -> {
                storeVisibleRows();
                config.linkExceptions.remove(index);
                rebuildWidgets();
            }).bounds(x + contentWidth - 76, y, 76, 20).build());
        }

        int bottom = height - 28;
        int addWidth = Math.min(100, Math.max(72, contentWidth - 196));
        addRenderableWidget(Button.builder(Localizer.literal("+ URL").withStyle(ChatFormatting.GREEN), button -> {
            storeVisibleRows();
            config.linkExceptions.add("");
            page = (config.linkExceptions.size() - 1) / rows;
            rebuildWidgets();
        }).bounds(x, bottom, addWidth, 20).build());

        int pagerX = x + addWidth + 4;

        Button previous = Button.builder(Localizer.literal("◀"), button -> {
            storeVisibleRows();
            page--;
            rebuildWidgets();
        }).bounds(pagerX, bottom, 28, 20).build();
        previous.active = page > 0;
        addRenderableWidget(previous);

        Button indicator = Button.builder(Localizer.literal((page + 1) + "/" + pages), button -> { })
                .bounds(pagerX + 32, bottom, 40, 20).build();
        indicator.active = false;
        addRenderableWidget(indicator);

        Button next = Button.builder(Localizer.literal("▶"), button -> {
            storeVisibleRows();
            page++;
            rebuildWidgets();
        }).bounds(pagerX + 76, bottom, 28, 20).build();
        next.active = page < pages - 1;
        addRenderableWidget(next);

        addRenderableWidget(Button.builder(Localizer.literal("Zurück"), button -> onClose())
                .bounds(x + contentWidth - 72, bottom, 72, 20).build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        int contentWidth = Math.min(360, width - 24);
        int x = (width - contentWidth) / 2;
        graphics.centeredText(font, title, width / 2, 12, 0xFFFFFFFF);
        graphics.centeredText(font, Localizer.literal("Eine Domain oder URL pro Zeile · Beispiel: twitch.tv/eigenername"),
                width / 2, 24, 0xFFAAAAAA);
        for (int i = 0; i < visibleRows.size(); i++) {
            graphics.text(font, Localizer.literal((visibleRows.get(i).index + 1) + "."), x, 46 + i * 24, 0xFFD9DFDC, true);
        }
        if (config.linkExceptions.isEmpty()) {
            graphics.centeredText(font, Localizer.literal("Noch sind keine URLs erlaubt."), width / 2, height / 2 - 8, 0xFFE0E0E0);
            graphics.centeredText(font, Localizer.literal("Nicht freigegebene Links benötigen vor dem Senden eine Bestätigung."),
                    width / 2, height / 2 + 5, 0xFF999999);
        }
    }

    @Override
    public void onClose() {
        storeVisibleRows();
        config.linkExceptions.removeIf(value -> value == null || value.isBlank());
        minecraft.gui.setScreen(parent);
    }

    @Override public boolean isPauseScreen() { return false; }

    private void storeVisibleRows() {
        for (Row row : visibleRows) {
            if (row.index < config.linkExceptions.size()) {
                config.linkExceptions.set(row.index, row.field.getValue().trim());
            }
        }
    }

    private int rowsPerPage() { return Math.max(1, (height - 78) / 24); }
    private int pageCount(int rows) { return Math.max(1, (config.linkExceptions.size() + rows - 1) / rows); }
    private record Row(int index, EditBox field) { }
}
