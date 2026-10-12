package de.dho.correctchat.core;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/** Bounded cache that also remembers invalid expressions. */
final class RegexCache {
    private static final int MAXIMUM_ENTRIES = 512;
    private final ConcurrentHashMap<String, Optional<Pattern>> patterns = new ConcurrentHashMap<>();

    Matcher matcher(String expression, String input) {
        Optional<Pattern> pattern = patterns.computeIfAbsent(expression, RegexCache::compile);
        if (patterns.size() > MAXIMUM_ENTRIES) patterns.clear();
        return pattern.map(value -> value.matcher(input)).orElse(null);
    }

    boolean find(String expression, String input) {
        Matcher matcher = matcher(expression, input);
        return matcher != null && matcher.find();
    }

    void clear() {
        patterns.clear();
    }

    private static Optional<Pattern> compile(String expression) {
        try {
            return Optional.of(Pattern.compile(expression));
        } catch (PatternSyntaxException exception) {
            return Optional.empty();
        }
    }
}
