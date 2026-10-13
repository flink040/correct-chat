package de.dho.correctchat.config;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigNormalizerTest {
    @Test
    void migratesLegacyTabsAndAddsDmExactlyOnce() {
        CorrectChatConfig config = new CorrectChatConfig();
        config.configVersion = 7;
        config.tabs = new ArrayList<>(List.of(
                new CorrectChatConfig.ChatTab("all", "Alle", CorrectChatConfig.TabType.ALL, "")));

        config.normalize();
        config.normalize();

        assertEquals(CorrectChatConfig.CURRENT_VERSION, config.configVersion);
        assertEquals(1, config.tabs.stream().filter(tab -> "dm".equals(tab.id)).count());
        assertFalse(config.tabs.getFirst().filters.isEmpty());
    }

    @Test
    void clampsValuesNormalizesCredentialsAndRemovesDuplicates() {
        CorrectChatConfig config = new CorrectChatConfig();
        config.maxStoredMessages = -1;
        config.chatLogRetention = 5_000;
        config.primaryChatFontScalePercent = 5;
        config.uploadEndpoint = "http://chatlog-api.hdskins.de/api/chatlog";
        config.twitchChannel = " https://twitch.tv/Mein_Kanal ";
        config.twitchOauthToken = " oauth:secret ";
        config.ignoredPlayers = new ArrayList<>(List.of(" Steve ", "steve", "Alex"));
        config.blockedAdvertisements = new ArrayList<>(List.of("[12:00] Werbung", "Werbung"));

        config.normalize();

        assertEquals(1, config.maxStoredMessages);
        assertEquals(1_000, config.chatLogRetention);
        assertEquals(25, config.primaryChatFontScalePercent);
        assertEquals("https://chatlog-api.hdskins.de/api/chatlog", config.uploadEndpoint);
        assertEquals("mein_kanal", config.twitchChannel);
        assertEquals("secret", config.twitchOauthToken);
        assertEquals(List.of("Steve", "Alex"), config.ignoredPlayers);
        assertEquals(List.of("Werbung"), config.blockedAdvertisements);
    }

    @Test
    void repairsSelectionsAgainstAssignedTabs() {
        CorrectChatConfig config = new CorrectChatConfig();
        config.tabs = new ArrayList<>(List.of(
                new CorrectChatConfig.ChatTab("all", "Alle", CorrectChatConfig.TabType.ALL, ""),
                new CorrectChatConfig.ChatTab("system", "System", CorrectChatConfig.TabType.SYSTEM, "")));
        config.primaryTabIds = new ArrayList<>(List.of("system", "missing", "system"));
        config.primaryTabId = "all";

        CorrectChatConfig.ChatWindow window = new CorrectChatConfig.ChatWindow();
        window.tabIds = new ArrayList<>(List.of("all", "missing"));
        window.activeTabId = "missing";
        config.windows = new ArrayList<>(List.of(window));

        config.normalize();

        assertEquals(List.of("system"), config.primaryTabIds);
        assertEquals("system", config.primaryTabId);
        assertEquals(List.of("all"), window.tabIds);
        assertEquals("all", window.activeTabId);
        assertTrue(config.serverProfiles.isEmpty());
    }

    @Test
    void defaultsMissingLanguageAndPreservesSelectionInCopies() {
        CorrectChatConfig config = new CorrectChatConfig();
        config.uiLanguage = null;
        config.normalize();
        assertEquals(CorrectChatConfig.UiLanguage.AUTO, config.uiLanguage);

        config.uiLanguage = CorrectChatConfig.UiLanguage.EN_US;
        assertEquals(CorrectChatConfig.UiLanguage.EN_US, config.copy().uiLanguage);
    }
}
