package de.dho.correctchat.config;

import de.dho.correctchat.core.AdvertisementFilter;

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
        if (config.defaultChatColor == null) config.defaultChatColor = CorrectChatConfig.ChatColor.NONE;
        config.uploadEndpoint = config.uploadEndpoint == null ? "" : config.uploadEndpoint.trim();
        if (config.uploadEndpoint.equalsIgnoreCase("http://chatlog-api.hdskins.de/api/chatlog")) {
            config.uploadEndpoint = "https://chatlog-api.hdskins.de/api/chatlog";
        }
        config.twitchChannel = normalizeTwitchName(config.twitchChannel);
        config.twitchUsername = normalizeTwitchName(config.twitchUsername);
        config.twitchOauthToken = config.twitchOauthToken == null ? "" : config.twitchOauthToken.trim();
        if (config.twitchOauthToken.regionMatches(true, 0, "oauth:", 0, 6)) {
            config.twitchOauthToken = config.twitchOauthToken.substring(6).trim();
        }
        config.maxStoredMessages = clamp(config.maxStoredMessages, 1, 10_000);
        if (config.chatLogFormat == null) config.chatLogFormat = CorrectChatConfig.ChatLogFormat.JSON;
        if (config.uiLanguage == null) config.uiLanguage = CorrectChatConfig.UiLanguage.AUTO;
        config.chatLogRetention = clamp(config.chatLogRetention, 1, 1_000);
        config.maxResponses = clamp(config.maxResponses, 1, 100);
        config.responseWindowMinutes = clamp(config.responseWindowMinutes, 1, 1_440);
        if (config.primaryChatWidth != 0) config.primaryChatWidth = clamp(config.primaryChatWidth, 40, 8_192);
        if (config.primaryChatHeight != 0) config.primaryChatHeight = clamp(config.primaryChatHeight, 20, 8_192);
        if (config.primaryChatFontScalePercent != 0) {
            config.primaryChatFontScalePercent = clamp(config.primaryChatFontScalePercent, 25, 200);
        }

        if (config.replacements == null) config.replacements = new ArrayList<>();
        if (config.listeners == null) config.listeners = new ArrayList<>();
        if (config.alerts == null) config.alerts = new ArrayList<>();
        if (config.linkExceptions == null) config.linkExceptions = new ArrayList<>();
        if (config.ignoredPlayers == null) config.ignoredPlayers = new ArrayList<>();
        if (config.blockedAdvertisements == null) config.blockedAdvertisements = new ArrayList<>();
        if (config.primaryTabIds == null) config.primaryTabIds = new ArrayList<>();
        if (config.tabs == null) config.tabs = CorrectChatConfig.defaultTabs();
        if (config.windows == null) config.windows = CorrectChatConfig.defaultWindows();
        if (config.serverProfiles == null) config.serverProfiles = new ArrayList<>();

        config.replacements.removeIf(value -> value == null);
        config.listeners.removeIf(value -> value == null);
        config.alerts.removeIf(value -> value == null);
        config.linkExceptions.removeIf(value -> value == null || value.isBlank());
        config.linkExceptions.replaceAll(String::trim);
        config.ignoredPlayers = uniquePlayers(config.ignoredPlayers);
        config.blockedAdvertisements = uniqueMessages(config.blockedAdvertisements);
        config.replacements.forEach(CorrectChatConfig.TextReplacement::normalize);
        config.listeners.forEach(CorrectChatConfig.ChatListener::normalize);
        config.alerts.forEach(CorrectChatConfig.ChatAlert::normalize);
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
        if (config.configVersion < 11) config.configVersion = 11;
        if (config.configVersion < 12) config.configVersion = 12;
        if (config.configVersion < 13) config.configVersion = 13;
        if (config.configVersion < 14) config.configVersion = 14;
        if (config.configVersion < 15) config.configVersion = 15;
        if (config.configVersion < 16) config.configVersion = 16;
        if (config.configVersion < 17) config.configVersion = 17;
        if (config.configVersion < 18) config.configVersion = 18;
        if (config.configVersion < 19) config.configVersion = 19;
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

    private static ArrayList<String> uniqueMessages(Iterable<String> messages) {
        java.util.LinkedHashSet<String> unique = new java.util.LinkedHashSet<>();
        for (String message : messages) {
            String key = AdvertisementFilter.key(message);
            if (!key.isBlank()) unique.add(key);
        }
        return new ArrayList<>(unique);
    }

    private static void addDmTabIfMissing(java.util.List<CorrectChatConfig.ChatTab> tabs) {
        if (tabs.stream().noneMatch(tab -> "dm".equals(tab.id))) {
            tabs.add(new CorrectChatConfig.ChatTab(
                    "dm", "DM", CorrectChatConfig.TabType.REGEX, CorrectChatConfig.DM_FILTER));
        }
    }

    private static void ensureSelections(CorrectChatConfig config) {
        if (config.tabs.isEmpty()) config.tabs.addAll(CorrectChatConfig.defaultTabs());
        normalizeArea(config.primaryTabIds, config.tabs);
        config.primaryTabId = selectedOrFirst(config.primaryTabId, config.primaryTabIds, config.tabs);
        for (CorrectChatConfig.ChatWindow window : config.windows) normalizeWindow(window, config.tabs);
        for (CorrectChatConfig.ServerProfile profile : config.serverProfiles) {
            normalizeArea(profile.primaryTabIds, profile.tabs);
            profile.primaryTabId = selectedOrFirst(profile.primaryTabId, profile.primaryTabIds, profile.tabs);
            for (CorrectChatConfig.ChatWindow window : profile.windows) normalizeWindow(window, profile.tabs);
        }
    }

    private static void normalizeWindow(CorrectChatConfig.ChatWindow window,
                                        java.util.List<CorrectChatConfig.ChatTab> tabs) {
        normalizeArea(window.tabIds, tabs);
        window.activeTabId = selectedOrFirst(window.activeTabId, window.tabIds, tabs);
    }

    private static void normalizeArea(java.util.List<String> areaIds,
                                      java.util.List<CorrectChatConfig.ChatTab> tabs) {
        java.util.LinkedHashSet<String> valid = new java.util.LinkedHashSet<>();
        for (String id : areaIds) {
            if (id != null && tabs.stream().anyMatch(tab -> tab.id.equals(id))) valid.add(id);
        }
        areaIds.clear();
        areaIds.addAll(valid);
    }

    private static String selectedOrFirst(String selected, java.util.List<String> areaIds,
                                          java.util.List<CorrectChatConfig.ChatTab> tabs) {
        java.util.List<String> allowed = areaIds.isEmpty()
                ? tabs.stream().map(tab -> tab.id).toList()
                : areaIds;
        if (selected != null && allowed.contains(selected)) return selected;
        return allowed.isEmpty() ? tabs.getFirst().id : allowed.getFirst();
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
