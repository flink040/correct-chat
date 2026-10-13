package de.dho.correctchat.core;

import de.dho.correctchat.CorrectChatClient;
import de.dho.correctchat.config.CorrectChatConfig;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.joml.Matrix3x2fStack;

import java.util.ArrayDeque;
import java.util.Deque;

/** Renders the current local alert independently of server HUD packets. */
final class ChatAlertOverlay {
    private static final int MAX_PENDING = 20;
    private Active active;
    private final Deque<Active> pending = new ArrayDeque<>();

    void register() {
        HudElementRegistry.addLast(
                Identifier.fromNamespaceAndPath(CorrectChatClient.MOD_ID, "chat_alert"),
                (graphics, delta) -> render(graphics));
    }

    void show(CorrectChatConfig.ChatAlert alert, String message) {
        String text = alert.text.replace("%message%", message);
        Component component = LegacyText.parse(alert.color.code + text);
        long durationMillis = Math.round(alert.durationSeconds * 1000.0F);
        Active next = new Active(component, alert.display, alert.fontScalePercent / 100.0F,
                color(alert.color), durationMillis, System.currentTimeMillis() + durationMillis);
        if (active == null) active = next;
        else if (pending.size() < MAX_PENDING) pending.addLast(next);
    }

    void clear() {
        active = null;
        pending.clear();
    }

    private void render(GuiGraphicsExtractor graphics) {
        long now = System.currentTimeMillis();
        Active shown = active;
        while (shown != null && now >= shown.expiresAt) {
            shown = pending.pollFirst();
            if (shown != null) shown = shown.startingAt(now);
            active = shown;
        }
        if (shown == null) return;

        Minecraft client = Minecraft.getInstance();
        int screenWidth = client.getWindow().getGuiScaledWidth();
        int screenHeight = client.getWindow().getGuiScaledHeight();
        float scale = shown.scale;
        Matrix3x2fStack matrices = graphics.pose();
        matrices.pushMatrix();
        matrices.scale(scale, scale);
        int logicalWidth = Math.round(screenWidth / scale);
        int textWidth = client.font.width(shown.text);
        int x = (logicalWidth - textWidth) / 2;

        switch (shown.display) {
            case TITLE -> {
                int y = Math.round((screenHeight * 0.42F) / scale);
                graphics.fill(x - 7, y - 5, x + textWidth + 7, y + client.font.lineHeight + 5, 0x90000000);
                graphics.centeredText(client.font, shown.text, logicalWidth / 2, y, 0xFFFFFFFF);
            }
            case ACTIONBAR -> {
                int y = Math.round((screenHeight - 62) / scale);
                graphics.fill(x - 6, y - 4, x + textWidth + 6, y + client.font.lineHeight + 4, 0xA0000000);
                graphics.centeredText(client.font, shown.text, logicalWidth / 2, y, 0xFFFFFFFF);
            }
            case BOSSBAR -> {
                int y = Math.round(16 / scale);
                int barWidth = Math.min(220, logicalWidth - 24);
                int barX = (logicalWidth - barWidth) / 2;
                graphics.centeredText(client.font, shown.text, logicalWidth / 2, y, 0xFFFFFFFF);
                int barY = y + client.font.lineHeight + 3;
                graphics.fill(barX - 1, barY - 1, barX + barWidth + 1, barY + 7, 0xE0000000);
                graphics.fill(barX, barY, barX + barWidth, barY + 6, shown.argb);
                graphics.fill(barX + 2, barY + 1, barX + barWidth - 2, barY + 3, 0x40FFFFFF);
            }
        }
        matrices.popMatrix();
    }

    private static int color(CorrectChatConfig.ChatColor color) {
        return switch (color) {
            case BLACK -> 0xFF202020;
            case DARK_BLUE -> 0xFF0000AA;
            case DARK_GREEN -> 0xFF00AA00;
            case DARK_AQUA -> 0xFF00AAAA;
            case DARK_RED -> 0xFFAA0000;
            case DARK_PURPLE -> 0xFFAA00AA;
            case GOLD -> 0xFFFFAA00;
            case GRAY -> 0xFFAAAAAA;
            case DARK_GRAY -> 0xFF555555;
            case BLUE -> 0xFF5555FF;
            case GREEN -> 0xFF55FF55;
            case AQUA -> 0xFF55FFFF;
            case RED, NONE -> 0xFFFF5555;
            case LIGHT_PURPLE -> 0xFFFF55FF;
            case YELLOW -> 0xFFFFFF55;
            case WHITE -> 0xFFFFFFFF;
        };
    }

    private record Active(Component text, CorrectChatConfig.AlertDisplay display, float scale, int argb,
                          long durationMillis, long expiresAt) {
        Active startingAt(long now) {
            return new Active(text, display, scale, argb, durationMillis, now + durationMillis);
        }
    }
}
