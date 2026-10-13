package de.dho.correctchat.core;

import de.dho.correctchat.CorrectChatClient;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Detects outbound links and renders the one-time confirmation prompt. */
final class ChatLinkGuard {
    private static final Pattern URL_PATTERN = Pattern.compile(
            "(?i)(?<![@\\p{Alnum}_])(?:https?://|www\\.)[^\\s<>()]+|(?<![@\\p{Alnum}_])(?:[a-z0-9](?:[a-z0-9-]{0,62}[a-z0-9])?\\.)+[a-z]{2,63}(?:/[^\\s<>()]*)?");

    private ChatLinkGuard() {
    }

    static List<String> blockedLinks(String message, List<String> exceptions) {
        List<String> blocked = new ArrayList<>();
        Matcher matcher = URL_PATTERN.matcher(message);
        while (matcher.find()) {
            String url = trimUrlPunctuation(matcher.group());
            if (!url.isEmpty() && !isExcepted(url, exceptions)) blocked.add(url);
        }
        return blocked;
    }

    static void showWarning(String message, List<String> blockedLinks) {
        Minecraft client = Minecraft.getInstance();
        String encoded = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(message.getBytes(StandardCharsets.UTF_8));
        MutableComponent yes = Localizer.literal("[Ja]").withStyle(style -> style
                .withColor(ChatFormatting.GREEN)
                .withClickEvent(new ClickEvent.RunCommand("/" + ChatEngine.INTERNAL_SEND + encoded))
                .withHoverEvent(new HoverEvent.ShowText(Localizer.literal("Nachricht einmalig mit Link senden"))));
        MutableComponent no = Localizer.literal("[Nein]").withStyle(style -> style
                .withColor(ChatFormatting.RED)
                .withClickEvent(new ClickEvent.RunCommand("/" + ChatEngine.INTERNAL_CANCEL_LINK))
                .withHoverEvent(new HoverEvent.ShowText(Localizer.literal("Nachricht nicht senden"))));
        String summary = blockedLinks.getFirst()
                + (blockedLinks.size() > 1 ? " (+" + (blockedLinks.size() - 1) + ")" : "");
        client.gui.hud.getChat().addClientSystemMessage(Localizer.literal("§e⚠ Link: §f" + summary + "  ")
                .append(yes).append(Localizer.literal(" ")).append(no));
    }

    private static boolean isExcepted(String url, List<String> exceptions) {
        String normalizedUrl = normalizeUrl(url);
        for (String exception : exceptions) {
            String normalizedException = normalizeUrl(exception);
            if (normalizedException.isEmpty()) continue;
            if (normalizedUrl.equals(normalizedException)
                    || normalizedUrl.startsWith(normalizedException + "/")
                    || normalizedUrl.startsWith(normalizedException + "?")
                    || normalizedUrl.startsWith(normalizedException + "#")) return true;
        }
        return false;
    }

    private static String normalizeUrl(String value) {
        String normalized = trimUrlPunctuation(value.trim().toLowerCase(Locale.ROOT));
        if (normalized.startsWith("https://")) normalized = normalized.substring(8);
        else if (normalized.startsWith("http://")) normalized = normalized.substring(7);
        if (normalized.startsWith("www.")) normalized = normalized.substring(4);
        while (normalized.endsWith("/")) normalized = normalized.substring(0, normalized.length() - 1);
        return normalized;
    }

    private static String trimUrlPunctuation(String value) {
        int end = value.length();
        while (end > 0 && ".,;:!?)]}".indexOf(value.charAt(end - 1)) >= 0) end--;
        return value.substring(0, end);
    }
}
