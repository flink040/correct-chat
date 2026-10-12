package de.dho.correctchat.config;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Locale;

/** Owns schema migration and cross-field validation for the persisted config model. */
final class ConfigNormalizer {
    private ConfigNormalizer() {
    }

    static void normalize(CorrectChatConfig config) {
        boolean addDmTab = config.configVersion < 8;
        boolean migrateTabFilters = config.configVersion < 9;
        migrateSchema(config);

        config.timeStyle = safe(config.timeStyle, "&e┃ &6%time% &8» &r");
        config.timeFormat = safe(config.timeFormat, "HH:mm:ss");
        config.copyFormat = safe(config.copyFormat, "&r[&aCopy&r] ");
        config.copyTooltip = safe(config.copyTooltip, "Click to copy.");
        config.uploadEndpoint = config.uploadEndpoint == null ? "" : config.uploadEndpoint.trim();
        config.twitchChannel = normalizeTwitchName(config.twitchChannel);
        config.twitchUsername = normalizeTwitchName(config.twitchUsername);
        config.twitchOauthToken = config.twitchOauthToken == null ? "" : config.twitchOauthToken.trim();
        if (config.twitchOauthToken.regionMatches(true, 0, "oauth:", 0, 6)) {
            config.twitchOauthToken = config.twitchOauthToken.substring(6).trim();
        }
        config.maxStoredMessages = clamp(config.maxStoredMessages, 1, 10_000);
        if (config.chatLogFormat == null) config.chatLogFormat = CorrectChatConfig.ChatLogFormat.JSON;
        config.chatLogRetention = clamp(config.chatLogRetention, 1, 1_000);
        config.maxResponses = clamp(config.maxResponses, 1, 100);
        config.responseWindowMinutes = clamp(config.responseWindowMinutes, 1, 1_440);

        if (config.replacements == null) config.replacements = new ArrayList<>();
        if (config.listeners == null) config.listeners = new ArrayList<>();
        if (config.linkExceptions == null) config.linkExceptions = new ArrayList<>();
        if (config.ignoredPlayers == null) config.ignoredPlayers = new ArrayList<>();
        if (config.tabs == null) config.tabs = CorrectChatConfig.defaultTabs();
        if (config.windows == null) config.windows = CorrectChatConfig.defaultWindows();
        if (config.serverProfiles == null) config.serverProfiles = new ArrayList<>();

        config.replacements.removeIf(value -> value == null);
        config.listeners.removeIf(value -> value == null);
        config.linkExceptions.removeIf(value -> value == null || value.isBlank());
        config.linkExceptions.replaceAll(String::trim);
        config.ignoredPlayers = uniquePlayers(config.ignoredPlayers);
        config.replacements.forEach(CorrectChatConfig.TextReplacement::normalize);
        config.listeners.forEach(CorrectChatConfig.ChatListener::normalize);
        config.tabs.removeIf(value -> value == null);
        config.windows.removeIf(value -> value == null);
        config.serverProfiles.removeIf(value -> value == null);
        config.serverProfiles.forEach(CorrectChatConfig.ServerProfile::normalize);

        if (migrateTabFilters) {
            config.tabs.forEach(CorrectChatConfig.ChatTab::migrateLegacyFilters);
            config.serverProfiles.forEach(profile ->
                    profile.tabs.forEach(CorrectChatConfig.ChatTab::migrateLegacyFilters));
        }
        config.tabs.forEach(CorrectChatConfig.ChatTab::normalize);
        config.windows.forEach(CorrectChatConfig.ChatWindow::normalize);
        if (migrateTabFilters) config.serverProfiles.forEach(CorrectChatConfig.ServerProfile::normalize);
        if (addDmTab) {
            addDmTabIfMissing(config.tabs);
            config.serverProfiles.forEach(profile -> addDmTabIfMissing(profile.tabs));
        }
        ensureSelections(config);
    }

    private static void migrateSchema(CorrectChatConfig config) {
        if (config.configVersion < 2) {
            if ("&r [&aCopy&r]".equals(config.copyFormat)) config.copyFormat = "&r[&aCopy&r] ";
            config.configVersion = 2;
        }
        if (config.configVersion < 3) {
            config.chatMessagePreview = true;
            config.configVersion = 3;
        }
        if (config.configVersion < 4) config.configVersion = 4;
        if (config.configVersion < 5) {
            if (config.tabs != null && config.tabs.stream()
                    .noneMatch(tab -> tab != null && tab.type == CorrectChatConfig.TabType.TWITCH)) {
                config.tabs.add(new CorrectChatConfig.ChatTab(
                        "twitch", "Twitch", CorrectChatConfig.TabType.TWITCH, ""));
            }
            config.configVersion = 5;
        }
        if (config.configVersion < 6) config.configVersion = 6;
        if (config.configVersion < 7) config.configVersion = 7;
        if (config.configVersion < 8) config.configVersion = 8;
        if (config.configVersion < 9) config.configVersion = 9;
        if (config.configVersion < 10) config.configVersion = 10;
    }

    private static ArrayList<String> uniquePlayers(Iterable<String> players) {
        LinkedHashMap<String, String> unique = new LinkedHashMap<>();
        for (String player : players) {
            if (player == null || player.isBlank()) continue;
            String trimmed = player.trim();
            unique.putIfAbsent(trimmed.toLowerCase(Locale.ROOT), trimmed);
        }
        return new ArrayList<>(unique.values());
    }

    private static void addDmTabIfMissing(java.util.List<CorrectChatConfig.ChatTab> tabs) {
        if (tabs.stream().noneMatch(tab -> "dm".equals(tab.id))) {
            tabs.add(new CorrectChatConfig.ChatTab(
                    "dm", "DM", CorrectChatConfig.TabType.REGEX, CorrectChatConfig.DM_FILTER));
        }
    }

    private static void ensureSelections(CorrectChatConfig config) {
        if (config.tabs.isEmpty()) config.tabs.addAll(CorrectChatConfig.defaultTabs());
        if (config.tabs.stream().noneMatch(tab -> tab.id.equals(config.primaryTabId))) {
            config.primaryTabId = config.tabs.getFirst().id;
        }
        for (CorrectChatConfig.ChatWindow window : config.windows) {
            if (config.tabs.stream().noneMatch(tab -> tab.id.equals(window.activeTabId))) {
                window.activeTabId = config.tabs.getFirst().id;
            }
        }
    }

    private static String safe(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static String normalizeTwitchName(String value) {
        if (value == null) return "";
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        int slash = normalized.lastIndexOf('/');
        if (slash >= 0) normalized = normalized.substring(slash + 1);
        if (normalized.startsWith("#")) normalized = normalized.substring(1);
        return normalized.replaceAll("[^a-z0-9_]", "");
    }
}
