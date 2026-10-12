package de.dho.correctchat.core;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Builds editable, conservative rule proposals from one observed chat message. */
public record MessageRuleSuggestion(String message, String player, String playerRegex, String formatRegex) {
    private static final Pattern PLAYER_BEFORE_LAST_SEPARATOR = Pattern.compile(
            "([~+@]?[A-Za-z0-9_]{1,17})\\s*(?=[>»›:→]\\s+[^>»›:→]*$)");
    private static final Pattern BODY_SEPARATOR = Pattern.compile("\\s([»›:→])\\s+");
    private static final Pattern TOKEN = Pattern.compile("\\d{1,2}:\\d{2}(?::\\d{2})?|\\d+|\\s+");

    public static MessageRuleSuggestion from(String rawMessage) {
        String message = rawMessage == null ? "" : rawMessage.strip();
        String player = detectPlayer(message);
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

    public String suggestedTabName() {
        return player.isEmpty() ? "Nachrichtenart" : player;
    }

    public String suggestedRuleName() {
        return player.isEmpty() ? "Chatregel" : "Nachrichten von " + player;
    }

    private static String buildFormatRegex(String message, String player) {
        Matcher separatorMatcher = BODY_SEPARATOR.matcher(message);
        int split = -1;
        String separator = "»";
        while (separatorMatcher.find()) {
            split = separatorMatcher.start(1);
            separator = separatorMatcher.group(1);
        }
        if (split < 0) return Pattern.quote(message);
        String prefix = message.substring(0, split);
        StringBuilder result = new StringBuilder("(?i)^\\s*");
        Matcher tokenMatcher = TOKEN.matcher(prefix);
        int cursor = 0;
        while (tokenMatcher.find()) {
            appendLiteralOrPlayer(result, prefix.substring(cursor, tokenMatcher.start()), player);
            String token = tokenMatcher.group();
            if (token.isBlank()) result.append("\\s+");
            else if (token.indexOf(':') >= 0) result.append("\\d{1,2}:\\d{2}(?::\\d{2})?");
            else result.append("\\d+");
            cursor = tokenMatcher.end();
        }
        appendLiteralOrPlayer(result, prefix.substring(cursor), player);
        result.append("\\s*").append(Pattern.quote(separator)).append("\\s+.*$");
        return result.toString();
    }

    private static void appendLiteralOrPlayer(StringBuilder result, String text, String player) {
        if (text.isEmpty()) return;
        if (player.isEmpty()) {
            result.append(Pattern.quote(text));
            return;
        }
        Pattern playerPattern = Pattern.compile("(?i)[~+@]?" + Pattern.quote(player));
        Matcher matcher = playerPattern.matcher(text);
        int cursor = 0;
        while (matcher.find()) {
            if (matcher.start() > cursor) result.append(Pattern.quote(text.substring(cursor, matcher.start())));
            result.append("[~+@]?[A-Za-z0-9_]{1,16}");
            cursor = matcher.end();
        }
        if (cursor < text.length()) result.append(Pattern.quote(text.substring(cursor)));
    }
}
