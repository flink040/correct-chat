package de.dho.correctchat.core;

import java.util.Optional;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/** Bounded cache that also remembers invalid expressions. */
final class RegexCache {
    private static final int MAXIMUM_ENTRIES = 512;
    private final Map<String, Optional<Pattern>> patterns = Collections.synchronizedMap(
            new LinkedHashMap<>(64, 0.75F, true) {
                @Override protected boolean removeEldestEntry(Map.Entry<String, Optional<Pattern>> eldest) {
                    return size() > MAXIMUM_ENTRIES;
                }
            });

    Matcher matcher(String expression, String input) {
        Optional<Pattern> pattern;
        synchronized (patterns) {
            pattern = patterns.computeIfAbsent(expression, RegexCache::compile);
        }
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
