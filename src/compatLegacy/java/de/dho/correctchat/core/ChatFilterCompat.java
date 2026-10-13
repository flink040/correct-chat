package de.dho.correctchat.core;

import de.dho.correctchat.mixin.ChatComponentAccessor;
import net.minecraft.client.GuiMessage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;

import java.util.IdentityHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.Objects;

/** Implements 26.2's visible chat predicate on the pre-26.1.2 chat storage API. */
final class ChatFilterCompat {
    private static final Map<GuiMessage.Line, Component> COMPONENTS = new IdentityHashMap<>();
    private static final List<GuiMessage> ALL_MESSAGES = new ArrayList<>();
    private static String contextKey;

    private ChatFilterCompat() { }

    @SuppressWarnings("unchecked")
    static void apply(ChatComponent chat, Predicate<GuiMessage> filter) {
        ChatComponentAccessor accessor = (ChatComponentAccessor) chat;
        List<GuiMessage> messages = (List<GuiMessage>)(List<?>)accessor.correctchat$getAllMessages();
        String currentContext = currentContextKey();
        if (!Objects.equals(contextKey, currentContext)) {
            ALL_MESSAGES.clear();
            contextKey = currentContext;
        }
        List<GuiMessage> newMessages = new ArrayList<>();
        for (GuiMessage message : messages) {
            if (!isRemembered(message)) newMessages.add(message);
        }
        ALL_MESSAGES.addAll(0, newMessages);
        messages.clear();
        ALL_MESSAGES.stream().filter(filter).forEach(messages::add);
        chat.rescaleChat();

        COMPONENTS.clear();
        List<GuiMessage.Line> lines = accessor.correctchat$getTrimmedMessages();
        int messageIndex = 0;
        for (GuiMessage.Line line : lines) {
            if (messageIndex >= messages.size()) break;
            COMPONENTS.put(line, messages.get(messageIndex).content());
            if (line.endOfEntry()) messageIndex++;
        }
    }

    static Component componentAt(List<GuiMessage.Line> lines, int index) {
        return COMPONENTS.get(lines.get(index));
    }

    static ChatWorkspace.Kind fallbackKind(GuiMessage message) {
        return ChatWorkspace.Kind.SYSTEM;
    }

    private static boolean isRemembered(GuiMessage candidate) {
        for (GuiMessage remembered : ALL_MESSAGES) if (remembered == candidate) return true;
        return false;
    }

    private static String currentContextKey() {
        Minecraft client = Minecraft.getInstance();
        if (client.hasSingleplayerServer()) return "singleplayer";
        return client.getCurrentServer() == null ? "" : client.getCurrentServer().ip;
    }
}
