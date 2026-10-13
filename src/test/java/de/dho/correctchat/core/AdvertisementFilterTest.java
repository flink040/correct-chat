package de.dho.correctchat.core;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdvertisementFilterTest {
    @Test
    void keyRemovesDisplayOnlyPrefixesAndFormatting() {
        assertEquals("Kaufe jetzt im Shop", AdvertisementFilter.key("§e┃ §612:34:56 §8» §r[C]  Kaufe   jetzt im Shop"));
        assertEquals("Kaufe jetzt im Shop", AdvertisementFilter.key("[Copy] §e┃ §612:34:56 §8» §rKaufe jetzt im Shop"));
    }

    @Test
    void exactAndWildcardRulesUseTheNormalizedMessage() {
        AdvertisementFilter.RuleSet rules = AdvertisementFilter.compile(List.of(
                "Kaufe jetzt im Shop",
                "Vote jetzt auf * und erhalte * Coins"));

        assertTrue(rules.blocks("[12:00] Kaufe jetzt im Shop"));
        assertTrue(rules.blocks("Vote jetzt auf example.net und erhalte 50 Coins"));
        assertFalse(rules.blocks("Spieler: Hallo zusammen"));
    }

    @Test
    void emptyRulesNeverBlock() {
        assertFalse(AdvertisementFilter.isBlocked(List.of(), "beliebiger Text"));
        assertFalse(AdvertisementFilter.compile(List.of(" ", "§a")).blocks("Text"));
    }
}
