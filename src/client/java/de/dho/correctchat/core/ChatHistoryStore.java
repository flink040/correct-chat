package de.dho.correctchat.core;

import net.minecraft.network.chat.Component;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/** Bounded in-memory history and identity-based message metadata. */
final class ChatHistoryStore {
    static final class Entry {
        final Component component;
        final ChatWorkspace.Kind kind;
        final long receivedAt;
        final String server;

        Entry(Component component, ChatWorkspace.Kind kind, long receivedAt, String server) {
            this.component = component;
            this.kind = kind;
            this.receivedAt = receivedAt;
            this.server = server;
        }
    }

    private final Deque<Entry> entries = new ArrayDeque<>();
    private final Map<Component, ChatWorkspace.Kind> componentKinds = new IdentityHashMap<>();

    void add(Component component, ChatWorkspace.Kind kind, String server) {
        entries.addLast(new Entry(component, kind, System.currentTimeMillis(), server));
        componentKinds.put(component, kind);
    }

    List<Entry> snapshot() {
        return new ArrayList<>(entries);
    }

    ChatWorkspace.Kind kindOf(Component component, ChatWorkspace.Kind fallback) {
        return componentKinds.getOrDefault(component, fallback);
    }

    List<Component> trim(int maximum) {
        List<Component> removed = new ArrayList<>();
        while (entries.size() > maximum) {
            Entry entry = entries.removeFirst();
            componentKinds.remove(entry.component);
            removed.add(entry.component);
        }
        return removed;
    }
}
