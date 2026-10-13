package de.dho.correctchat.core;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import de.dho.correctchat.CorrectChatClient;
import de.dho.correctchat.config.CorrectChatConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.MutableComponent;

import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Localizes Correct Chat's own literal UI text without changing chat or user supplied content. */
public final class Localizer {
    private static final String ENGLISH_RESOURCE = "/assets/correctchat/lang/ui_en_us.json";
    private static final Map<String, String> ENGLISH = loadEnglish();
    private static final Map<Pattern, String> ENGLISH_TEMPLATES = compileTemplates(ENGLISH);
    private static CorrectChatConfig.UiLanguage previewLanguage;

    private Localizer() { }

    public static MutableComponent literal(String source) {
        return net.minecraft.network.chat.Component.literal(text(source));
    }

    public static String text(String source) {
        if (source == null || !english()) return source;
        String exact = ENGLISH.get(source);
        if (exact != null) return exact;
        for (Map.Entry<Pattern, String> entry : ENGLISH_TEMPLATES.entrySet()) {
            Matcher matcher = entry.getKey().matcher(source);
            if (!matcher.matches()) continue;
            String result = entry.getValue();
            for (int index = 1; index <= matcher.groupCount(); index++) {
                result = result.replace("{" + (index - 1) + "}", matcher.group(index));
            }
            return result;
        }
        return source;
    }

    public static void preview(CorrectChatConfig.UiLanguage language) {
        previewLanguage = language;
    }

    public static void clearPreview() {
        previewLanguage = null;
    }

    public static String languageLabel(CorrectChatConfig.UiLanguage language) {
        return switch (language) {
            case AUTO -> text("Automatisch");
            case DE_DE -> "Deutsch";
            case EN_US -> "English";
        };
    }

    private static boolean english() {
        CorrectChatConfig.UiLanguage selected = previewLanguage;
        if (selected == null && CorrectChatClient.config() != null) selected = CorrectChatClient.config().uiLanguage;
        if (selected == CorrectChatConfig.UiLanguage.EN_US) return true;
        if (selected == CorrectChatConfig.UiLanguage.DE_DE) return false;
        try {
            return !Minecraft.getInstance().getLanguageManager().getSelected()
                    .toLowerCase(java.util.Locale.ROOT).startsWith("de_");
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private static Map<String, String> loadEnglish() {
        try (var stream = Localizer.class.getResourceAsStream(ENGLISH_RESOURCE)) {
            if (stream == null) return Map.of();
            Type type = new TypeToken<LinkedHashMap<String, String>>() { }.getType();
            Map<String, String> values = new Gson().fromJson(
                    new InputStreamReader(stream, StandardCharsets.UTF_8), type);
            return values == null ? Map.of() : values;
        } catch (Exception exception) {
            CorrectChatClient.LOGGER.error("Unable to load English UI translations", exception);
            return Map.of();
        }
    }

    private static Map<Pattern, String> compileTemplates(Map<String, String> translations) {
        Map<Pattern, String> result = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : translations.entrySet()) {
            if (!entry.getKey().contains("{0}")) continue;
            Matcher placeholders = Pattern.compile("\\{(\\d+)}").matcher(entry.getKey());
            StringBuilder regex = new StringBuilder("^");
            int end = 0;
            while (placeholders.find()) {
                regex.append(Pattern.quote(entry.getKey().substring(end, placeholders.start()))).append("(.*?)");
                end = placeholders.end();
            }
            regex.append(Pattern.quote(entry.getKey().substring(end))).append('$');
            result.put(Pattern.compile(regex.toString(), Pattern.DOTALL), entry.getValue());
        }
        return result;
    }
}
