package de.dho.correctchat.core;

import de.dho.correctchat.CorrectChatClient;
import de.dho.correctchat.config.CorrectChatConfig;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/** Lightweight Twitch IRC client used by the Twitch chat tab. */
public final class TwitchChatService {
    private static final URI TWITCH_IRC = URI.create("wss://irc-ws.chat.twitch.tv:443");
    private static final int MAX_MESSAGE_LENGTH = 450;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();
    private final AtomicLong generation = new AtomicLong();
    private volatile WebSocket socket;
    private volatile State state = State.DISCONNECTED;
    private volatile String activeChannel = "";
    private volatile int activeCredentialsHash;
    private volatile boolean stopping;

    public void register() {
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> disconnect());
        onConfigChanged();
    }

    public void onConfigChanged() {
        CorrectChatConfig config = CorrectChatClient.config();
        String wantedChannel = config == null ? "" : config.twitchChannel;
        int wantedCredentials = config == null ? 0 : Objects.hash(config.twitchUsername, config.twitchOauthToken);
        boolean shouldConnect = config != null && config.enabled && config.twitchEnabled && !wantedChannel.isBlank();
        if (!shouldConnect) {
            closeSocket(false);
            return;
        }
        if (socket != null && activeChannel.equals(wantedChannel)
                && activeCredentialsHash == wantedCredentials && state != State.FAILED) return;
        connect();
    }

    public String statusText() {
        return switch (state) {
            case DISCONNECTED -> "getrennt";
            case CONNECTING -> "Verbindung wird aufgebaut …";
            case CONNECTED -> "verbunden mit #" + activeChannel;
            case FAILED -> "Verbindung fehlgeschlagen";
        };
    }

    public void sendMessage(String message) {
        CorrectChatConfig config = CorrectChatClient.config();
        if (config == null || !config.twitchEnabled || config.twitchChannel.isBlank()) {
            statusMessage("Kein Twitch-Kanal konfiguriert.", ChatFormatting.RED);
            return;
        }
        if (config.twitchUsername.isBlank() || config.twitchOauthToken.isBlank()) {
            statusMessage("Zum Schreiben Twitch-Benutzer und OAuth-Token in den Einstellungen eintragen.", ChatFormatting.RED);
            return;
        }
        WebSocket current = socket;
        if (current == null || state != State.CONNECTED) {
            statusMessage("Twitch ist noch nicht verbunden.", ChatFormatting.RED);
            onConfigChanged();
            return;
        }
        String clean = message == null ? "" : message.replace('\r', ' ').replace('\n', ' ').trim();
        if (clean.isEmpty()) return;
        if (clean.length() > MAX_MESSAGE_LENGTH) clean = clean.substring(0, MAX_MESSAGE_LENGTH);
        sendLine(current, "PRIVMSG #" + activeChannel + " :" + clean);
    }

    private synchronized void connect() {
        closeSocket(false);
        CorrectChatConfig config = CorrectChatClient.config();
        if (config == null || !config.enabled || !config.twitchEnabled || config.twitchChannel.isBlank()) return;

        stopping = false;
        long attempt = generation.incrementAndGet();
        activeChannel = config.twitchChannel;
        activeCredentialsHash = Objects.hash(config.twitchUsername, config.twitchOauthToken);
        state = State.CONNECTING;
        String username = config.twitchUsername;
        String token = config.twitchOauthToken;
        boolean authenticated = !username.isBlank() && !token.isBlank();
        String nickname = authenticated ? username : "justinfan" + (10000 + Math.abs(System.nanoTime() % 80000));
        String password = authenticated ? "oauth:" + token : "SCHMOOPIIE";

        httpClient.newWebSocketBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .buildAsync(TWITCH_IRC, new IrcListener(attempt, activeChannel, nickname, password))
                .whenComplete((connected, error) -> {
                    if (generation.get() != attempt) {
                        if (connected != null) connected.abort();
                        return;
                    }
                    if (error != null) {
                        socket = null;
                        state = State.FAILED;
                        CorrectChatClient.LOGGER.warn("Could not connect to Twitch chat", error);
                        statusMessage("Verbindung fehlgeschlagen: " + safeError(error), ChatFormatting.RED);
                        scheduleReconnect(attempt);
                    } else {
                        socket = connected;
                    }
                });
    }

    private synchronized void disconnect() {
        stopping = true;
        closeSocket(true);
    }

    private synchronized void closeSocket(boolean permanent) {
        generation.incrementAndGet();
        WebSocket old = socket;
        socket = null;
        state = State.DISCONNECTED;
        activeChannel = "";
        activeCredentialsHash = 0;
        if (permanent) stopping = true;
        if (old != null) {
            try { old.sendClose(WebSocket.NORMAL_CLOSURE, "configuration changed"); }
            catch (RuntimeException ignored) { old.abort(); }
        }
    }

    private void scheduleReconnect(long failedGeneration) {
        CompletableFuture.delayedExecutor(5, TimeUnit.SECONDS).execute(() -> {
            if (!stopping && generation.get() == failedGeneration) connect();
        });
    }

    private void handleLine(long attempt, WebSocket webSocket, String line) {
        if (generation.get() != attempt || line.isBlank()) return;
        if (line.startsWith("PING")) {
            sendLine(webSocket, "PONG" + line.substring(4));
            return;
        }
        IrcMessage irc = IrcMessage.parse(line);
        if (irc == null) return;
        switch (irc.command) {
            case "001", "GLOBALUSERSTATE", "ROOMSTATE" -> {
                if (state != State.CONNECTED) {
                    state = State.CONNECTED;
                    statusMessage("Verbunden mit #" + activeChannel + ".", ChatFormatting.GREEN);
                }
            }
            case "PRIVMSG" -> {
                if (!irc.channel.equalsIgnoreCase(activeChannel)) return;
                String displayName = irc.tags.getOrDefault("display-name", irc.user);
                if (displayName.isBlank()) displayName = irc.user;
                chatMessage(displayName, irc.trailing);
            }
            case "NOTICE" -> {
                if (!irc.trailing.isBlank()) statusMessage(irc.trailing, ChatFormatting.YELLOW);
            }
            case "RECONNECT" -> {
                state = State.CONNECTING;
                webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "server reconnect");
            }
            default -> { }
        }
    }

    private void chatMessage(String username, String message) {
        MutableComponent component = Localizer.literal("[Twitch] ").withStyle(ChatFormatting.DARK_PURPLE)
                .append(Component.literal(username).withStyle(ChatFormatting.LIGHT_PURPLE))
                .append(Localizer.literal(": ").withStyle(ChatFormatting.DARK_GRAY))
                .append(Component.literal(message).withStyle(ChatFormatting.WHITE));
        emit(component, true);
    }

    private void statusMessage(String message, ChatFormatting color) {
        MutableComponent component = Localizer.literal("[Twitch] ").withStyle(ChatFormatting.DARK_PURPLE)
                .append(Component.literal(message).withStyle(color));
        emit(component, false);
    }

    private void emit(Component component, boolean processMessage) {
        Minecraft client = Minecraft.getInstance();
        client.execute(() -> {
            if (client.gui == null || client.gui.hud == null || CorrectChatClient.workspace() == null) return;
            if (processMessage && CorrectChatClient.engine() != null) {
                Component processed = CorrectChatClient.engine().processTwitch(component);
                if (processed != null) client.gui.hud.getChat().addClientSystemMessage(processed);
            } else {
                CorrectChatClient.workspace().record(component, ChatWorkspace.Kind.TWITCH);
                client.gui.hud.getChat().addClientSystemMessage(component);
            }
        });
    }

    private static void sendLine(WebSocket target, String line) {
        target.sendText(line + "\r\n", true);
    }

    private static String safeError(Throwable error) {
        Throwable cause = error;
        while (cause.getCause() != null) cause = cause.getCause();
        String message = cause.getMessage();
        return message == null || message.isBlank() ? cause.getClass().getSimpleName() : message;
    }

    private enum State { DISCONNECTED, CONNECTING, CONNECTED, FAILED }

    private final class IrcListener implements WebSocket.Listener {
        private final long attempt;
        private final String channel;
        private final String nickname;
        private final String password;
        private final StringBuilder incoming = new StringBuilder();

        private IrcListener(long attempt, String channel, String nickname, String password) {
            this.attempt = attempt;
            this.channel = channel;
            this.nickname = nickname;
            this.password = password;
        }

        @Override public void onOpen(WebSocket webSocket) {
            WebSocket.Listener.super.onOpen(webSocket);
            if (generation.get() != attempt) {
                webSocket.abort();
                return;
            }
            socket = webSocket;
            sendLine(webSocket, "CAP REQ :twitch.tv/tags twitch.tv/commands");
            sendLine(webSocket, "PASS " + password);
            sendLine(webSocket, "NICK " + nickname);
            sendLine(webSocket, "JOIN #" + channel);
        }

        @Override public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            incoming.append(data);
            if (last) {
                String payload = incoming.toString();
                incoming.setLength(0);
                for (String line : payload.split("\\r?\\n")) handleLine(attempt, webSocket, line);
            }
            webSocket.request(1);
            return null;
        }

        @Override public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            if (generation.get() == attempt && !stopping) {
                socket = null;
                state = State.FAILED;
                scheduleReconnect(attempt);
            }
            return WebSocket.Listener.super.onClose(webSocket, statusCode, reason);
        }

        @Override public void onError(WebSocket webSocket, Throwable error) {
            if (generation.get() != attempt || stopping) return;
            socket = null;
            state = State.FAILED;
            CorrectChatClient.LOGGER.warn("Twitch chat connection failed", error);
            statusMessage("Verbindung unterbrochen: " + safeError(error), ChatFormatting.RED);
            scheduleReconnect(attempt);
        }
    }

    private record IrcMessage(Map<String, String> tags, String command, String channel, String user, String trailing) {
        private static IrcMessage parse(String raw) {
            int cursor = 0;
            Map<String, String> tags = new HashMap<>();
            if (raw.startsWith("@")) {
                int end = raw.indexOf(' ');
                if (end < 0) return null;
                for (String tag : raw.substring(1, end).split(";")) {
                    int equals = tag.indexOf('=');
                    if (equals < 0) tags.put(tag, "");
                    else tags.put(tag.substring(0, equals), unescapeTag(tag.substring(equals + 1)));
                }
                cursor = end + 1;
            }

            String prefix = "";
            if (cursor < raw.length() && raw.charAt(cursor) == ':') {
                int end = raw.indexOf(' ', cursor);
                if (end < 0) return null;
                prefix = raw.substring(cursor + 1, end);
                cursor = end + 1;
            }

            int trailingAt = raw.indexOf(" :", cursor);
            String middle = trailingAt < 0 ? raw.substring(cursor) : raw.substring(cursor, trailingAt);
            String trailing = trailingAt < 0 ? "" : raw.substring(trailingAt + 2);
            String[] parts = middle.trim().split(" +");
            if (parts.length == 0 || parts[0].isBlank()) return null;
            String command = parts[0];
            String channel = parts.length > 1 ? parts[1].replaceFirst("^#", "") : "";
            int bang = prefix.indexOf('!');
            String user = bang < 0 ? prefix : prefix.substring(0, bang);
            return new IrcMessage(tags, command, channel, user, trailing);
        }

        private static String unescapeTag(String value) {
            StringBuilder result = new StringBuilder(value.length());
            boolean escaped = false;
            for (int i = 0; i < value.length(); i++) {
                char current = value.charAt(i);
                if (!escaped && current == '\\') {
                    escaped = true;
                    continue;
                }
                if (escaped) {
                    result.append(switch (current) {
                        case 's' -> ' ';
                        case ':' -> ';';
                        case 'r' -> '\r';
                        case 'n' -> '\n';
                        default -> current;
                    });
                    escaped = false;
                } else result.append(current);
            }
            if (escaped) result.append('\\');
            return result.toString();
        }
    }
}
