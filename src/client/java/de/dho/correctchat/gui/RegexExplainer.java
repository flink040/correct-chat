package de.dho.correctchat.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/** Produces a short, best-effort explanation for regex fields in the UI. */
final class RegexExplainer {
    private static final int MAX_PARTS = 7;

    private RegexExplainer() { }

    static String explain(String expression) {
        String regex = expression == null ? "" : expression.trim();
        if (regex.isEmpty()) return "Bedeutung: Noch kein Suchmuster eingegeben.";
        try {
            Pattern.compile(regex);
        } catch (PatternSyntaxException exception) {
            return "Ungültige Regex: " + exception.getDescription();
        }

        boolean ignoreCase = regex.contains("(?i") || regex.contains("(?iu") || regex.contains("(?ui");
        boolean startsAtBeginning = hasUnescapedAnchor(regex, '^');
        boolean endsAtEnd = hasUnescapedAnchor(regex, '$');
        List<String> parts = describeParts(regex);

        StringBuilder result = new StringBuilder("Bedeutung: ");
        if (startsAtBeginning && endsAtEnd) result.append("gesamte Nachricht: ");
        else if (startsAtBeginning) result.append("ab Nachrichtenanfang: ");
        else result.append("sucht irgendwo nach: ");
        result.append(parts.isEmpty() ? "diesem Muster" : String.join(" → ", parts));
        if (ignoreCase) result.append(" · Groß/Klein egal");
        return result.toString();
    }

    static String explainSimplified(String expression) {
        String value = expression == null ? "" : expression.trim();
        if (value.isEmpty()) return "Bedeutung: Noch kein Suchmuster eingegeben.";
        String display = value
                .replace("&player", "[eigener Name]")
                .replace("%", "[gemerkter Text]")
                .replace("*", "[beliebiger Text]");
        return "Bedeutung: sucht irgendwo nach: " + display;
    }

    private static List<String> describeParts(String regex) {
        List<String> parts = new ArrayList<>();
        StringBuilder literal = new StringBuilder();
        boolean shortened = false;
        for (int i = 0; i < regex.length() && parts.size() < MAX_PARTS; i++) {
            char current = regex.charAt(i);
            if (current == '\\' && i + 1 < regex.length()) {
                char escaped = regex.charAt(++i);
                if (escaped == 'Q') {
                    int end = regex.indexOf("\\E", i + 1);
                    if (end < 0) end = regex.length();
                    appendLiteral(parts, literal);
                    addPart(parts, quote(regex.substring(i + 1, end)));
                    i = Math.min(regex.length() - 1, end + 1);
                } else if (escaped == 's') {
                    appendLiteral(parts, literal);
                    addPart(parts, "[Leerraum]");
                } else if (escaped == 'd') {
                    appendLiteral(parts, literal);
                    addPart(parts, "[Zahl]");
                } else if (escaped == 'w') {
                    appendLiteral(parts, literal);
                    addPart(parts, "[Wortzeichen]");
                } else if ("bBAZzG".indexOf(escaped) < 0) {
                    literal.append(escaped);
                }
                continue;
            }
            if (current == '[') {
                appendLiteral(parts, literal);
                int end = findClassEnd(regex, i + 1);
                String value = end < 0 ? regex.substring(i + 1) : regex.substring(i + 1, end);
                addPart(parts, value.startsWith("^") ? "[anderes Zeichen]" : "[Zeichen: " + compact(value) + "]");
                i = end < 0 ? regex.length() : end;
            } else if (current == '.') {
                appendLiteral(parts, literal);
                addPart(parts, "[beliebiger Text]");
            } else if (current == '|') {
                appendLiteral(parts, literal);
                addPart(parts, "oder");
            } else if (current == '(' && i + 2 < regex.length() && regex.charAt(i + 1) == '?') {
                int flagEnd = regex.indexOf(')', i + 2);
                int colon = regex.indexOf(':', i + 2);
                if (flagEnd >= 0 && (colon < 0 || flagEnd < colon)) i = flagEnd;
            } else if ("^$(){}*+?".indexOf(current) >= 0) {
                appendLiteral(parts, literal);
            } else {
                literal.append(current);
            }
        }
        appendLiteral(parts, literal);
        if (parts.size() >= MAX_PARTS) shortened = true;
        if (shortened) parts.add("…");
        return parts;
    }

    private static void appendLiteral(List<String> parts, StringBuilder literal) {
        String value = literal.toString().trim();
        literal.setLength(0);
        if (!value.isEmpty()) addPart(parts, quote(value));
    }

    private static void addPart(List<String> parts, String value) {
        if (parts.size() >= MAX_PARTS || value.isBlank()) return;
        if (!parts.isEmpty() && parts.getLast().equals(value) && value.startsWith("[")) return;
        parts.add(value);
    }

    private static String quote(String value) {
        String compact = compact(value);
        if (compact.length() > 28) compact = compact.substring(0, 27) + "…";
        return "„" + compact + "“";
    }

    private static String compact(String value) {
        return value.replaceAll("\\s+", " ").trim();
    }

    private static int findClassEnd(String regex, int from) {
        boolean escaped = false;
        for (int i = from; i < regex.length(); i++) {
            char current = regex.charAt(i);
            if (current == ']' && !escaped) return i;
            escaped = current == '\\' && !escaped;
            if (current != '\\') escaped = false;
        }
        return -1;
    }

    private static boolean hasUnescapedAnchor(String regex, char anchor) {
        if (anchor == '^') return regex.startsWith("^") || regex.matches("^\\(\\?[a-zA-Z-]+\\)\\^.*");
        int index = regex.length() - 1;
        return index >= 0 && regex.charAt(index) == '$' && (index == 0 || regex.charAt(index - 1) != '\\');
    }
}
