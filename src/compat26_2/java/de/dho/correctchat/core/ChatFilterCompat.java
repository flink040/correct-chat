package de.dho.correctchat.core;

import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.client.multiplayer.chat.GuiMessageSource;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.function.Predicate;

/** Small adapter for chat APIs that changed between 1.21.11 and 26.2. */
final class ChatFilterCompat {
    private ChatFilterCompat() { }

    static void apply(ChatComponent chat, Predicate<GuiMessage> filter) {
        chat.setVisibleMessageFilter(filter);
    }

    static Component componentAt(List<GuiMessage.Line> lines, int index) {
        return lines.get(index).parent().content();
    }

    static ChatWorkspace.Kind fallbackKind(GuiMessage message) {
        return message.source() == GuiMessageSource.PLAYER
                ? ChatWorkspace.Kind.PLAYER
                : ChatWorkspace.Kind.SYSTEM;
    }
}
