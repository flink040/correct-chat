package de.dho.correctchat.core;

import de.dho.correctchat.config.CorrectChatConfig;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;

import java.util.List;
import java.util.EnumMap;
import java.util.IdentityHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** Pure message-to-tab routing, including optional plugin/mod recognition. */
final class ChatRouting {
    private static final Set<String> WORLD_EDIT_COMMANDS = Set.of(
            "worldedit", "we", "fawe", "cancel", "fcancel", "confirm", "undo", "redo", "clearhistory",
            "limit", "timeout", "perf", "gmask", "toggleplace", "toggleeditwand", "search", "searchitem",
            "sel", "pos1", "pos2", "hpos1", "hpos2", "chunk", "wand", "navwand", "drawsel",
            "contract", "outset", "inset", "shift", "size", "count", "distr", "set", "replace",
            "overlay", "walls", "faces", "smooth", "move", "stack", "regen", "naturalize", "generate",
            "hcyl", "cyl", "sphere", "hsphere", "pyramid", "hpyramid", "pumpkins", "forestgen", "flora",
            "schematic", "schem", "copy", "cut", "paste", "rotate", "flip", "brush", "tool", "unstuck",
            "ascend", "descend", "ceil", "thru", "jumpto", "up", "biomelist", "biomeinfo", "setbiome",
            "fixlighting", "removelighting", "setlighting", "setskylighting", "updateblocks", "wea",
            "weanywhere", "worldeditanywhere", "wer", "worldeditregion");
    private static final Pattern VULCAN_ALERT = Pattern.compile(
            "\\b(?:benutzt|failed|flagged)\\b.+(?:\\([^\\)\\r\\n]+/[a-z0-9]+\\)|\\[[^]\\r\\n]+/[a-z0-9]+\\])"
                    + ".*\\[\\s*(?:x\\s*)?\\d+\\s*/\\s*\\d+\\s*\\]"
                    + "|\\[vl:\\s*\\d+\\].*\\[ping:\\s*\\d+\\].*\\[tps:",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final List<String> VULCAN_MESSAGE_MARKERS = List.of(
            "violations for all online players were reset",
            "was setback for being in an unloaded chunk",
            "was kicked for sending an invalid confirmation packet",
            "was kicked for sending an out of order confirmation packet",
            "was kicked for not replying to confirmation packets",
            "was kicked for not replying to keepalive packets",
            "kicked for having too high ping",
            "position was updated for being on a ghost block",
            "was punished for", "wurde bestraft für", "bestraft für", " joined using ",
            "you are frozen", "logged out while frozen", "was frozen by", "was unfrozen by",
            "judgement day has", "judgement day v1 has");

    private final Map<Component, String> componentMods;
    private final RegexCache regexes = new RegexCache();
    private final IdentityHashMap<Component, EnumMap<ChatWorkspace.Kind, IdentityHashMap<CorrectChatConfig.ChatTab, Boolean>>>
            matchResults = new IdentityHashMap<>();

    ChatRouting(Map<Component, String> componentMods) {
        this.componentMods = componentMods;
    }

    boolean matches(CorrectChatConfig.ChatTab tab, Component component, ChatWorkspace.Kind kind,
                    List<CorrectChatConfig.ChatTab> allTabs) {
        if (tab == null) return true;
        EnumMap<ChatWorkspace.Kind, IdentityHashMap<CorrectChatConfig.ChatTab, Boolean>> byKind =
                matchResults.computeIfAbsent(component, ignored -> new EnumMap<>(ChatWorkspace.Kind.class));
        IdentityHashMap<CorrectChatConfig.ChatTab, Boolean> byTab =
                byKind.computeIfAbsent(kind, ignored -> new IdentityHashMap<>());
        Boolean cached = byTab.get(tab);
        if (cached != null) return cached;
        boolean result = matchesUncached(tab, component, kind, allTabs);
        byTab.put(tab, result);
        return result;
    }

    void clear() {
        matchResults.clear();
        regexes.clear();
    }

    void forget(Component component) {
        matchResults.remove(component);
    }

    private boolean matchesUncached(CorrectChatConfig.ChatTab tab, Component component, ChatWorkspace.Kind kind,
                                    List<CorrectChatConfig.ChatTab> allTabs) {
        String text = component.getString();
        if (matchesExplicitRoute(tab, component, text, kind)) return true;
        if (hasExplicitRoute(component, text, kind, allTabs)) return false;
        return tab.filters.stream().anyMatch(filter -> matchesFilter(filter, component, text, kind));
    }

    String detectedMod(Component component, List<CorrectChatConfig.ChatTab> tabs) {
        for (CorrectChatConfig.ChatTab tab : tabs) {
            for (CorrectChatConfig.TabFilter filter : tab.filters) {
                if (filter.type == CorrectChatConfig.TabType.MOD && modMatches(component, filter.value, 0)) {
                    return filter.value.trim().toLowerCase(Locale.ROOT);
                }
            }
        }
        return null;
    }

    static boolean commandMatchesMod(String command, String modId) {
        if (modId == null || modId.isBlank()) return false;
        String normalizedMod = modId.trim().toLowerCase(Locale.ROOT);
        String normalizedCommand = command.trim().toLowerCase(Locale.ROOT);
        String root = normalizedCommand.split("\\s+", 2)[0];
        if (root.equals(normalizedMod) || root.startsWith(normalizedMod + ":")) return true;
        if (normalizedMod.equals("coreprotect")) {
            return root.equals("co") || root.equals("core") || root.equals("coreprotect")
                    || root.equals("coreprotect:co") || root.equals("coreprotect:core");
        }
        if (normalizedMod.equals("vulcan")) {
            return root.equals("vulcan") || root.startsWith("vulcan:")
                    || root.equals("alerts") || root.endsWith(":alerts")
                    || root.equals("verbose") || root.endsWith(":verbose")
                    || root.equals("jday") || root.endsWith(":jday")
                    || root.equals("logs") || root.endsWith(":logs")
                    || root.equals("punishlogs") || root.endsWith(":punishlogs");
        }
        return normalizedMod.equals("worldedit")
                && (normalizedCommand.startsWith("/") || WORLD_EDIT_COMMANDS.contains(root)
                || root.equals("fawe") || root.equals("fastasyncworldedit")
                || root.startsWith("fawe:") || root.startsWith("fastasyncworldedit:"));
    }

    static long responseWindowMillis(String modId) {
        return switch (modId) {
            case "worldedit" -> 120_000L;
            case "coreprotect", "vulcan" -> 30_000L;
            default -> 5_000L;
        };
    }

    static boolean modMatches(Component component, String modId, int depth) {
        if (component == null || modId == null || modId.isBlank() || depth > 16) return false;
        String normalized = modId.trim().toLowerCase(Locale.ROOT);
        if (depth == 0) {
            String text = component.getString().toLowerCase(Locale.ROOT);
            if (normalized.equals("coreprotect") && text.contains("coreprotect")) return true;
            if (normalized.equals("vulcan") && (text.contains("vulcan")
                    || VULCAN_ALERT.matcher(text).find()
                    || VULCAN_MESSAGE_MARKERS.stream().anyMatch(text::contains)
                    || hasVulcanClickAction(component, 0))) return true;
            if (normalized.equals("worldedit") && (text.contains("worldedit")
                    || text.contains("fastasyncworldedit") || text.contains("fawe")
                    || hasWorldEditClickAction(component, 0))) return true;
        }
        if (component.getContents() instanceof TranslatableContents translatable) {
            if (translationKeyMatches(translatable.getKey(), normalized)
                    || normalized.equals("worldedit") && (translationKeyMatches(translatable.getKey(), "fawe")
                    || translationKeyMatches(translatable.getKey(), "fastasyncworldedit"))) return true;
            for (Object argument : translatable.getArgs()) {
                if (argument instanceof Component nested && modMatches(nested, normalized, depth + 1)) return true;
            }
        }
        for (Component sibling : component.getSiblings()) {
            if (modMatches(sibling, normalized, depth + 1)) return true;
        }
        return false;
    }

    private boolean matchesFilter(CorrectChatConfig.TabFilter filter, Component component, String text,
                                  ChatWorkspace.Kind kind) {
        return switch (filter.type) {
            case ALL -> true;
            case PLAYER -> kind == ChatWorkspace.Kind.PLAYER;
            case SYSTEM -> kind == ChatWorkspace.Kind.SYSTEM;
            case TWITCH -> kind == ChatWorkspace.Kind.TWITCH;
            case CONTAINS -> !filter.value.isBlank()
                    && text.toLowerCase(Locale.ROOT).contains(filter.value.toLowerCase(Locale.ROOT));
            case REGEX -> !filter.value.isBlank() && regexMatches(filter.value, text);
            case MOD -> {
                String sourceMod = componentMods.get(component);
                yield modMatches(component, filter.value, 0)
                        || sourceMod != null && sourceMod.equalsIgnoreCase(filter.value.trim());
            }
        };
    }

    private boolean hasExplicitRoute(Component component, String text, ChatWorkspace.Kind kind,
                                     List<CorrectChatConfig.ChatTab> tabs) {
        return tabs.stream().anyMatch(candidate -> matchesExplicitRoute(candidate, component, text, kind));
    }

    private boolean matchesExplicitRoute(CorrectChatConfig.ChatTab tab, Component component, String text,
                                         ChatWorkspace.Kind kind) {
        if (tab.routingRules.stream().anyMatch(rule -> regexMatches(rule, text))) return true;
        return tab.filters.stream().anyMatch(filter -> matchesExplicitFilter(filter, component, text, kind));
    }

    private boolean matchesExplicitFilter(CorrectChatConfig.TabFilter filter, Component component, String text,
                                          ChatWorkspace.Kind kind) {
        return switch (filter.type) {
            // Dedicated non-player streams must not leak into the catch-all tab.
            case SYSTEM -> kind == ChatWorkspace.Kind.SYSTEM;
            case TWITCH -> kind == ChatWorkspace.Kind.TWITCH;
            case REGEX -> !filter.value.isBlank() && regexMatches(filter.value, text);
            case CONTAINS -> !filter.value.isBlank()
                    && text.toLowerCase(Locale.ROOT).contains(filter.value.toLowerCase(Locale.ROOT));
            case MOD -> {
                String sourceMod = componentMods.get(component);
                yield modMatches(component, filter.value, 0)
                        || sourceMod != null && sourceMod.equalsIgnoreCase(filter.value.trim());
            }
            default -> false;
        };
    }

    private static boolean hasVulcanClickAction(Component component, int depth) {
        if (component == null || depth > 16) return false;
        ClickEvent clickEvent = component.getStyle().getClickEvent();
        if (clickEvent instanceof ClickEvent.RunCommand runCommand) {
            String command = runCommand.command().trim().toLowerCase(Locale.ROOT);
            if (command.equals("/vulcan") || command.startsWith("/vulcan ")
                    || command.startsWith("/vulcan:")) return true;
        }
        if (component.getContents() instanceof TranslatableContents translatable) {
            for (Object argument : translatable.getArgs()) {
                if (argument instanceof Component nested && hasVulcanClickAction(nested, depth + 1)) return true;
            }
        }
        for (Component sibling : component.getSiblings()) {
            if (hasVulcanClickAction(sibling, depth + 1)) return true;
        }
        return false;
    }

    private static boolean hasWorldEditClickAction(Component component, int depth) {
        if (component == null || depth > 16) return false;
        ClickEvent clickEvent = component.getStyle().getClickEvent();
        if (clickEvent instanceof ClickEvent.RunCommand runCommand) {
            String command = runCommand.command().trim();
            if (command.startsWith("/") && commandMatchesMod(command.substring(1), "worldedit")) return true;
        }
        if (component.getContents() instanceof TranslatableContents translatable) {
            for (Object argument : translatable.getArgs()) {
                if (argument instanceof Component nested && hasWorldEditClickAction(nested, depth + 1)) return true;
            }
        }
        for (Component sibling : component.getSiblings()) {
            if (hasWorldEditClickAction(sibling, depth + 1)) return true;
        }
        return false;
    }

    private static boolean translationKeyMatches(String key, String modId) {
        if (key == null) return false;
        String normalizedKey = key.toLowerCase(Locale.ROOT);
        if (normalizedKey.startsWith(modId + ".") || normalizedKey.startsWith(modId + ":")) return true;
        for (String segment : normalizedKey.split("[.:/]")) {
            if (segment.equals(modId)) return true;
        }
        return false;
    }

    private boolean regexMatches(String expression, String text) {
        if (expression.isEmpty()) return true;
        return regexes.find(expression, text);
    }
}
