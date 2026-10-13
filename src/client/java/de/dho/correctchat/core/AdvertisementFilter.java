package de.dho.correctchat.core;

import java.util.List;
import java.util.Set;
import java.util.HashSet;
import java.util.ArrayList;
import java.util.regex.Pattern;

/** Produces stable keys for messages whose display prefix changes over time. */
public final class AdvertisementFilter {
    private static final Pattern LEGACY_FORMATTING = Pattern.compile("(?i)§[0-9A-FK-ORX]");
    private static final Pattern COPY_PREFIX = Pattern.compile("(?i)^\\s*\\[(?:c|copy)]\\s*");
    private static final Pattern TIMESTAMP_PREFIX = Pattern.compile(
            "(?iu)^\\s*(?:[┃|]\\s*)?(?:\\[|\\()?\\d{1,2}:\\d{2}(?::\\d{2})?(?:\\]|\\))?\\s*(?:[»›→|:-]\\s*)?");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private AdvertisementFilter() {
    }

    /** Pre-normalized rules for the per-message hot path. */
    static final class RuleSet {
        private static final RuleSet EMPTY = new RuleSet(Set.of(), List.of());
        private final Set<String> exact;
        private final List<Pattern> wildcards;

        private RuleSet(Set<String> exact, List<Pattern> wildcards) {
            this.exact = exact;
            this.wildcards = wildcards;
        }

        boolean blocks(String message) {
            if (exact.isEmpty() && wildcards.isEmpty()) return false;
            String candidate = key(message);
            if (candidate.isEmpty()) return false;
            if (exact.contains(candidate)) return true;
            for (Pattern wildcard : wildcards) {
                if (wildcard.matcher(candidate).matches()) return true;
            }
            return false;
        }
    }

    public static String key(String message) {
        if (message == null) return "";
        String normalized = LEGACY_FORMATTING.matcher(message).replaceAll("");
        // Both display features are configurable and older configs may prepend them in either order.
        for (int pass = 0; pass < 2; pass++) {
            String before = normalized;
            normalized = COPY_PREFIX.matcher(normalized).replaceFirst("");
            normalized = TIMESTAMP_PREFIX.matcher(normalized).replaceFirst("");
            if (normalized.equals(before)) break;
        }
        return WHITESPACE.matcher(normalized).replaceAll(" ").strip();
    }

    public static boolean isBlocked(List<String> blockedAdvertisements, String message) {
        return compile(blockedAdvertisements).blocks(message);
    }

    static RuleSet compile(List<String> blockedAdvertisements) {
        if (blockedAdvertisements == null || blockedAdvertisements.isEmpty()) return RuleSet.EMPTY;
        Set<String> exact = new HashSet<>();
        List<Pattern> wildcards = new ArrayList<>();
        for (String blocked : blockedAdvertisements) {
            String rule = key(blocked);
            if (rule.isEmpty()) continue;
            if (rule.indexOf('*') >= 0) wildcards.add(wildcard(rule));
            else exact.add(rule);
        }
        if (exact.isEmpty() && wildcards.isEmpty()) return RuleSet.EMPTY;
        return new RuleSet(Set.copyOf(exact), List.copyOf(wildcards));
    }

    private static Pattern wildcard(String rule) {
        StringBuilder expression = new StringBuilder("^");
        int start = 0;
        for (int index = rule.indexOf('*'); index >= 0; index = rule.indexOf('*', start)) {
            expression.append(Pattern.quote(rule.substring(start, index))).append(".*");
            start = index + 1;
        }
        expression.append(Pattern.quote(rule.substring(start))).append('$');
        return Pattern.compile(expression.toString());
    }
}
