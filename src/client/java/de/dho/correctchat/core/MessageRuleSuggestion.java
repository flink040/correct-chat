package de.dho.correctchat.core;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Builds editable, conservative rule proposals from one observed chat message. */
public record MessageRuleSuggestion(String message, String player, String playerRegex, String formatRegex) {
    private static final Pattern PLAYER_BEFORE_LAST_SEPARATOR = Pattern.compile(
            "([~+@]?[A-Za-z0-9_]{1,17})\\s*(?=[>»›:→]\\s+[^>»›:→]*$)");
    private static final Pattern BODY_SEPARATOR = Pattern.compile("[>»›:→]\\s+");
    private static final Pattern CLOCK = Pattern.compile(
            "(?:\\[\\s*)?(?<!\\d)\\d{1,2}:\\d{2}(?::\\d{2})?(?!\\d)(?:\\s*])?");
    private static final Pattern TOKEN = Pattern.compile("\\d{1,2}:\\d{2}(?::\\d{2})?|\\d+|\\s+");

    public static MessageRuleSuggestion from(String rawMessage) {
        return from(rawMessage, java.util.List.of());
    }

    public static MessageRuleSuggestion from(String rawMessage, Iterable<String> knownPlayers) {
        String message = rawMessage == null ? "" : rawMessage.strip();
        String player = detectPlayer(message);
        if (player.isEmpty()) player = detectKnownPlayer(message, knownPlayers);
        String playerRule = player.isEmpty()
                ? Pattern.quote(message)
                : "(?i)^.*(?:<|\\||»|›|\\s)[~+@]?" + Pattern.quote(player)
                    + "\\s*(?:>|»|›|:|→)\\s+.*$";
        return new MessageRuleSuggestion(message, player, playerRule, buildFormatRegex(message, player));
    }

    public static String detectPlayer(String message) {
        if (message == null || message.isBlank()) return "";
        Matcher matcher = PLAYER_BEFORE_LAST_SEPARATOR.matcher(message);
        String result = "";
        while (matcher.find()) result = matcher.group(1);
        while (!result.isEmpty() && "~+@".indexOf(result.charAt(0)) >= 0) result = result.substring(1);
        return result.matches("[A-Za-z0-9_]{1,16}") ? result : "";
    }

    private static String detectKnownPlayer(String message, Iterable<String> knownPlayers) {
        String best = "";
        for (String candidate : knownPlayers) {
            if (candidate == null || !candidate.matches("[A-Za-z0-9_]{1,16}")) continue;
            if (candidate.length() <= best.length()) continue;
            Pattern bounded = Pattern.compile("(?i)(?<![A-Za-z0-9_])" + Pattern.quote(candidate) + "(?![A-Za-z0-9_])");
            if (bounded.matcher(message).find()) best = candidate;
        }
        return best;
    }

    public String suggestedTabName() {
        return player.isEmpty() ? "Nachrichtenart" : player;
    }

    public String suggestedRuleName() {
        return player.isEmpty() ? "Chatregel" : "Nachrichten von " + player;
    }

    private static String buildFormatRegex(String message, String player) {
        Matcher separatorMatcher = BODY_SEPARATOR.matcher(message);
        int contentStart = -1;
        while (separatorMatcher.find()) {
            contentStart = separatorMatcher.end();
        }
        String content;
        if (contentStart >= 0 && contentStart < message.length()) {
            content = message.substring(contentStart);
        } else {
            content = contentWithoutVolatilePrefix(message, player);
        }
        return buildContentRegex(content);
    }

    /** Builds a find-expression from message content only; chat prefix, clock and player are absent. */
    private static String buildContentRegex(String content) {
        String normalized = longestSegmentWithoutClock(content);
        if (normalized.isEmpty()) return "(?!)";
        StringBuilder result = new StringBuilder("(?i)");
        Matcher tokenMatcher = TOKEN.matcher(normalized);
        int cursor = 0;
        while (tokenMatcher.find()) {
            appendLiteral(result, normalized.substring(cursor, tokenMatcher.start()));
            String token = tokenMatcher.group();
            if (token.isBlank()) result.append("\\s+");
            else result.append("\\d+");
            cursor = tokenMatcher.end();
        }
        appendLiteral(result, normalized.substring(cursor));
        return result.toString();
    }

    private static String contentWithoutVolatilePrefix(String message, String player) {
        String withoutClock = message;
        if (!player.isEmpty()) {
            Matcher playerMatcher = Pattern.compile("(?i)(?<![A-Za-z0-9_])[~+@]?" + Pattern.quote(player)
                    + "(?![A-Za-z0-9_])").matcher(withoutClock);
            if (playerMatcher.find()) {
                String beforePlayer = withoutClock.substring(0, playerMatcher.start()).strip();
                String afterPlayer = withoutClock.substring(playerMatcher.end())
                        .replaceFirst("^\\s*(?:[>»›:→|.-]+\\s*)?", "").strip();
                return afterPlayer.length() >= beforePlayer.length() ? afterPlayer : beforePlayer;
            }
        }
        return withoutClock.strip();
    }

    private static String longestSegmentWithoutClock(String content) {
        Matcher matcher = CLOCK.matcher(content);
        String longest = "";
        int cursor = 0;
        while (matcher.find()) {
            String segment = content.substring(cursor, matcher.start()).strip();
            if (segment.length() > longest.length()) longest = segment;
            cursor = matcher.end();
        }
        String tail = content.substring(cursor).strip();
        if (tail.length() > longest.length()) longest = tail;
        return longest;
    }

    private static void appendLiteral(StringBuilder result, String text) {
        if (!text.isEmpty()) result.append(Pattern.quote(text));
    }
}
