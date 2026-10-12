package de.dho.correctchat.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.Reader;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public final class ConfigManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve("correct-chat.json");
    private static final Path BACKUP_PATH = PATH.resolveSibling(PATH.getFileName() + ".bak");
    private static final DateTimeFormatter CORRUPT_TIME = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private ConfigManager() {
    }

    public static CorrectChatConfig load(Logger logger) {
        boolean mainConfigFailed = false;
        if (Files.isRegularFile(PATH)) {
            try {
                return readConfig(PATH);
            } catch (Exception exception) {
                mainConfigFailed = true;
                logger.error("Unable to read {}; attempting backup recovery", PATH, exception);
                archiveCorruptConfig(logger);
                CorrectChatConfig recovered = loadBackup(logger);
                if (recovered != null) return recovered;
            }
        } else {
            CorrectChatConfig recovered = loadBackup(logger);
            if (recovered != null) return recovered;
        }

        CorrectChatConfig migrated = migrateLegacy(logger);
        if (mainConfigFailed) {
            try {
                writeAtomic(migrated, false);
            } catch (IOException exception) {
                logger.error("Unable to replace unreadable configuration {}", PATH, exception);
            }
        } else {
            save(migrated, logger);
        }
        return migrated;
    }

    public static void save(CorrectChatConfig config, Logger logger) {
        config.normalize();
        try {
            writeAtomic(config, true);
        } catch (Exception exception) {
            logger.error("Unable to save {}", PATH, exception);
        }
    }

    public static Path path() {
        return PATH;
    }

    private static CorrectChatConfig readConfig(Path source) throws IOException {
        try (Reader reader = Files.newBufferedReader(source, StandardCharsets.UTF_8)) {
            CorrectChatConfig config = GSON.fromJson(reader, CorrectChatConfig.class);
            if (config == null) throw new IOException("Configuration is empty");
            config.normalize();
            return config;
        } catch (RuntimeException exception) {
            throw new IOException("Configuration is invalid", exception);
        }
    }

    private static CorrectChatConfig loadBackup(Logger logger) {
        if (!Files.isRegularFile(BACKUP_PATH)) return null;
        try {
            CorrectChatConfig recovered = readConfig(BACKUP_PATH);
            writeAtomic(recovered, false);
            logger.warn("Recovered {} from backup {}", PATH, BACKUP_PATH);
            return recovered;
        } catch (Exception exception) {
            logger.error("Unable to recover configuration from {}", BACKUP_PATH, exception);
            return null;
        }
    }

    private static void writeAtomic(CorrectChatConfig config, boolean preserveCurrent) throws IOException {
        Path directory = PATH.getParent();
        Files.createDirectories(directory);
        String serialized = GSON.toJson(config);
        CorrectChatConfig verification = GSON.fromJson(serialized, CorrectChatConfig.class);
        if (verification == null) throw new IOException("Serialized configuration could not be validated");
        verification.normalize();

        Path temporary = Files.createTempFile(directory, "correct-chat-", ".tmp");
        try {
            Files.writeString(temporary, serialized, StandardCharsets.UTF_8,
                    StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
            forceFile(temporary);
            if (preserveCurrent && Files.isRegularFile(PATH)) createBackup();
            moveReplacing(temporary, PATH);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static void createBackup() throws IOException {
        // Never replace a known-good backup with malformed current data.
        readConfig(PATH);
        Path temporaryBackup = Files.createTempFile(PATH.getParent(), "correct-chat-backup-", ".tmp");
        try {
            Files.copy(PATH, temporaryBackup, StandardCopyOption.REPLACE_EXISTING);
            forceFile(temporaryBackup);
            moveReplacing(temporaryBackup, BACKUP_PATH);
        } finally {
            Files.deleteIfExists(temporaryBackup);
        }
    }

    private static void moveReplacing(Path source, Path destination) throws IOException {
        try {
            Files.move(source, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(source, destination, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void forceFile(Path path) throws IOException {
        try (FileChannel channel = FileChannel.open(path, StandardOpenOption.WRITE)) {
            channel.force(true);
        }
    }

    private static void archiveCorruptConfig(Logger logger) {
        if (!Files.exists(PATH)) return;
        Path archived = PATH.resolveSibling(PATH.getFileName() + ".corrupt-" + CORRUPT_TIME.format(LocalDateTime.now()));
        try {
            moveReplacing(PATH, archived);
            logger.warn("Moved unreadable configuration to {}", archived);
        } catch (IOException exception) {
            logger.warn("Unable to archive unreadable configuration {}", PATH, exception);
        }
    }

    private static CorrectChatConfig migrateLegacy(Logger logger) {
        CorrectChatConfig config = new CorrectChatConfig();
        config.configVersion = 1;
        Path root = FabricLoader.getInstance().getGameDir().resolve("labymod-neo/configs");
        boolean found = false;
        found |= read(root.resolve("anticommandchoker/settings.json"), json -> config.antiCommandChoker = bool(json, "enabled", true), logger);
        found |= read(root.resolve("chatlog/settings.json"), json -> config.chatLog = bool(json, "enabled", true), logger);
        found |= read(root.resolve("chattime/settings.json"), json -> {
            config.chatTime = bool(json, "enabled", true);
            config.timeStyle = string(json, "style", config.timeStyle);
            config.timeFormat = string(json, "format", config.timeFormat);
        }, logger);
        found |= read(root.resolve("chatutilities/settings.json"), json -> {
            config.enabled = bool(json, "enabled", true);
            config.copyButton = bool(json, "copy", false);
            config.copyFormat = string(json, "copyFormat", config.copyFormat);
            config.copyTooltip = string(json, "copyTooltip", config.copyTooltip);
            config.textReplacement = bool(json, "textReplacement", true);
            if (json.has("chatListener") && json.get("chatListener").isJsonObject()) {
                JsonObject listener = json.getAsJsonObject("chatListener");
                config.chatListeners = bool(listener, "enabled", true);
                config.antiSpam = bool(listener, "useAntiSpam", false);
                config.maxResponses = integer(listener, "maxFrequency", 2);
                config.responseWindowMinutes = integer(listener, "maxTimeSpan", 5);
            }
        }, logger);
        if (found) logger.info("Imported compatible LabyMod settings into {}", PATH);
        config.normalize();
        return config;
    }

    private static boolean read(Path path, JsonConsumer consumer, Logger logger) {
        if (!Files.isRegularFile(path)) return false;
        try (Reader reader = Files.newBufferedReader(path)) {
            consumer.accept(GSON.fromJson(reader, JsonObject.class));
            return true;
        } catch (Exception exception) {
            logger.warn("Unable to import legacy config {}", path, exception);
            return false;
        }
    }

    private static boolean bool(JsonObject object, String key, boolean fallback) {
        return object.has(key) ? object.get(key).getAsBoolean() : fallback;
    }

    private static int integer(JsonObject object, String key, int fallback) {
        return object.has(key) ? object.get(key).getAsInt() : fallback;
    }

    private static String string(JsonObject object, String key, String fallback) {
        return object.has(key) ? object.get(key).getAsString() : fallback;
    }

    @FunctionalInterface
    private interface JsonConsumer { void accept(JsonObject object); }
}
