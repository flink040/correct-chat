package de.dho.correctchat.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class CorrectChatConfig {
    public static final int CURRENT_VERSION = 19;
    static final String DM_FILTER = "(?i)\\[(?:du\\s*->\\s*[^\\]]+|[^\\]]+\\s*->\\s*mir)\\]";

    public int configVersion = CURRENT_VERSION;
    public boolean enabled = true;
    public UiLanguage uiLanguage = UiLanguage.AUTO;

    public boolean antiCommandChoker = true;
    public boolean chatTime = true;
    public String timeStyle = "&e┃ &6%time% &8» &r";
    public String timeFormat = "HH:mm:ss";

    public boolean chatLog = true;
    public int maxStoredMessages = 1000;
    public ChatLogFormat chatLogFormat = ChatLogFormat.JSON;
    public int chatLogRetention = 50;
    public boolean uploadEnabled = false;
    public String uploadEndpoint = "https://chatlog-api.hdskins.de/api/chatlog";

    public boolean textReplacement = true;
    public boolean chatListeners = true;
    public boolean copyButton = false;
    public boolean chatMessagePreview = true;
    public ChatColor defaultChatColor = ChatColor.NONE;
    public String copyFormat = "&r[&aCopy&r] ";
    public String copyTooltip = "Click to copy.";
    public boolean antiSpam = false;
    public int maxResponses = 2;
    public int responseWindowMinutes = 5;
    public boolean linkFilter = false;
    public List<String> linkExceptions = new ArrayList<>();
    public List<String> ignoredPlayers = new ArrayList<>();
    /** Exact rendered messages hidden globally after being marked as advertising. */
    public List<String> blockedAdvertisements = new ArrayList<>();

    public boolean chatTabsEnabled = true;
    public boolean unreadBadges = true;
    public boolean mentionHighlight = true;
    /** Scaled GUI dimensions; zero keeps Minecraft's current vanilla chat size. */
    public int primaryChatWidth = 0;
    public int primaryChatHeight = 0;
    /** Percentage from 25 to 200; zero keeps Minecraft's current chat scale. */
    public int primaryChatFontScalePercent = 0;
    public String primaryTabId = "all";
    /** Empty keeps the backwards-compatible behavior of showing every tab in the main chat. */
    public List<String> primaryTabIds = new ArrayList<>();
    public List<ChatTab> tabs = defaultTabs();
    public List<ChatWindow> windows = defaultWindows();
    public List<ServerProfile> serverProfiles = new ArrayList<>();

    public boolean twitchEnabled = false;
    public String twitchChannel = "";
    public String twitchUsername = "";
    public String twitchOauthToken = "";

    public List<TextReplacement> replacements = new ArrayList<>();
    public List<ChatListener> listeners = new ArrayList<>();
    /** Local visual/audio alarms created from chat messages. */
    public List<ChatAlert> alerts = new ArrayList<>();

    public void normalize() {
        ConfigNormalizer.normalize(this);
    }

    public CorrectChatConfig copy() {
        CorrectChatConfig copy = new CorrectChatConfig();
        copy.configVersion = configVersion;
        copy.enabled = enabled;
        copy.uiLanguage = uiLanguage;
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
        copy.defaultChatColor = defaultChatColor;
        copy.copyFormat = copyFormat;
        copy.copyTooltip = copyTooltip;
        copy.antiSpam = antiSpam;
        copy.maxResponses = maxResponses;
        copy.responseWindowMinutes = responseWindowMinutes;
        copy.linkFilter = linkFilter;
        copy.linkExceptions = new ArrayList<>(linkExceptions);
        copy.ignoredPlayers = new ArrayList<>(ignoredPlayers);
        copy.blockedAdvertisements = new ArrayList<>(blockedAdvertisements);
        copy.chatTabsEnabled = chatTabsEnabled;
        copy.unreadBadges = unreadBadges;
        copy.mentionHighlight = mentionHighlight;
        copy.primaryChatWidth = primaryChatWidth;
        copy.primaryChatHeight = primaryChatHeight;
        copy.primaryChatFontScalePercent = primaryChatFontScalePercent;
        copy.primaryTabId = primaryTabId;
        copy.primaryTabIds = new ArrayList<>(primaryTabIds);
        replacements.forEach(value -> copy.replacements.add(value.copy()));
        listeners.forEach(value -> copy.listeners.add(value.copy()));
        alerts.forEach(value -> copy.alerts.add(value.copy()));
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

    public List<String> primaryTabIdsFor(String serverKey) {
        ServerProfile profile = profileFor(serverKey);
        return profile == null ? primaryTabIds : profile.primaryTabIds;
    }

    public void setPrimaryTabFor(String serverKey, String tabId) {
        ServerProfile profile = profileFor(serverKey);
        if (profile == null) primaryTabId = tabId;
        else profile.primaryTabId = tabId;
    }

    static List<ChatTab> defaultTabs() {
        List<ChatTab> result = new ArrayList<>();
        result.add(new ChatTab("all", "Chat", TabType.ALL, ""));
        result.add(new ChatTab("dm", "DM", TabType.REGEX, DM_FILTER));
        result.add(new ChatTab("twitch", "Twitch", TabType.TWITCH, ""));
        return result;
    }

    static List<ChatWindow> defaultWindows() {
        return new ArrayList<>();
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
        public boolean caseSensitive = false;
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
            copy.caseSensitive = caseSensitive;
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

    public static final class ChatAlert {
        public boolean enabled = true;
        public String name = "Alarm";
        public MatchType matchType = MatchType.EQUALS;
        public boolean caseSensitive = false;
        public String match = "";
        /** Supports %message% for the complete matching message. */
        public String text = "%message%";
        public AlertDisplay display = AlertDisplay.TITLE;
        public ChatColor color = ChatColor.RED;
        public int fontScalePercent = 150;
        public float durationSeconds = 5.0F;
        public float cooldownSeconds = 0.0F;
        public ServerFilter server = new ServerFilter();
        public Sound sound = new Sound();

        public void normalize() {
            name = safe(name, "Alarm");
            match = match == null ? "" : match;
            text = safe(text, "%message%");
            if (matchType == null) matchType = MatchType.EQUALS;
            if (display == null) display = AlertDisplay.TITLE;
            if (color == null || color == ChatColor.NONE) color = ChatColor.RED;
            fontScalePercent = clamp(fontScalePercent, 50, 300);
            durationSeconds = Math.max(0.5F, Math.min(60.0F, durationSeconds));
            cooldownSeconds = Math.max(0.0F, Math.min(3600.0F, cooldownSeconds));
            if (server == null) server = new ServerFilter();
            if (sound == null) sound = new Sound();
            server.normalize();
            sound.normalize();
        }

        public ChatAlert copy() {
            ChatAlert copy = new ChatAlert();
            copy.enabled = enabled;
            copy.name = name;
            copy.matchType = matchType;
            copy.caseSensitive = caseSensitive;
            copy.match = match;
            copy.text = text;
            copy.display = display;
            copy.color = color;
            copy.fontScalePercent = fontScalePercent;
            copy.durationSeconds = durationSeconds;
            copy.cooldownSeconds = cooldownSeconds;
            copy.server = server.copy();
            copy.sound = sound.copy();
            return copy;
        }
    }

    public enum MatchType { EQUALS, CONTAINS, REGEX, SIMPLIFIED_REGEX }
    public enum AlertDisplay { TITLE, BOSSBAR, ACTIONBAR }
    public enum CooldownType { GLOBAL, PARTIAL }
    public enum TabType { ALL, PLAYER, SYSTEM, TWITCH, CONTAINS, EQUALS, REGEX, MOD }
    public enum FilterRule { OR, AND }
    public enum ChatLogFormat { JSON, TEXT }
    public enum UiLanguage { AUTO, DE_DE, EN_US }

    public enum ChatColor {
        NONE("", "Keine", null),
        BLACK("&0", "Schwarz", net.minecraft.ChatFormatting.BLACK),
        DARK_BLUE("&1", "Dunkel Blau", net.minecraft.ChatFormatting.DARK_BLUE),
        DARK_GREEN("&2", "Dunkel Grün", net.minecraft.ChatFormatting.DARK_GREEN),
        DARK_AQUA("&3", "Dunkel Aqua", net.minecraft.ChatFormatting.DARK_AQUA),
        DARK_RED("&4", "Dunkel Rot", net.minecraft.ChatFormatting.DARK_RED),
        DARK_PURPLE("&5", "Dunkel Lila", net.minecraft.ChatFormatting.DARK_PURPLE),
        GOLD("&6", "Gold", net.minecraft.ChatFormatting.GOLD),
        GRAY("&7", "Grau", net.minecraft.ChatFormatting.GRAY),
        DARK_GRAY("&8", "Dunkel Grau", net.minecraft.ChatFormatting.DARK_GRAY),
        BLUE("&9", "Blau", net.minecraft.ChatFormatting.BLUE),
        GREEN("&a", "Grün", net.minecraft.ChatFormatting.GREEN),
        AQUA("&b", "Aqua", net.minecraft.ChatFormatting.AQUA),
        RED("&c", "Rot", net.minecraft.ChatFormatting.RED),
        LIGHT_PURPLE("&d", "Hell Lila", net.minecraft.ChatFormatting.LIGHT_PURPLE),
        YELLOW("&e", "Gelb", net.minecraft.ChatFormatting.YELLOW),
        WHITE("&f", "Weiß", net.minecraft.ChatFormatting.WHITE);

        public final String code;
        public final String label;
        public final net.minecraft.ChatFormatting formatting;

        ChatColor(String code, String label, net.minecraft.ChatFormatting formatting) {
            this.code = code;
            this.label = label;
            this.formatting = formatting;
        }
    }

    public static final class ChatTab {
        public String id = java.util.UUID.randomUUID().toString();
        public String name = "Neuer Tab";
        /** Kept for backwards-compatible config imports. New code uses filters. */
        public TabType type = TabType.CONTAINS;
        /** Kept for backwards-compatible config imports. New code uses filters. */
        public String filter = "";
        public List<TabFilter> filters = new ArrayList<>();
        public FilterRule filterRule = FilterRule.OR;
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
            if (filterRule == null) filterRule = FilterRule.OR;
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
            copy.filterRule = filterRule;
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
        /** Empty keeps the backwards-compatible behavior of showing every tab in this window. */
        public List<String> tabIds = new ArrayList<>();

        public void normalize() {
            name = safe(name, "Chatfenster");
            width = clamp(width, 100, 600);
            height = clamp(height, 40, 400);
            activeTabId = safe(activeTabId, "all");
            if (tabIds == null) tabIds = new ArrayList<>();
            tabIds.removeIf(value -> value == null || value.isBlank());
        }
        public ChatWindow copy() {
            ChatWindow copy = new ChatWindow();
            copy.name = name; copy.visible = visible; copy.x = x; copy.y = y; copy.width = width; copy.height = height; copy.activeTabId = activeTabId;
            copy.tabIds = new ArrayList<>(tabIds);
            return copy;
        }
    }

    public static final class ServerProfile {
        public String name = "Serverprofil";
        public String server = "";
        public boolean enabled = true;
        public String primaryTabId = "all";
        public List<String> primaryTabIds = new ArrayList<>();
        public List<ChatTab> tabs = defaultTabs();
        public List<ChatWindow> windows = defaultWindows();

        public void normalize() {
            name = safe(name, "Serverprofil");
            server = server == null ? "" : server.trim();
            if (tabs == null) tabs = defaultTabs();
            if (windows == null) windows = defaultWindows();
            if (primaryTabIds == null) primaryTabIds = new ArrayList<>();
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
            copy.primaryTabIds = new ArrayList<>(primaryTabIds);
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
        /** Optional local MP3, WAV or OGG file. When set, it takes precedence over id. */
        public String file = "";
        public int volume = 100;
        public float pitch = 1.0F;

        public void normalize() {
            id = safe(id, "minecraft:block.bell.resonate");
            file = file == null ? "" : file.trim();
            volume = clamp(volume, 0, 100);
            pitch = Math.max(0.1F, Math.min(4.0F, pitch));
        }

        public Sound copy() {
            Sound copy = new Sound();
            copy.enabled = enabled;
            copy.id = id;
            copy.file = file;
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
