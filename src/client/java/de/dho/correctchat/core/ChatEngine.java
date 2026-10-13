package de.dho.correctchat.core;

import de.dho.correctchat.CorrectChatClient;
import de.dho.correctchat.config.CorrectChatConfig;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.authlib.GameProfile;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ChatEngine {
    static final String INTERNAL_SEND = "correctchat-send-anyway ";
    static final String INTERNAL_CANCEL_LINK = "correctchat-cancel-link";
    private static final Pattern PLAYER_NAME = Pattern.compile("[A-Za-z0-9_]{1,16}");
    private final ChatLogService logService = new ChatLogService();
    private final ChatAlertOverlay alertOverlay = new ChatAlertOverlay();
    private final Map<String, List<Long>> responseUsage = new HashMap<>();
    private final Map<CorrectChatConfig.ChatListener, Long> globalCooldowns = new IdentityHashMap<>();
    private final Map<CorrectChatConfig.ChatListener, Map<String, Long>> partialCooldowns = new IdentityHashMap<>();
    private final Map<CorrectChatConfig.ChatAlert, Long> alertCooldowns = new IdentityHashMap<>();
    private final RegexCache regexes = new RegexCache();
    private final Set<String> warnedInvalidRegexes = ConcurrentHashMap.newKeySet();
    private volatile long responseGeneration;
    private volatile String cachedTimePattern;
    private volatile DateTimeFormatter cachedTimeFormatter;
    private String bypassOnce;

    public void register() {
        alertOverlay.register();
        registerInternalCommands();
        ClientSendMessageEvents.ALLOW_CHAT.register(this::allowOutgoingChat);
        ClientSendMessageEvents.MODIFY_CHAT.register(this::replaceOutgoingChat);
        ClientSendMessageEvents.ALLOW_COMMAND.register(this::allowOutgoingCommand);
        ClientSendMessageEvents.MODIFY_COMMAND.register(this::replaceOutgoingCommand);

        ClientReceiveMessageEvents.ALLOW_CHAT.register((message, signed, sender, params, timestamp) -> {
            Processed processed = processIncoming(message, ChatWorkspace.Kind.PLAYER, sender);
            if (!processed.cancelled && processed.changed) {
                Minecraft.getInstance().gui.hud.getChat().addClientSystemMessage(processed.component);
            }
            return !processed.cancelled && !processed.changed;
        });
        ClientReceiveMessageEvents.ALLOW_GAME.register((message, overlay) -> {
            if (overlay) {
                if (CorrectChatClient.workspace() != null && CorrectChatClient.workspace().shouldCaptureOverlay(message)) {
                    Processed processed = processIncoming(message, ChatWorkspace.Kind.SYSTEM, null);
                    if (!processed.cancelled) Minecraft.getInstance().gui.hud.getChat().addClientSystemMessage(processed.component);
                }
                return true;
            }
            Processed processed = processIncoming(message, ChatWorkspace.Kind.SYSTEM, null);
            if (!processed.cancelled && processed.changed) {
                Minecraft.getInstance().gui.hud.getChat().addClientSystemMessage(processed.component);
            }
            return !processed.cancelled && !processed.changed;
        });
    }

    public void onConfigChanged() {
        responseGeneration++;
        responseUsage.clear();
        globalCooldowns.clear();
        partialCooldowns.clear();
        alertCooldowns.clear();
        regexes.clear();
        warnedInvalidRegexes.clear();
        cachedTimePattern = null;
        cachedTimeFormatter = null;
        alertOverlay.clear();
        CustomSoundPlayer.stopAll();
    }

    public ChatLogService logs() { return logService; }
    public void stopCustomSounds() { CustomSoundPlayer.stopAll(); }

    public Component processTwitch(Component original) {
        Processed processed = processIncoming(original, ChatWorkspace.Kind.TWITCH, null);
        return processed.cancelled ? null : processed.component;
    }

    private void registerInternalCommands() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(ClientCommands.literal("correctchat-send-anyway")
                    .then(ClientCommands.argument("payload", StringArgumentType.greedyString())
                            .executes(context -> sendEncodedMessage(StringArgumentType.getString(context, "payload")))));
            dispatcher.register(ClientCommands.literal(INTERNAL_CANCEL_LINK)
                    .executes(context -> 1));
            dispatcher.register(ClientCommands.literal("ignorelist")
                    .executes(context -> showIgnoredPlayers()));
            dispatcher.register(ClientCommands.literal("ignoreclear")
                    .executes(context -> clearIgnoredPlayers()));
        });
    }

    private int sendEncodedMessage(String encoded) {
        try {
            String message = new String(Base64.getUrlDecoder().decode(encoded.trim()), StandardCharsets.UTF_8);
            bypassOnce = message;
            Minecraft client = Minecraft.getInstance();
            if (client.getConnection() != null) client.getConnection().sendChat(message);
            return 1;
        } catch (IllegalArgumentException exception) {
            CorrectChatClient.LOGGER.warn("Invalid internal send-anyway payload");
            return 0;
        }
    }

    private boolean allowOutgoingChat(String message) {
        CorrectChatConfig config = CorrectChatClient.config();
        if (!config.enabled) return true;
        if (CorrectChatClient.workspace() != null && CorrectChatClient.workspace().isPrimaryTwitchTab()) {
            if (CorrectChatClient.twitch() != null) CorrectChatClient.twitch().sendMessage(message);
            return false;
        }
        if (message.equals(bypassOnce)) {
            bypassOnce = null;
            return true;
        }
        if (config.antiCommandChoker && message.startsWith("7") && !message.startsWith("7 ") && message.length() > 1) {
            showChokerWarning(message);
            return false;
        }
        List<String> blockedLinks = ChatLinkGuard.blockedLinks(message, config.linkExceptions);
        if (config.linkFilter && !blockedLinks.isEmpty()) {
            ChatLinkGuard.showWarning(message, blockedLinks);
            return false;
        }
        return true;
    }

    private boolean allowOutgoingCommand(String command) {
        // Keep /ignore out of the client dispatcher: forwarding it with sendCommand would
        // re-enter Fabric's client-command handler and execute the same command recursively.
        if (command.equals("ignore")) {
            showIgnoredPlayers();
            return false;
        }
        if (command.startsWith("ignore ")) {
            String[] parts = command.trim().split("\\s+");
            if (parts.length == 2 && PLAYER_NAME.matcher(parts[1]).matches()) {
                toggleIgnoredPlayer(parts[1]);
            }
        }
        if (command.equals(INTERNAL_CANCEL_LINK)) {
            return false;
        }
        if (command.startsWith(INTERNAL_SEND)) {
            sendEncodedMessage(command.substring(INTERNAL_SEND.length()));
            return false;
        }
        if (command.equals("lcl") || command.startsWith("lcl ")) {
            Integer amount = null;
            String[] parts = command.trim().split("\\s+");
            if (parts.length > 1) {
                try { amount = Integer.parseInt(parts[1]); }
                catch (NumberFormatException ignored) { }
            }
            logService.exportOrUpload(amount);
            return false;
        }
        if (CorrectChatClient.workspace() != null && CorrectChatClient.workspace().isPrimaryTwitchTab()) {
            if (CorrectChatClient.twitch() != null) CorrectChatClient.twitch().sendMessage("/" + command);
            return false;
        }
        if (CorrectChatClient.workspace() != null) CorrectChatClient.workspace().noteCommand(command);
        return true;
    }

    private String replaceOutgoingChat(String message) {
        String result = replaceOutgoingText(message);
        CorrectChatConfig config = CorrectChatClient.config();
        if (!config.enabled || config.defaultChatColor == CorrectChatConfig.ChatColor.NONE) return result;
        return config.defaultChatColor.code + result;
    }

    private String replaceOutgoingCommand(String command) {
        return replaceOutgoingText(command);
    }

    private String replaceOutgoingText(String message) {
        CorrectChatConfig config = CorrectChatClient.config();
        if (!config.enabled || !config.textReplacement) return message;
        String result = message;
        for (CorrectChatConfig.TextReplacement replacement : config.replacements) {
            if (replacement.enabled && !replacement.find.isEmpty() && allowedOnServer(replacement.server)) {
                result = result.replace(replacement.find, replacement.replacement);
            }
        }
        return result;
    }

    private Processed processIncoming(Component original, ChatWorkspace.Kind kind, GameProfile sender) {
        CorrectChatConfig config = CorrectChatClient.config();
        if (!config.enabled) {
            return new Processed(original, false, false);
        }
        String plain = original.getString();
        if (isIgnoredMessage(config, plain, sender)) {
            return new Processed(original, true, false);
        }
        if (kind == ChatWorkspace.Kind.TWITCH) logService.add(plain, "twitch:#" + config.twitchChannel);
        else logService.add(plain);
        runAlerts(plain, kind);
        boolean cancelled = config.chatListeners && runListeners(plain, kind);
        if (cancelled) return new Processed(original, true, false);

        Component result = original;
        boolean changed = false;
        if (config.textReplacement) {
            for (CorrectChatConfig.TextReplacement replacement : config.replacements) {
                if (replacement.enabled && replacement.onReceive && !replacement.find.isEmpty() && allowedOnServer(replacement.server)) {
                    Component replaced = replacePreservingStyles(result, replacement.find, replacement.replacement);
                    if (!replaced.getString().equals(result.getString())) changed = true;
                    result = replaced;
                }
            }
        }
        if (config.chatTime) {
            String time = formatTime(config.timeFormat, System.currentTimeMillis());
            result = LegacyText.parse(config.timeStyle.replace("%time%", time)).append(result.copy());
            changed = true;
        }
        if (config.copyButton && !plain.isBlank()) {
            String copyText = result.getString();
            Component button = LegacyText.parse(config.copyFormat).withStyle(style -> style
                    .withClickEvent(new ClickEvent.CopyToClipboard(copyText))
                    .withHoverEvent(new HoverEvent.ShowText(Component.literal(config.copyTooltip))));
            result = button.copy().append(result.copy());
            changed = true;
        }
        CorrectChatClient.workspace().record(result, kind, plain);
        return new Processed(result, false, changed);
    }

    private String formatTime(String pattern, long timestamp) {
        String requested = pattern == null || pattern.isBlank() ? "HH:mm:ss" : pattern;
        DateTimeFormatter formatter = cachedTimeFormatter;
        if (formatter == null || !requested.equals(cachedTimePattern)) {
            try {
                formatter = DateTimeFormatter.ofPattern(requested, Locale.ROOT).withZone(ZoneId.systemDefault());
            } catch (IllegalArgumentException exception) {
                formatter = DateTimeFormatter.ofPattern("HH:mm:ss", Locale.ROOT).withZone(ZoneId.systemDefault());
            }
            cachedTimeFormatter = formatter;
            cachedTimePattern = requested;
        }
        return formatter.format(Instant.ofEpochMilli(timestamp));
    }

    private int toggleIgnoredPlayer(String player) {
        if (!PLAYER_NAME.matcher(player).matches()) {
            localMessage("§cUngültiger Spielername: §f" + player);
            return 0;
        }
        CorrectChatConfig config = CorrectChatClient.config();
        int existing = indexOfIgnore(config, player);
        boolean ignored = existing < 0;
        if (ignored) config.ignoredPlayers.add(player);
        else config.ignoredPlayers.remove(existing);
        CorrectChatClient.applyConfig(config);

        localMessage(ignored
                ? "§a" + player + " wird jetzt vollständig ausgeblendet."
                : "§e" + player + " wird nicht mehr ausgeblendet.");
        return 1;
    }

    private int showIgnoredPlayers() {
        CorrectChatConfig config = CorrectChatClient.config();
        if (config.ignoredPlayers.isEmpty()) {
            localMessage("§7Keine Spieler lokal ignoriert.");
        } else {
            localMessage("§eLokal ignoriert (§f" + config.ignoredPlayers.size() + "§e): §f"
                    + String.join("§7, §f", config.ignoredPlayers));
        }
        return 1;
    }

    private int clearIgnoredPlayers() {
        CorrectChatConfig config = CorrectChatClient.config();
        int count = config.ignoredPlayers.size();
        config.ignoredPlayers.clear();
        CorrectChatClient.applyConfig(config);
        localMessage("§aLokale Ignore-Liste geleert (§f" + count + "§a)."
                + (count > 0 ? " §7Die serverseitige Ignore-Liste bleibt unverändert." : ""));
        return 1;
    }

    private boolean isIgnoredMessage(CorrectChatConfig config, String message, GameProfile sender) {
        if (sender != null && isIgnored(config, sender.name())) return true;
        String detectedPlayer = MessageRuleSuggestion.detectPlayer(message);
        if (!detectedPlayer.isEmpty() && isIgnored(config, detectedPlayer)) return true;
        for (String ignored : config.ignoredPlayers) {
            if (looksLikePluginChat(message, ignored)) return true;
        }
        return false;
    }

    private static boolean isIgnored(CorrectChatConfig config, String player) {
        return indexOfIgnore(config, player) >= 0;
    }

    private static int indexOfIgnore(CorrectChatConfig config, String player) {
        for (int i = 0; i < config.ignoredPlayers.size(); i++) {
            if (config.ignoredPlayers.get(i).equalsIgnoreCase(player)) return i;
        }
        return -1;
    }

    private boolean looksLikePluginChat(String message, String player) {
        String ranks = "(?:\\[[^\\r\\n\\]]{1,32}\\]\\s*)*";
        String openingBracket = "(?:<\\s*)?";
        String separator = "(?:\\s*>|\\s*(?::|»|›|\\||→))";
        return regexes.find("(?iu)^\\s*" + ranks + openingBracket + Pattern.quote(player) + separator, message);
    }

    private static void localMessage(String message) {
        Minecraft.getInstance().gui.hud.getChat().addClientSystemMessage(LegacyText.parse(message));
    }

    private boolean runListeners(String message, ChatWorkspace.Kind kind) {
        CorrectChatConfig config = CorrectChatClient.config();
        boolean cancel = false;
        for (CorrectChatConfig.ChatListener listener : config.listeners) {
            if (!listener.enabled || !allowedOnServer(listener.server) || blacklisted(listener, message)) continue;
            Match match = match(listener, message);
            if (!match.matches || !cooldownAllows(listener, match.matcher)) continue;
            String key = listener.name + '\0' + listener.match + '\0' + listener.response;
            boolean canRespond = !config.antiSpam || antiSpamAllows(key, config.maxResponses, config.responseWindowMinutes);
            String response = substituteGroups(listener.response, match.matcher);
            if (!response.isBlank() && (canRespond || response.startsWith("/"))) scheduleResponse(response, listener.delaySeconds, kind);
            if (listener.sound.enabled) scheduleSound(listener.sound, listener.delaySeconds, kind);
            cancel |= listener.cancel;
        }
        return cancel;
    }

    private void runAlerts(String message, ChatWorkspace.Kind kind) {
        CorrectChatConfig config = CorrectChatClient.config();
        for (CorrectChatConfig.ChatAlert alert : config.alerts) {
            if (!alert.enabled || alert.match.isBlank() || !allowedOnServer(alert.server)) continue;
            if (!matches(alert, message) || !alertCooldownAllows(alert)) continue;
            alertOverlay.show(alert, message);
            if (alert.sound.enabled) scheduleSound(alert.sound, 0.0F, kind);
        }
    }

    private boolean matches(CorrectChatConfig.ChatAlert alert, String message) {
        String patternText = alert.match.replace("&player", playerName());
        return switch (alert.matchType) {
            case EQUALS -> alert.caseSensitive ? message.equals(patternText) : message.equalsIgnoreCase(patternText);
            case CONTAINS -> alert.caseSensitive ? message.contains(patternText)
                    : message.toLowerCase(Locale.ROOT).contains(patternText.toLowerCase(Locale.ROOT));
            case REGEX, SIMPLIFIED_REGEX -> {
                if (alert.matchType == CorrectChatConfig.MatchType.SIMPLIFIED_REGEX) patternText = simplifiedRegex(patternText);
                Matcher matcher = regexes.matcher(patternText, message);
                if (matcher == null && warnedInvalidRegexes.add(patternText)) {
                    CorrectChatClient.LOGGER.warn("Invalid chat alert regex '{}'", patternText);
                }
                yield matcher != null && matcher.matches();
            }
        };
    }

    private Match match(CorrectChatConfig.ChatListener listener, String message) {
        String patternText = listener.match.replace("&player", playerName());
        return switch (listener.matchType) {
            case EQUALS -> new Match(listener.caseSensitive ? message.equals(patternText) : message.equalsIgnoreCase(patternText), null);
            case CONTAINS -> new Match(listener.caseSensitive ? message.contains(patternText)
                    : message.toLowerCase(Locale.ROOT).contains(patternText.toLowerCase(Locale.ROOT)), null);
            case REGEX, SIMPLIFIED_REGEX -> {
                if (listener.matchType == CorrectChatConfig.MatchType.SIMPLIFIED_REGEX) patternText = simplifiedRegex(patternText);
                Matcher matcher = regexes.matcher(patternText, message);
                if (matcher == null) {
                    if (warnedInvalidRegexes.add(patternText)) {
                        CorrectChatClient.LOGGER.warn("Invalid chat listener regex '{}'", patternText);
                    }
                    yield new Match(false, null);
                }
                yield new Match(matcher.matches(), matcher);
            }
        };
    }

    private boolean alertCooldownAllows(CorrectChatConfig.ChatAlert alert) {
        if (alert.cooldownSeconds <= 0.0F) return true;
        long now = System.currentTimeMillis();
        long previous = alertCooldowns.getOrDefault(alert, 0L);
        if (now - previous < (long)(alert.cooldownSeconds * 1000.0F)) return false;
        alertCooldowns.put(alert, now);
        return true;
    }

    private boolean cooldownAllows(CorrectChatConfig.ChatListener listener, Matcher matcher) {
        if (!listener.cooldown.enabled || listener.cooldown.seconds <= 0) return true;
        long now = System.currentTimeMillis();
        long duration = (long)(listener.cooldown.seconds * 1000.0F);
        if (listener.cooldown.type == CorrectChatConfig.CooldownType.GLOBAL) {
            long previous = globalCooldowns.getOrDefault(listener, 0L);
            if (now - previous < duration) return false;
            globalCooldowns.put(listener, now);
            return true;
        }
        if (matcher == null || listener.cooldown.group > matcher.groupCount()) return false;
        String group = matcher.group(listener.cooldown.group);
        Map<String, Long> usages = partialCooldowns.computeIfAbsent(listener, ignored -> new HashMap<>());
        long previous = usages.getOrDefault(group, 0L);
        if (now - previous < duration) return false;
        usages.put(group, now);
        return true;
    }

    private boolean antiSpamAllows(String key, int max, int minutes) {
        long now = System.currentTimeMillis();
        long window = minutes * 60_000L;
        List<Long> uses = responseUsage.computeIfAbsent(key, ignored -> new ArrayList<>());
        uses.removeIf(time -> now - time >= window);
        boolean allowed = uses.size() < max;
        uses.add(now);
        return allowed;
    }

    private void scheduleResponse(String response, float delaySeconds, ChatWorkspace.Kind kind) {
        Minecraft scheduledClient = Minecraft.getInstance();
        var scheduledConnection = scheduledClient.getConnection();
        String scheduledTwitchChannel = CorrectChatClient.config().twitchChannel;
        long scheduledGeneration = responseGeneration;
        CompletableFuture.delayedExecutor(Math.max(0L, (long)(delaySeconds * 1000.0F)), TimeUnit.MILLISECONDS).execute(() -> {
            Minecraft client = Minecraft.getInstance();
            client.execute(() -> {
                CorrectChatConfig config = CorrectChatClient.config();
                if (config == null || !config.enabled || !config.chatListeners
                        || scheduledGeneration != responseGeneration) return;
                if (kind == ChatWorkspace.Kind.TWITCH) {
                    if (!config.twitchEnabled || !config.twitchChannel.equals(scheduledTwitchChannel)
                            || CorrectChatClient.twitch() == null) return;
                    CorrectChatClient.twitch().sendMessage(response);
                    return;
                }
                if (scheduledConnection == null || client.getConnection() != scheduledConnection) return;
                if (response.startsWith("/")) client.getConnection().sendCommand(response.substring(1));
                else client.getConnection().sendChat(response);
            });
        });
    }

    private void scheduleSound(CorrectChatConfig.Sound sound, float delaySeconds, ChatWorkspace.Kind kind) {
        CorrectChatConfig.Sound scheduledSound = sound.copy();
        Minecraft scheduledClient = Minecraft.getInstance();
        var scheduledConnection = scheduledClient.getConnection();
        String scheduledTwitchChannel = CorrectChatClient.config().twitchChannel;
        long scheduledGeneration = responseGeneration;
        CompletableFuture.delayedExecutor(Math.max(0L, (long)(delaySeconds * 1000.0F)), TimeUnit.MILLISECONDS)
                .execute(() -> scheduledClient.execute(() -> {
                    CorrectChatConfig config = CorrectChatClient.config();
                    if (config == null || !config.enabled || scheduledGeneration != responseGeneration
                            || (kind == ChatWorkspace.Kind.TWITCH
                                ? !config.twitchEnabled || !config.twitchChannel.equals(scheduledTwitchChannel)
                                : scheduledConnection == null || scheduledClient.getConnection() != scheduledConnection)) return;
                    if (CustomSoundPlayer.supports(scheduledSound.file)) {
                        CustomSoundPlayer.play(scheduledSound.file, scheduledSound.volume, scheduledSound.pitch);
                        return;
                    }
                    if (!scheduledSound.file.isBlank()) {
                        CorrectChatClient.LOGGER.warn("Custom alarm sound does not exist or is unsupported: {}", scheduledSound.file);
                        return;
                    }
                    Identifier id = Identifier.tryParse(scheduledSound.id);
                    if (id == null) return;
                    SimpleSoundInstance instance = SimpleSoundInstance.forUI(
                            SoundEvent.createVariableRangeEvent(id), scheduledSound.pitch,
                            scheduledSound.volume / 100.0F);
                    scheduledClient.getSoundManager().play(instance);
                }));
    }

    private boolean allowedOnServer(CorrectChatConfig.ServerFilter filter) {
        if (!filter.enabled) return true;
        Minecraft client = Minecraft.getInstance();
        if (client.hasSingleplayerServer()) return filter.singlePlayer;
        if (client.getCurrentServer() == null) return false;
        return filter.address.isBlank() || filter.address.equalsIgnoreCase(stripPort(client.getCurrentServer().ip));
    }

    private static String stripPort(String address) {
        if (address == null) return "";
        int colon = address.lastIndexOf(':');
        return colon > 0 && address.indexOf(':') == colon ? address.substring(0, colon) : address;
    }

    private static boolean blacklisted(CorrectChatConfig.ChatListener listener, String message) {
        for (String word : listener.blacklist) if (!word.isEmpty() && message.contains(word)) return true;
        return false;
    }

    private static String playerName() {
        Minecraft client = Minecraft.getInstance();
        return client.player == null ? "" : client.player.getName().getString();
    }

    private static String substituteGroups(String response, Matcher matcher) {
        if (matcher == null) return response;
        String result = response;
        for (int i = 1; i <= matcher.groupCount(); i++) result = result.replace("%" + i, String.valueOf(matcher.group(i)));
        return result;
    }

    private static String simplifiedRegex(String input) {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (c == '*') result.append(".*");
            else if (c == '%') result.append("(.*)");
            else {
                if ("\\()[]+.^?{}$|".indexOf(c) >= 0) result.append('\\');
                result.append(c);
            }
        }
        return result.toString();
    }

    private static Component replacePreservingStyles(Component component, String find, String replacement) {
        MutableComponent result = Component.empty();
        for (Component flat : component.toFlatList()) {
            result.append(Component.literal(flat.getString().replace(find, replacement)).setStyle(flat.getStyle()));
        }
        return result;
    }

    private static void showChokerWarning(String message) {
        Minecraft client = Minecraft.getInstance();
        String encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(message.getBytes(StandardCharsets.UTF_8));
        MutableComponent corrected = Localizer.literal("§a[KORREKTEN BEFEHL SENDEN]")
                .withStyle(style -> style.withClickEvent(new ClickEvent.RunCommand("/" + message.substring(1))));
        MutableComponent anyway = Localizer.literal("§b[NACHRICHT TROTZDEM SENDEN]")
                .withStyle(style -> style.withClickEvent(new ClickEvent.RunCommand("/" + INTERNAL_SEND + encoded)));
        client.gui.hud.getChat().addClientSystemMessage(Localizer.literal("§c§m-----------------------------------"));
        client.gui.hud.getChat().addClientSystemMessage(Localizer.literal("§e⚠ §cMisslungenen Befehl erkannt! §e⚠"));
        client.gui.hud.getChat().addClientSystemMessage(Localizer.literal("§cEingabe: §7'§f" + message + "§7'"));
        client.gui.hud.getChat().addClientSystemMessage(corrected);
        client.gui.hud.getChat().addClientSystemMessage(anyway);
        client.gui.hud.getChat().addClientSystemMessage(Localizer.literal("§c§m-----------------------------------"));
    }

    private record Processed(Component component, boolean cancelled, boolean changed) { }
    private record Match(boolean matches, Matcher matcher) { }
}
