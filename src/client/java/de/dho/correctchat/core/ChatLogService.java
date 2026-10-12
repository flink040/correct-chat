package de.dho.correctchat.core;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import de.dho.correctchat.CorrectChatClient;
import de.dho.correctchat.config.CorrectChatConfig;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class ChatLogService {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final DateTimeFormatter FILE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss-SSS").withZone(ZoneId.systemDefault());
    private static final DateTimeFormatter LINE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());
    private final Deque<Entry> entries = new ArrayDeque<>();
    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "correct-chat-log");
        thread.setDaemon(true);
        return thread;
    });

    public synchronized void add(String text) {
        CorrectChatConfig config = CorrectChatClient.config();
        if (!config.enabled || !config.chatLog) return;
        Minecraft client = Minecraft.getInstance();
        String server = client.hasSingleplayerServer() ? "singleplayer"
                : client.getCurrentServer() == null ? "" : client.getCurrentServer().ip;
        entries.addLast(new Entry(text, server, System.currentTimeMillis()));
        while (entries.size() > config.maxStoredMessages) entries.removeFirst();
    }

    public synchronized int size() { return entries.size(); }
    public synchronized void clear() { entries.clear(); }

    public void exportOrUpload(Integer requestedAmount) {
        List<Entry> snapshot = snapshot(requestedAmount);
        if (snapshot.isEmpty()) {
            message(Component.literal("§eCorrect Chat: Keine Chatnachrichten gespeichert."));
            return;
        }
        CorrectChatConfig config = CorrectChatClient.config();
        ioExecutor.execute(() -> {
            if (config.uploadEnabled && !config.uploadEndpoint.isBlank()) upload(snapshot, config.uploadEndpoint);
            else export(snapshot, config.chatLogFormat, config.chatLogRetention);
        });
    }

    private synchronized List<Entry> snapshot(Integer requestedAmount) {
        List<Entry> all = new ArrayList<>(entries);
        int amount = requestedAmount == null ? all.size() : Math.max(0, Math.min(requestedAmount, all.size()));
        return new ArrayList<>(all.subList(all.size() - amount, all.size()));
    }

    private void export(List<Entry> snapshot, CorrectChatConfig.ChatLogFormat format, int retention) {
        Path directory = FabricLoader.getInstance().getGameDir().resolve("chatlogs/correct-chat");
        try {
            Files.createDirectories(directory);
            String extension = format == CorrectChatConfig.ChatLogFormat.TEXT ? ".txt" : ".json";
            Path destination = uniqueDestination(directory, extension);
            String content = format == CorrectChatConfig.ChatLogFormat.TEXT ? text(snapshot) : GSON.toJson(snapshot);
            Files.writeString(destination, content, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            try {
                pruneExports(directory, retention);
            } catch (IOException exception) {
                CorrectChatClient.LOGGER.warn("Unable to prune old chat logs", exception);
            }
            Component path = Component.literal(destination.toAbsolutePath().toString())
                    .withStyle(style -> style.withUnderlined(true).withClickEvent(new ClickEvent.OpenFile(destination.toAbsolutePath().toString())));
            message(Component.literal("§aChatlog gespeichert: ").append(path));
        } catch (IOException exception) {
            CorrectChatClient.LOGGER.error("Unable to export chat log", exception);
            message(Component.literal("§cChatlog konnte nicht gespeichert werden."));
        }
    }

    private static Path uniqueDestination(Path directory, String extension) {
        String base = "chatlog_" + FILE_TIME.format(Instant.now());
        Path destination = directory.resolve(base + extension);
        for (int suffix = 2; Files.exists(destination); suffix++) {
            destination = directory.resolve(base + "-" + suffix + extension);
        }
        return destination;
    }

    private static String text(List<Entry> entries) {
        StringBuilder result = new StringBuilder();
        for (Entry entry : entries) {
            result.append('[').append(LINE_TIME.format(Instant.ofEpochMilli(entry.timestamp))).append("] ");
            if (!entry.server.isBlank()) result.append('[').append(entry.server).append("] ");
            result.append(entry.text.replace('\r', ' ').replace('\n', ' ')).append(System.lineSeparator());
        }
        return result.toString();
    }

    private static void pruneExports(Path directory, int retention) throws IOException {
        try (var files = Files.list(directory)) {
            List<Path> exports = files
                    .filter(Files::isRegularFile)
                    .filter(path -> {
                        String name = path.getFileName().toString();
                        return name.startsWith("chatlog_") && (name.endsWith(".json") || name.endsWith(".txt"));
                    })
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .toList();
            int remove = Math.max(0, exports.size() - retention);
            for (int index = 0; index < remove; index++) Files.deleteIfExists(exports.get(index));
        }
    }

    private void upload(List<Entry> snapshot, String endpoint) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint))
                    .header("Content-Type", "application/json; charset=utf-8")
                    .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(snapshot), StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                String id = response.headers().firstValue("chatLogId").orElse("");
                String url = "https://chatlog.hdskins.de/?id=" + id;
                Component link = Component.literal(url).withStyle(style -> style.withUnderlined(true)
                        .withClickEvent(new ClickEvent.OpenUrl(URI.create(url))));
                clear();
                message(Component.literal("§aChatlog hochgeladen: ").append(link));
            } else if (response.statusCode() == 429) {
                message(Component.literal("§cZu viele Uploads. Bitte später erneut versuchen."));
            } else {
                message(Component.literal("§cChatlog-Upload fehlgeschlagen (HTTP " + response.statusCode() + ")."));
            }
        } catch (Exception exception) {
            CorrectChatClient.LOGGER.error("Unable to upload chat log", exception);
            message(Component.literal("§cChatlog-Upload fehlgeschlagen."));
        }
    }

    private static void message(Component component) {
        Minecraft client = Minecraft.getInstance();
        client.execute(() -> client.gui.hud.getChat().addClientSystemMessage(component));
    }

    public record Entry(String text, String server, long timestamp) { }
}
