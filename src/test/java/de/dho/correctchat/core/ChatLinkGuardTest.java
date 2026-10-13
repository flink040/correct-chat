package de.dho.correctchat.core;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChatLinkGuardTest {
    @Test
    void detectsAllLinksAndTrimsSentencePunctuation() {
        assertEquals(List.of("https://example.org/path", "www.example.net/test"),
                ChatLinkGuard.blockedLinks(
                        "Siehe https://example.org/path, danach www.example.net/test!", List.of()));
    }

    @Test
    void exceptionMatchesOnlyTheConfiguredHostAndPath() {
        assertTrue(ChatLinkGuard.blockedLinks(
                "https://twitch.tv/eigenername/videos", List.of("twitch.tv/eigenername")).isEmpty());
        assertEquals(List.of("https://twitch.tv/fremdername"), ChatLinkGuard.blockedLinks(
                "https://twitch.tv/fremdername", List.of("twitch.tv/eigenername")));
    }

    @Test
    void emailAddressesAreNotMistakenForLinks() {
        assertTrue(ChatLinkGuard.blockedLinks("mail an name@example.org", List.of()).isEmpty());
    }
}
