package de.dho.correctchat.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class CorrectChatConfig {
    static final String DM_FILTER = "(?i)\\[(?:du\\s*->\\s*[^\\]]+|[^\\]]+\\s*->\\s*mir)\\]";

    public int configVersion = 10;
    public boolean enabled = true;

    public boolean antiCommandChoker = true;
    public boolean chatTime = true;
    public String timeStyle = "&e┃ &6%time% &8» &r";
    public String timeFormat = "HH:mm:ss";

    public boolean chatLog = true;
    public int maxStoredMessages = 1000;
    public ChatLogFormat chatLogFormat = ChatLogFormat.JSON;
    public int chatLogRetention = 50;
    public boolean uploadEnabled = false;
    public String uploadEndpoint = "http://chatlog-api.hdskins.de/api/chatlog";

    public boolean textReplacement = true;
    public boolean chatListeners = true;
    public boolean copyButton = false;
    public boolean chatMessagePreview = true;
    public String copyFormat = "&r[&aCopy&r] ";
    public String copyTooltip = "Click to copy.";
    public boolean antiSpam = false;
    public int maxResponses = 2;
    public int responseWindowMinutes = 5;
    public boolean linkFilter = false;
    public List<String> linkExceptions = new ArrayList<>();
    public List<String> ignoredPlayers = new ArrayList<>();

    public boolean chatTabsEnabled = true;
    public boolean unreadBadges = true;
    public boolean mentionHighlight = true;
    public String primaryTabId = "all";
    public List<ChatTab> tabs = defaultTabs();
    public List<ChatWindow> windows = defaultWindows();
    public List<ServerProfile> serverProfiles = new ArrayList<>();

    public boolean twitchEnabled = false;
    public String twitchChannel = "";
    public String twitchUsername = "";
    public String twitchOauthToken = "";

    public List<TextReplacement> replacements = new ArrayList<>();
    public List<ChatListener> listeners = new ArrayList<>();

    public void normalize() {
        ConfigNormalizer.normalize(this);
    }

    public CorrectChatConfig copy() {
        CorrectChatConfig copy = new CorrectChatConfig();
        copy.configVersion = configVersion;
        copy.enabled = enabled;
        copy.antiCommandChoker = antiCommandChoker;
        copy.chatTime = chatTime;
        copy.timeStyle = timeStyle;
        copy.timeFormat = timeFormat;
        copy.chatLog = chatLog;
        copy.maxStoredMessages = maxStoredMessages;
        copy.chatLogFormat = chatLogFormat;
        copy.chatLogRetention = chatLogRetention;
        copy.uploadEnabled = uploadEnabled;
        copy.uploadEndpoint = uploadEndpoint;
        copy.textReplacement = textReplacement;
        copy.chatListeners = chatListeners;
        copy.copyButton = copyButton;
        copy.chatMessagePreview = chatMessagePreview;
        copy.copyFormat = copyFormat;
        copy.copyTooltip = copyTooltip;
        copy.antiSpam = antiSpam;
        copy.maxResponses = maxResponses;
        copy.responseWindowMinutes = responseWindowMinutes;
        copy.linkFilter = linkFilter;
        copy.linkExceptions = new ArrayList<>(linkExceptions);
        copy.ignoredPlayers = new ArrayList<>(ignoredPlayers);
        copy.chatTabsEnabled = chatTabsEnabled;
        copy.unreadBadges = unreadBadges;
        copy.mentionHighlight = mentionHighlight;
        copy.primaryTabId = primaryTabId;
        replacements.forEach(value -> copy.replacements.add(value.copy()));
        listeners.forEach(value -> copy.listeners.add(value.copy()));
        copy.tabs = new ArrayList<>();
        tabs.forEach(value -> copy.tabs.add(value.copy()));
        copy.windows = new ArrayList<>();
        windows.forEach(value -> copy.windows.add(value.copy()));
        copy.serverProfiles = new ArrayList<>();
        serverProfiles.forEach(value -> copy.serverProfiles.add(value.copy()));
        copy.twitchEnabled = twitchEnabled;
        copy.twitchChannel = twitchChannel;
        copy.twitchUsername = twitchUsername;
        copy.twitchOauthToken = twitchOauthToken;
        return copy;
    }

    public ServerProfile profileFor(String serverKey) {
        if (serverKey == null || serverKey.isBlank()) return null;
        for (ServerProfile profile : serverProfiles) {
            if (profile.enabled && profile.server.equalsIgnoreCase(serverKey.trim())) return profile;
        }
        return null;
    }

    public List<ChatTab> tabsFor(String serverKey) {
        ServerProfile profile = profileFor(serverKey);
        return profile == null ? tabs : profile.tabs;
    }

    public List<ChatWindow> windowsFor(String serverKey) {
        ServerProfile profile = profileFor(serverKey);
        return profile == null ? windows : profile.windows;
    }

    public String primaryTabFor(String serverKey) {
        ServerProfile profile = profileFor(serverKey);
        return profile == null ? primaryTabId : profile.primaryTabId;
    }

    public void setPrimaryTabFor(String serverKey, String tabId) {
        ServerProfile profile = profileFor(serverKey);
        if (profile == null) primaryTabId = tabId;
        else profile.primaryTabId = tabId;
    }

    static List<ChatTab> defaultTabs() {
        List<ChatTab> result = new ArrayList<>();
        result.add(new ChatTab("all", "Alle", TabType.ALL, ""));
        result.add(new ChatTab("players", "Spieler", TabType.PLAYER, ""));
        result.add(new ChatTab("system", "System", TabType.SYSTEM, ""));
        result.add(new ChatTab("dm", "DM", TabType.REGEX, DM_FILTER));
        result.add(new ChatTab("twitch", "Twitch", TabType.TWITCH, ""));
        return result;
    }

    static List<ChatWindow> defaultWindows() {
        List<ChatWindow> result = new ArrayList<>();
        ChatWindow system = new ChatWindow();
        system.name = "System";
        system.x = -244;
        system.y = 36;
        system.width = 240;
        system.height = 96;
        system.activeTabId = "system";
        result.add(system);
        return result;
    }

    private static String safe(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    public static final class TextReplacement {
        public boolean enabled = true;
        public String name = "";
        public String find = "";
        public String replacement = "";
        public boolean onReceive = false;
        public ServerFilter server = new ServerFilter();

        public void normalize() {
            name = name == null ? "" : name;
            find = find == null ? "" : find;
            replacement = replacement == null ? "" : replacement;
            if (server == null) server = new ServerFilter();
            server.normalize();
        }

        public TextReplacement copy() {
            TextReplacement copy = new TextReplacement();
            copy.enabled = enabled;
            copy.name = name;
            copy.find = find;
            copy.replacement = replacement;
            copy.onReceive = onReceive;
            copy.server = server.copy();
            return copy;
        }
    }

    public static final class ChatListener {
        public boolean enabled = true;
        public String name = "";
        public MatchType matchType = MatchType.EQUALS;
        public String match = "";
        public String response = "";
        public boolean cancel = false;
        public float delaySeconds = 0.0F;
        public List<String> blacklist = new ArrayList<>();
        public ServerFilter server = new ServerFilter();
        public Sound sound = new Sound();
        public Cooldown cooldown = new Cooldown();

        public void normalize() {
            name = name == null ? "" : name;
            match = match == null ? "" : match;
            response = response == null ? "" : response;
            if (matchType == null) matchType = MatchType.EQUALS;
            delaySeconds = Math.max(0.0F, Math.min(600.0F, delaySeconds));
            if (blacklist == null) blacklist = new ArrayList<>();
            blacklist.removeIf(value -> value == null || value.isEmpty());
            if (server == null) server = new ServerFilter();
            if (sound == null) sound = new Sound();
            if (cooldown == null) cooldown = new Cooldown();
            server.normalize();
            sound.normalize();
            cooldown.normalize();
        }

        public ChatListener copy() {
            ChatListener copy = new ChatListener();
            copy.enabled = enabled;
            copy.name = name;
            copy.matchType = matchType;
            copy.match = match;
            copy.response = response;
            copy.cancel = cancel;
            copy.delaySeconds = delaySeconds;
            copy.blacklist = new ArrayList<>(blacklist);
            copy.server = server.copy();
            copy.sound = sound.copy();
            copy.cooldown = cooldown.copy();
            return copy;
        }
    }

    public enum MatchType { EQUALS, CONTAINS, REGEX, SIMPLIFIED_REGEX }
    public enum CooldownType { GLOBAL, PARTIAL }
    public enum TabType { ALL, PLAYER, SYSTEM, TWITCH, CONTAINS, REGEX, MOD }
    public enum ChatLogFormat { JSON, TEXT }

    public static final class ChatTab {
        public String id = java.util.UUID.randomUUID().toString();
        public String name = "Neuer Tab";
        /** Kept for backwards-compatible config imports. New code uses filters. */
        public TabType type = TabType.CONTAINS;
        /** Kept for backwards-compatible config imports. New code uses filters. */
        public String filter = "";
        public List<TabFilter> filters = new ArrayList<>();
        /** Legacy context-menu rules, migrated to filters since config version 9. */
        public List<String> routingRules = new ArrayList<>();

        public ChatTab() { }
        public ChatTab(String id, String name, TabType type, String filter) {
            this.id = id; this.name = name; this.type = type; this.filter = filter;
            this.filters.add(new TabFilter(type, filter));
        }
        public void normalize() {
            id = safe(id, java.util.UUID.randomUUID().toString());
            name = safe(name, "Tab");
            if (type == null) type = TabType.ALL;
            filter = filter == null ? "" : filter;
            if (filters == null) filters = new ArrayList<>();
            filters.removeIf(value -> value == null);
            filters.forEach(TabFilter::normalize);
            if (routingRules == null) routingRules = new ArrayList<>();
            routingRules.removeIf(value -> value == null || value.isBlank());
            routingRules.replaceAll(String::trim);
            if (type == TabType.MOD && filter.isBlank()) {
                String normalizedName = name.toLowerCase(Locale.ROOT);
                if (normalizedName.contains("worldedit") || normalizedName.contains("fawe")) filter = "worldedit";
                else if (normalizedName.contains("coreprotect")) filter = "coreprotect";
                else if (normalizedName.contains("anticheat") || normalizedName.contains("vulcan")) filter = "vulcan";
            }
            if (!filters.isEmpty()) {
                type = filters.getFirst().type;
                filter = filters.getFirst().value;
            }
        }
        void migrateLegacyFilters() {
            if (filters == null) filters = new ArrayList<>();
            if (filters.isEmpty()) filters.add(new TabFilter(type, filter));
            if (routingRules != null) {
                for (String rule : routingRules) {
                    if (rule != null && !rule.isBlank()) filters.add(new TabFilter(TabType.REGEX, rule));
                }
                routingRules.clear();
            }
        }
        public boolean hasType(TabType expected) {
            return filters.stream().anyMatch(value -> value.type == expected);
        }
        public boolean isTwitchOnly() {
            return !filters.isEmpty() && filters.stream().allMatch(value -> value.type == TabType.TWITCH);
        }
        public ChatTab copy() {
            ChatTab copy = new ChatTab(id, name, type, filter);
            copy.filters = new ArrayList<>();
            filters.forEach(value -> copy.filters.add(value.copy()));
            copy.routingRules = new ArrayList<>(routingRules);
            return copy;
        }
    }

    public static final class TabFilter {
        public TabType type = TabType.CONTAINS;
        public String value = "";

        public TabFilter() { }
        public TabFilter(TabType type, String value) {
            this.type = type;
            this.value = value;
            normalize();
        }
        public void normalize() {
            if (type == null) type = TabType.CONTAINS;
            value = value == null ? "" : value.trim();
            if (type == TabType.MOD && value.isBlank()) value = "worldedit";
            if (type == TabType.ALL || type == TabType.PLAYER || type == TabType.SYSTEM || type == TabType.TWITCH) {
                value = "";
            }
        }
        public TabFilter copy() { return new TabFilter(type, value); }
    }

    public static final class ChatWindow {
        public String name = "Chatfenster";
        public boolean visible = true;
        public int x = 4;
        public int y = 36;
        public int width = 240;
        public int height = 96;
        public String activeTabId = "all";

        public void normalize() {
            name = safe(name, "Chatfenster");
            width = clamp(width, 100, 600);
            height = clamp(height, 40, 400);
            activeTabId = safe(activeTabId, "all");
        }
        public ChatWindow copy() {
            ChatWindow copy = new ChatWindow();
            copy.name = name; copy.visible = visible; copy.x = x; copy.y = y; copy.width = width; copy.height = height; copy.activeTabId = activeTabId;
            return copy;
        }
    }

    public static final class ServerProfile {
        public String name = "Serverprofil";
        public String server = "";
        public boolean enabled = true;
        public String primaryTabId = "all";
        public List<ChatTab> tabs = defaultTabs();
        public List<ChatWindow> windows = defaultWindows();

        public void normalize() {
            name = safe(name, "Serverprofil");
            server = server == null ? "" : server.trim();
            if (tabs == null) tabs = defaultTabs();
            if (windows == null) windows = defaultWindows();
            tabs.removeIf(value -> value == null);
            windows.removeIf(value -> value == null);
            tabs.forEach(ChatTab::normalize);
            windows.forEach(ChatWindow::normalize);
            if (tabs.isEmpty()) tabs.addAll(defaultTabs());
            if (tabs.stream().noneMatch(tab -> tab.id.equals(primaryTabId))) primaryTabId = tabs.getFirst().id;
            for (ChatWindow window : windows) {
                if (tabs.stream().noneMatch(tab -> tab.id.equals(window.activeTabId))) window.activeTabId = tabs.getFirst().id;
            }
        }

        public ServerProfile copy() {
            ServerProfile copy = new ServerProfile();
            copy.name = name;
            copy.server = server;
            copy.enabled = enabled;
            copy.primaryTabId = primaryTabId;
            copy.tabs = new ArrayList<>();
            tabs.forEach(value -> copy.tabs.add(value.copy()));
            copy.windows = new ArrayList<>();
            windows.forEach(value -> copy.windows.add(value.copy()));
            return copy;
        }
    }

    public static final class ServerFilter {
        public boolean enabled = false;
        public boolean singlePlayer = true;
        public String address = "";

        public void normalize() { address = address == null ? "" : address.trim(); }
        public ServerFilter copy() {
            ServerFilter copy = new ServerFilter();
            copy.enabled = enabled;
            copy.singlePlayer = singlePlayer;
            copy.address = address;
            return copy;
        }
    }

    public static final class Sound {
        public boolean enabled = false;
        public String id = "minecraft:block.bell.resonate";
        public int volume = 100;
        public float pitch = 1.0F;

        public void normalize() {
            id = safe(id, "minecraft:block.bell.resonate");
            volume = clamp(volume, 0, 100);
            pitch = Math.max(0.1F, Math.min(4.0F, pitch));
        }

        public Sound copy() {
            Sound copy = new Sound();
            copy.enabled = enabled;
            copy.id = id;
            copy.volume = volume;
            copy.pitch = pitch;
            return copy;
        }
    }

    public static final class Cooldown {
        public boolean enabled = false;
        public float seconds = 0.0F;
        public CooldownType type = CooldownType.GLOBAL;
        public int group = 1;

        public void normalize() {
            seconds = Math.max(0.0F, Math.min(3600.0F, seconds));
            if (type == null) type = CooldownType.GLOBAL;
            group = clamp(group, 1, 99);
        }

        public Cooldown copy() {
            Cooldown copy = new Cooldown();
            copy.enabled = enabled;
            copy.seconds = seconds;
            copy.type = type;
            copy.group = group;
            return copy;
        }
    }
}
