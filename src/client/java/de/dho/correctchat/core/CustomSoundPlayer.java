package de.dho.correctchat.core;

import de.dho.correctchat.CorrectChatClient;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.FloatControl;
import javax.sound.sampled.SourceDataLine;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/** Streams user-selected audio through Java Sound without blocking Minecraft's render thread. */
final class CustomSoundPlayer {
    private static final AtomicLong GENERATION = new AtomicLong();
    private static final ThreadPoolExecutor PLAYERS = new ThreadPoolExecutor(2, 2, 0L, TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(16), runnable -> {
        Thread thread = new Thread(runnable, "correctchat-custom-sound");
        thread.setDaemon(true);
        return thread;
    }, new ThreadPoolExecutor.DiscardOldestPolicy());

    private CustomSoundPlayer() { }

    static boolean supports(String file) {
        if (file == null || file.isBlank()) return false;
        try {
            Path path = Path.of(file);
            String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
            return Files.isRegularFile(path)
                    && (name.endsWith(".mp3") || name.endsWith(".wav") || name.endsWith(".ogg"));
        } catch (RuntimeException exception) {
            return false;
        }
    }

    static void play(String file, int volume, float pitch) {
        if (!supports(file)) {
            CorrectChatClient.LOGGER.warn("Custom alarm sound does not exist or is unsupported: {}", file);
            return;
        }
        long generation = GENERATION.get();
        PLAYERS.execute(() -> stream(Path.of(file), volume, pitch, generation));
    }

    static void stopAll() {
        GENERATION.incrementAndGet();
        PLAYERS.getQueue().clear();
    }

    private static void stream(Path path, int volume, float pitch, long generation) {
        SourceDataLine line = null;
        try (AudioInputStream encoded = AudioSystem.getAudioInputStream(path.toFile())) {
            AudioFormat source = encoded.getFormat();
            AudioFormat pcm = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED,
                    source.getSampleRate(), 16, source.getChannels(), source.getChannels() * 2,
                    source.getSampleRate(), false);
            try (AudioInputStream decoded = AudioSystem.getAudioInputStream(pcm, encoded)) {
                float requestedRate = pcm.getSampleRate() * Math.max(0.25F, Math.min(4.0F, pitch));
                AudioFormat output = new AudioFormat(pcm.getEncoding(), requestedRate, pcm.getSampleSizeInBits(),
                        pcm.getChannels(), pcm.getFrameSize(), requestedRate, pcm.isBigEndian());
                try {
                    line = AudioSystem.getSourceDataLine(output);
                    line.open(output);
                } catch (IllegalArgumentException unsupportedRate) {
                    output = pcm;
                    line = AudioSystem.getSourceDataLine(output);
                    line.open(output);
                }
                applyVolume(line, volume);
                line.start();
                byte[] buffer = new byte[16_384];
                int read;
                while (GENERATION.get() == generation && (read = decoded.read(buffer)) >= 0) {
                    if (read > 0) line.write(buffer, 0, read);
                }
                if (GENERATION.get() == generation) line.drain();
            }
        } catch (Exception exception) {
            CorrectChatClient.LOGGER.warn("Could not play custom alarm sound '{}'", path, exception);
        } finally {
            if (line != null) {
                line.stop();
                line.close();
            }
        }
    }

    private static void applyVolume(SourceDataLine line, int volume) {
        if (!line.isControlSupported(FloatControl.Type.MASTER_GAIN)) return;
        FloatControl gain = (FloatControl)line.getControl(FloatControl.Type.MASTER_GAIN);
        float linear = Math.max(0.0001F, Math.min(1.0F, volume / 100.0F));
        float decibels = (float)(20.0D * Math.log10(linear));
        gain.setValue(Math.max(gain.getMinimum(), Math.min(gain.getMaximum(), decibels)));
    }
}
