package de.dho.correctchat.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RegexCacheTest {
    @Test
    void cachesValidAndInvalidExpressionsWithoutThrowing() {
        RegexCache cache = new RegexCache();

        assertTrue(cache.find("h.llo", "well, hello"));
        assertFalse(cache.find("[invalid", "anything"));
        assertFalse(cache.find("[invalid", "anything else"));
    }

    @Test
    void clearAllowsTheCacheToBeReused() {
        RegexCache cache = new RegexCache();
        assertTrue(cache.find("eins", "eins"));
        cache.clear();
        assertTrue(cache.find("zwei", "zwei"));
    }
}
