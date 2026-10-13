package de.dho.correctchat.core;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MessageRuleSuggestionTest {
    @Test
    void detectsPlayerBeforeTheLastMessageSeparator() {
        MessageRuleSuggestion suggestion = MessageRuleSuggestion.from("[12:34] [Admin] Steve » Hallo 123");

        assertEquals("Steve", suggestion.player());
        assertEquals("Steve", suggestion.suggestedTabName());
        assertTrue(Pattern.compile(suggestion.playerRegex()).matcher("[Mod] Steve » Andere Nachricht").matches());
    }

    @Test
    void formatRuleKeepsContentButGeneralizesNumbersAndWhitespace() {
        MessageRuleSuggestion suggestion = MessageRuleSuggestion.from("[12:34] Alex: Du hast 42 Coins erhalten");
        Pattern rule = Pattern.compile(suggestion.formatRegex());

        assertTrue(rule.matcher("Du hast 900 Coins erhalten").find());
        assertFalse(rule.matcher("Du hast Items erhalten").find());
    }

    @Test
    void knownPlayerFallbackRespectsIdentifierBoundaries() {
        MessageRuleSuggestion suggestion = MessageRuleSuggestion.from(
                "Servermeldung für TestUser bestätigt", List.of("Test", "TestUser"));

        assertEquals("TestUser", suggestion.player());
    }
}
