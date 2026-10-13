package de.dho.correctchat.gui;

import de.dho.correctchat.core.Localizer;

import de.dho.correctchat.core.ModCatalog;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/** Searchable list of Fabric mods that were loaded directly from the user's mods directory. */
final class ModFilterPickerScreen extends Screen {
    private final Screen parent;
    private final Consumer<ModCatalog.Entry> selection;
    private String queryValue = "";
    private EditBox query;
    private int page;
    private boolean noMatches;

    ModFilterPickerScreen(Screen parent, Consumer<ModCatalog.Entry> selection) {
        super(Localizer.literal("Installierte Mod auswählen"));
        this.parent = parent;
        this.selection = selection;
    }

    @Override protected void init() {
        int contentWidth = Math.min(360, width - 24);
        int x = (width - contentWidth) / 2;
        int searchWidth = Math.min(76, contentWidth / 3);
        query = new EditBox(font, x, 34, contentWidth - searchWidth - 6, 20, Localizer.literal("Mod suchen"));
        query.setMaxLength(128);
        query.setValue(queryValue);
        addRenderableWidget(query);
        addRenderableWidget(Button.builder(Localizer.literal("Suchen"), button -> {
            queryValue = query.getValue().trim();
            page = 0;
            rebuildWidgets();
        }).bounds(x + contentWidth - searchWidth, 34, searchWidth, 20).build());

        List<ModCatalog.Entry> mods = filteredMods();
        noMatches = mods.isEmpty();
        int rows = rows();
        int pages = Math.max(1, (mods.size() + rows - 1) / rows);
        page = Math.min(page, pages - 1);
        int from = page * rows;
        int to = Math.min(mods.size(), from + rows);
        for (int index = from; index < to; index++) {
            ModCatalog.Entry mod = mods.get(index);
            int y = 64 + (index - from) * 24;
            String hints = mod.messageHints() == 1 ? "1 Meldungshinweis" : mod.messageHints() + " Meldungshinweise";
            String label = mod.name() + "  [" + mod.id() + "]  · " + hints;
            addRenderableWidget(Button.builder(Localizer.literal(label), button -> {
                selection.accept(mod);
                minecraft.gui.setScreen(parent);
            }).bounds(x, y, contentWidth, 20).build());
        }

        int bottom = height - 28;
        Button previous = Button.builder(Localizer.literal("◀"), button -> { page--; rebuildWidgets(); })
                .bounds(width / 2 - 104, bottom, 42, 20).build();
        previous.active = page > 0;
        addRenderableWidget(previous);
        addRenderableWidget(Button.builder(Localizer.literal((page + 1) + "/" + pages), button -> { })
                .bounds(width / 2 - 56, bottom, 52, 20).build()).active = false;
        Button next = Button.builder(Localizer.literal("▶"), button -> { page++; rebuildWidgets(); })
                .bounds(width / 2 + 2, bottom, 42, 20).build();
        next.active = page + 1 < pages;
        addRenderableWidget(next);
        addRenderableWidget(Button.builder(Localizer.literal("Zurück"), button -> onClose())
                .bounds(width / 2 + 50, bottom, 104, 20).build());
    }

    private List<ModCatalog.Entry> filteredMods() {
        String needle = queryValue.toLowerCase(Locale.ROOT);
        return ModCatalog.installedMods().stream().filter(mod -> needle.isEmpty()
                || mod.id().toLowerCase(Locale.ROOT).contains(needle)
                || mod.name().toLowerCase(Locale.ROOT).contains(needle)).toList();
    }

    private int rows() { return Math.max(1, (height - 112) / 24); }

    @Override public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        graphics.centeredText(font, title, width / 2, 12, 0xFFFFFFFF);
        if (noMatches) {
            graphics.centeredText(font, Localizer.literal("Keine passende Mod direkt im mods-Ordner gefunden.")
                    .withStyle(ChatFormatting.GRAY), width / 2, 82, 0xFFAAAAAA);
        }
        graphics.centeredText(font, Localizer.literal(
                "JAR-Hinweise: Übersetzungsschlüssel und geeignete Sprachvorlagen"), width / 2, height - 41, 0xFF9DA6A1);
    }

    @Override public void onClose() { minecraft.gui.setScreen(parent); }
    @Override public boolean isPauseScreen() { return false; }
}
