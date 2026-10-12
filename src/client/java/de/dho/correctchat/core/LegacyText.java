package de.dho.correctchat.core;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

public final class LegacyText {
    private LegacyText() {
    }

    public static MutableComponent parse(String input) {
        MutableComponent result = Component.empty();
        if (input == null || input.isEmpty()) return result;
        StringBuilder part = new StringBuilder();
        ChatFormatting color = null;
        boolean bold = false, italic = false, underlined = false, strike = false, obfuscated = false;
        for (int i = 0; i < input.length(); i++) {
            char current = input.charAt(i);
            if ((current == '&' || current == '§') && i + 1 < input.length()) {
                ChatFormatting format = ChatFormatting.getByCode(input.charAt(i + 1));
                if (format != null) {
                    append(result, part, color, bold, italic, underlined, strike, obfuscated);
                    part.setLength(0);
                    if (format == ChatFormatting.RESET) {
                        color = null;
                        bold = italic = underlined = strike = obfuscated = false;
                    } else if (format.ordinal() <= ChatFormatting.WHITE.ordinal()) {
                        color = format;
                        bold = italic = underlined = strike = obfuscated = false;
                    } else {
                        switch (format) {
                            case BOLD -> bold = true;
                            case ITALIC -> italic = true;
                            case UNDERLINE -> underlined = true;
                            case STRIKETHROUGH -> strike = true;
                            case OBFUSCATED -> obfuscated = true;
                            default -> { }
                        }
                    }
                    i++;
                    continue;
                }
            }
            part.append(current);
        }
        append(result, part, color, bold, italic, underlined, strike, obfuscated);
        return result;
    }

    public static boolean hasFormattingCode(String input) {
        if (input == null) return false;
        for (int i = 0; i + 1 < input.length(); i++) {
            char marker = input.charAt(i);
            if ((marker == '&' || marker == '§') && ChatFormatting.getByCode(input.charAt(i + 1)) != null) {
                return true;
            }
        }
        return false;
    }

    private static void append(MutableComponent target, StringBuilder text, ChatFormatting color,
                               boolean bold, boolean italic, boolean underlined, boolean strike, boolean obfuscated) {
        if (text.isEmpty()) return;
        MutableComponent component = Component.literal(text.toString());
        if (color != null) component.withStyle(color);
        component.withStyle(style -> style.withBold(bold).withItalic(italic).withUnderlined(underlined)
                .withStrikethrough(strike).withObfuscated(obfuscated));
        target.append(component);
    }
}
