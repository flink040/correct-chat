package de.dho.correctchat.core;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;

import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** Discovers user-installed Fabric mods and conservative chat-message hints from their resources. */
public final class ModCatalog {
    public record Entry(String id, String name, String version, int messageHints) { }

    private record Details(Set<String> namespaces, List<Pattern> messagePatterns) { }

    private static final int MAX_LANGUAGE_PATTERNS = 750;
    private static final Set<String> MESSAGE_KEY_MARKERS = Set.of(
            "chat", "message", "command", "feedback", "warning", "error", "status", "notice", "reply");
    private static final List<Entry> ENTRIES;
    private static final Map<String, Details> DETAILS;

    static {
        Map<String, Details> details = new HashMap<>();
        List<Entry> entries = new ArrayList<>();
        Path modsDirectory = FabricLoader.getInstance().getGameDir().resolve("mods").toAbsolutePath().normalize();
        for (ModContainer container : FabricLoader.getInstance().getAllMods()) {
            String id = normalize(container.getMetadata().getId());
            if (id.isEmpty() || !isFromModsDirectory(container, modsDirectory)) continue;
            Set<String> namespaces = discoverNamespaces(container);
            namespaces.add(id);
            container.getMetadata().getProvides().stream().map(ModCatalog::normalize)
                    .filter(value -> !value.isEmpty()).forEach(namespaces::add);
            List<Pattern> patterns = discoverMessagePatterns(container, namespaces);
            details.put(id, new Details(Set.copyOf(namespaces), List.copyOf(patterns)));
            entries.add(new Entry(id, container.getMetadata().getName(),
                    container.getMetadata().getVersion().getFriendlyString(), patterns.size()));
        }
        entries.sort(Comparator.comparing(Entry::name, String.CASE_INSENSITIVE_ORDER));
        ENTRIES = List.copyOf(entries);
        DETAILS = Map.copyOf(details);
    }

    private ModCatalog() { }

    public static List<Entry> installedMods() {
        return ENTRIES;
    }

    /** Matches strong translation namespaces first, then conservative message templates from lang JSON files. */
    public static boolean matches(String modId, Component component) {
        if (component == null) return false;
        String normalized = normalize(modId);
        Details details = DETAILS.get(normalized);
        if (details == null) return false;
        if (translationMatches(component, details.namespaces(), 0)) return true;
        String text = component.getString();
        return !text.isBlank() && details.messagePatterns().stream().anyMatch(pattern -> pattern.matcher(text).matches());
    }

    private static boolean isFromModsDirectory(ModContainer container, Path modsDirectory) {
        try {
            for (Path path : container.getOrigin().getPaths()) {
                if (path.toAbsolutePath().normalize().startsWith(modsDirectory)) return true;
            }
        } catch (RuntimeException ignored) {
            // A synthetic development container may not expose a conventional origin.
        }
        return false;
    }

    private static Set<String> discoverNamespaces(ModContainer container) {
        Set<String> result = new HashSet<>();
        for (Path root : container.getRootPaths()) {
            Path assets = root.resolve("assets");
            if (!Files.isDirectory(assets)) continue;
            try (var children = Files.list(assets)) {
                children.filter(Files::isDirectory)
                        .map(path -> normalize(path.getFileName().toString()))
                        .filter(value -> !value.isEmpty())
                        .forEach(result::add);
            } catch (Exception ignored) {
                // A malformed optional resource tree must not prevent the game from starting.
            }
        }
        return result;
    }

    private static List<Pattern> discoverMessagePatterns(ModContainer container, Set<String> namespaces) {
        List<Pattern> result = new ArrayList<>();
        for (Path root : container.getRootPaths()) {
            for (String namespace : namespaces) {
                Path languageDirectory = root.resolve("assets").resolve(namespace).resolve("lang");
                if (!Files.isDirectory(languageDirectory)) continue;
                try (var files = Files.list(languageDirectory)) {
                    for (Path file : files.filter(path -> path.getFileName().toString().endsWith(".json")).toList()) {
                        readLanguageFile(file, result);
                        if (result.size() >= MAX_LANGUAGE_PATTERNS) return result;
                    }
                } catch (Exception ignored) {
                    // Language resources are hints only; unreadable files are safely skipped.
                }
            }
        }
        return result;
    }

    private static void readLanguageFile(Path file, List<Pattern> result) {
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            JsonElement parsed = JsonParser.parseReader(reader);
            if (!parsed.isJsonObject()) return;
            for (Map.Entry<String, JsonElement> value : parsed.getAsJsonObject().entrySet()) {
                if (result.size() >= MAX_LANGUAGE_PATTERNS) break;
                if (!value.getValue().isJsonPrimitive()) continue;
                String key = value.getKey().toLowerCase(Locale.ROOT);
                if (MESSAGE_KEY_MARKERS.stream().noneMatch(key::contains)) continue;
                String template = value.getValue().getAsString().strip();
                if (template.length() < 8 || template.length() > 512 || template.lines().count() > 1) continue;
                result.add(Pattern.compile(templateRegex(template), Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE));
            }
        } catch (Exception ignored) {
            // Keep discovery best-effort and isolated from normal chat operation.
        }
    }

    private static String templateRegex(String template) {
        StringBuilder regex = new StringBuilder("^\\s*");
        int literalStart = 0;
        for (int index = 0; index < template.length(); index++) {
            int placeholderEnd = placeholderEnd(template, index);
            if (placeholderEnd < 0) continue;
            if (index > literalStart) regex.append(Pattern.quote(template.substring(literalStart, index)));
            char type = template.charAt(placeholderEnd - 1);
            regex.append(type == 'd' || type == 'i' || type == 'f' ? "[-+]?\\d+(?:[.,]\\d+)?" : ".+?");
            index = placeholderEnd - 1;
            literalStart = placeholderEnd;
        }
        if (literalStart < template.length()) regex.append(Pattern.quote(template.substring(literalStart)));
        return regex.append("\\s*$").toString();
    }

    private static int placeholderEnd(String value, int index) {
        if (value.charAt(index) == '%' && index + 1 < value.length()) {
            int cursor = index + 1;
            while (cursor < value.length() && (Character.isDigit(value.charAt(cursor)) || value.charAt(cursor) == '$')) cursor++;
            if (cursor < value.length() && "sdif".indexOf(Character.toLowerCase(value.charAt(cursor))) >= 0) return cursor + 1;
        }
        if (value.charAt(index) == '{') {
            int close = value.indexOf('}', index + 1);
            if (close > index + 1 && value.substring(index + 1, close).chars().allMatch(Character::isDigit)) return close + 1;
        }
        return -1;
    }

    private static boolean translationMatches(Component component, Set<String> namespaces, int depth) {
        if (component == null || depth > 16) return false;
        if (component.getContents() instanceof TranslatableContents translatable) {
            String key = translatable.getKey().toLowerCase(Locale.ROOT);
            for (String namespace : namespaces) {
                if (key.startsWith(namespace + ".") || key.startsWith(namespace + ":")) return true;
            }
            for (Object argument : translatable.getArgs()) {
                if (argument instanceof Component nested && translationMatches(nested, namespaces, depth + 1)) return true;
            }
        }
        for (Component sibling : component.getSiblings()) {
            if (translationMatches(sibling, namespaces, depth + 1)) return true;
        }
        return false;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
