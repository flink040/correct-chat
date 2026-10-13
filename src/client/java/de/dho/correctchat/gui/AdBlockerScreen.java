package de.dho.correctchat.gui;

import de.dho.correctchat.core.Localizer;

import de.dho.correctchat.config.CorrectChatConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Lists messages explicitly hidden through the chat context menu. */
final class AdBlockerScreen extends Screen {
    private final Screen parent;
    private final CorrectChatConfig config;
    private int page;

    AdBlockerScreen(Screen parent, CorrectChatConfig config) {
        super(Localizer.literal("Ad-Blocker"));
        this.parent = parent;
        this.config = config;
    }

    @Override
    protected void init() {
        int rows = rowsPerPage();
        int pages = pageCount(rows);
        page = Math.min(page, pages - 1);
        int from = page * rows;
        int to = Math.min(config.blockedAdvertisements.size(), from + rows);
        int contentWidth = Math.min(460, width - 24);
        int x = (width - contentWidth) / 2;

        for (int i = from; i < to; i++) {
            int index = i;
            int y = 40 + (i - from) * 24;
            String message = compact(config.blockedAdvertisements.get(i), 70);
            Button preview = Button.builder(Component.literal(message), button ->
                    minecraft.gui.setScreen(new AdvertisementRuleEditorScreen(this, config, index)))
                    .bounds(x, y, contentWidth - 106, 20).build();
            addRenderableWidget(preview);
            addRenderableWidget(Button.builder(Localizer.literal("Freischalten").withStyle(ChatFormatting.GREEN), button -> {
                config.blockedAdvertisements.remove(index);
                rebuildWidgets();
            }).bounds(x + contentWidth - 102, y, 102, 20).build());
        }

        int bottom = height - 28;
        int cursor = x;
        Button previous = Button.builder(Localizer.literal("◀"), button -> {
            page--;
            rebuildWidgets();
        }).bounds(cursor, bottom, 30, 20).build();
        previous.active = page > 0;
        addRenderableWidget(previous);

        Button indicator = Button.builder(Localizer.literal((page + 1) + "/" + pages), button -> { })
                .bounds(cursor + 34, bottom, 40, 20).build();
        indicator.active = false;
        addRenderableWidget(indicator);

        Button next = Button.builder(Localizer.literal("▶"), button -> {
            page++;
            rebuildWidgets();
        }).bounds(cursor + 78, bottom, 30, 20).build();
        next.active = page < pages - 1;
        addRenderableWidget(next);

        addRenderableWidget(Button.builder(Localizer.literal("+ Muster").withStyle(ChatFormatting.GREEN), button ->
                minecraft.gui.setScreen(new AdvertisementRuleEditorScreen(this, config, -1)))
                .bounds(cursor + 116, bottom, 76, 20).build());

        addRenderableWidget(Button.builder(Localizer.literal("Zurück"), button -> onClose())
                .bounds(x + contentWidth - 72, bottom, 72, 20).build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        graphics.centeredText(font, title, width / 2, 12, 0xFFFFFFFF);
        graphics.centeredText(font, Localizer.literal("Einträge anklicken zum Bearbeiten · * steht für beliebigen Text."),
                width / 2, 24, 0xFFAAAAAA);
        if (config.blockedAdvertisements.isEmpty()) {
            graphics.centeredText(font, Localizer.literal("Keine Nachrichten als Werbung markiert."),
                    width / 2, height / 2 - 8, 0xFFE0E0E0);
            graphics.centeredText(font, Localizer.literal("Rechtsklick auf eine Chatnachricht → Als Werbung markieren"),
                    width / 2, height / 2 + 5, 0xFF999999);
        }
    }

    @Override public void onClose() { minecraft.gui.setScreen(parent); }
    @Override public boolean isPauseScreen() { return false; }

    private int rowsPerPage() { return Math.max(1, (height - 78) / 24); }
    private int pageCount(int rows) {
        return Math.max(1, (config.blockedAdvertisements.size() + rows - 1) / rows);
    }

    private static String compact(String value, int maximum) {
        String singleLine = value.replace('\n', ' ').replace('\r', ' ');
        return singleLine.length() <= maximum ? singleLine : singleLine.substring(0, maximum - 1) + "…";
    }
}
