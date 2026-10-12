package de.dho.correctchat.core;

import de.dho.correctchat.config.CorrectChatConfig;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;
import java.util.function.Function;
import java.util.function.IntUnaryOperator;

/** Draws additional chat panels without owning workspace interaction state. */
final class ChatWindowRenderer {
    @FunctionalInterface
    interface MessageMatcher {
        boolean matches(CorrectChatConfig.ChatTab tab, Component component, ChatWorkspace.Kind kind);
    }

    private ChatWindowRenderer() {
    }

    static void render(GuiGraphicsExtractor graphics, Font font,
                       List<CorrectChatConfig.ChatWindow> windows,
                       List<ChatHistoryStore.Entry> entries,
                       boolean focused,
                       IntUnaryOperator primaryWidth,
                       IntUnaryOperator primaryTop,
                       Function<String, CorrectChatConfig.ChatTab> findTab,
                       MessageMatcher matcher) {
        if (focused) renderPrimaryResizeHandle(graphics, primaryWidth, primaryTop);
        for (CorrectChatConfig.ChatWindow window : windows) {
            if (window.visible) renderWindow(graphics, font, window, entries, focused, findTab, matcher);
        }
    }

    static int resolvedX(CorrectChatConfig.ChatWindow window, int screenWidth) {
        int x = window.x < 0 ? screenWidth + window.x : window.x;
        return Math.max(0, Math.min(x, Math.max(0, screenWidth - window.width)));
    }

    static int resolvedY(CorrectChatConfig.ChatWindow window, int screenHeight) {
        return Math.max(0, Math.min(window.y, Math.max(0, screenHeight - window.height)));
    }

    private static void renderPrimaryResizeHandle(GuiGraphicsExtractor graphics,
                                                  IntUnaryOperator primaryWidth,
                                                  IntUnaryOperator primaryTop) {
        int right = 4 + primaryWidth.applyAsInt(graphics.guiWidth());
        int top = primaryTop.applyAsInt(graphics.guiHeight());
        graphics.fill(right - 14, top, right, top + 14, 0xC0222222);
        graphics.horizontalLine(right - 11, right - 2, top + 2, 0xFFE0E0E0);
        graphics.verticalLine(right - 2, top + 2, top + 11, 0xFFE0E0E0);
        graphics.horizontalLine(right - 6, right - 2, top + 7, 0xFFAAAAAA);
        graphics.verticalLine(right - 7, top + 2, top + 6, 0xFFAAAAAA);
    }

    private static void renderWindow(GuiGraphicsExtractor graphics, Font font,
                                     CorrectChatConfig.ChatWindow window,
                                     List<ChatHistoryStore.Entry> entries,
                                     boolean focused,
                                     Function<String, CorrectChatConfig.ChatTab> findTab,
                                     MessageMatcher matcher) {
        int x = resolvedX(window, graphics.guiWidth());
        int y = resolvedY(window, graphics.guiHeight());
        int width = Math.min(window.width, graphics.guiWidth() - x);
        int height = Math.min(window.height, graphics.guiHeight() - y);
        if (width < 40 || height < 28) return;

        int headerHeight = focused ? 34 : 12;
        graphics.fill(x, y, x + width, y + height, focused ? 0xB0101010 : 0x80101010);
        graphics.fill(x, y, x + width, y + headerHeight, 0xD0222222);
        CorrectChatConfig.ChatTab active = findTab.apply(window.activeTabId);
        String title = window.name + (focused ? "  ↕ ziehen" : active == null ? "" : " · " + active.name);
        graphics.text(font, title, x + 3, y + 2, 0xFFFFFFFF, true);
        if (focused) renderResizeHandle(graphics, x, y, width, height);

        int contentTop = y + headerHeight + 2;
        int contentBottom = y + height - 2;
        int lineY = contentBottom - font.lineHeight;
        long visibleSince = System.currentTimeMillis() - 10_000L;
        graphics.enableScissor(x + 2, contentTop, x + width - 2, contentBottom);
        outer:
        for (int i = entries.size() - 1; i >= 0; i--) {
            ChatHistoryStore.Entry entry = entries.get(i);
            if (!focused && entry.receivedAt < visibleSince) continue;
            if (!matcher.matches(active, entry.component, entry.kind)) continue;
            List<FormattedCharSequence> lines = font.split(entry.component, Math.max(20, width - 8));
            for (int line = lines.size() - 1; line >= 0; line--) {
                if (lineY < contentTop) break outer;
                graphics.text(font, lines.get(line), x + 4, lineY, 0xFFFFFFFF, true);
                lineY -= font.lineHeight;
            }
        }
        graphics.disableScissor();
    }

    private static void renderResizeHandle(GuiGraphicsExtractor graphics, int x, int y, int width, int height) {
        int right = x + width - 3;
        int bottom = y + height - 3;
        graphics.horizontalLine(right - 9, right, bottom, 0xFFE0E0E0);
        graphics.verticalLine(right, bottom - 9, bottom, 0xFFE0E0E0);
        graphics.horizontalLine(right - 4, right, bottom - 5, 0xFFAAAAAA);
        graphics.verticalLine(right - 5, bottom - 4, bottom, 0xFFAAAAAA);
    }
}
