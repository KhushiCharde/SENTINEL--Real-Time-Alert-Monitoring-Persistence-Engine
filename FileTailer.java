package sentinel;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Objects;
import java.util.function.Consumer;

/** Polls complete UTF-8 lines, retaining partial lines between polls. */
public final class FileTailer {
    private FileTailer() {}
    public static void follow(Path path, boolean fromEnd, Consumer<String> consumer, Runnable dashboard) throws Exception {
        RandomAccessFile file = null;
        Object identity = null;
        ByteArrayOutputStream pending = new ByteArrayOutputStream();
        boolean oversized = false, opened = false;
        boolean skipInitial = fromEnd && Files.exists(path);
        long lastDashboard = System.nanoTime();
        try {
            while (!Thread.currentThread().isInterrupted()) {
                if (Files.exists(path)) {
                    BasicFileAttributes attrs = Files.readAttributes(path, BasicFileAttributes.class);
                    if (file == null || !Objects.equals(identity, attrs.fileKey()) || attrs.size() < file.getFilePointer()) {
                        if (file != null) file.close();
                        file = new RandomAccessFile(path.toFile(), "r"); identity = attrs.fileKey();
                        pending.reset(); oversized = false;
                        if (!opened && skipInitial) file.seek(file.length());
                        opened = true;
                    }
                    int b;
                    while ((b = file.read()) != -1) {
                        if (b == '\n') {
                            if (oversized) consumer.accept("INVALID_OVERSIZED_LINE");
                            else consumer.accept(pending.toString(StandardCharsets.UTF_8).replaceFirst("\r$", ""));
                            pending.reset(); oversized = false;
                        } else if (!oversized) {
                            if (pending.size() >= 65536) { oversized = true; pending.reset(); }
                            else pending.write(b);
                        }
                        if (System.nanoTime() - lastDashboard >= 5_000_000_000L) {
                            dashboard.run(); lastDashboard = System.nanoTime();
                        }
                    }
                }
                if (System.nanoTime() - lastDashboard >= 5_000_000_000L) {
                    dashboard.run(); lastDashboard = System.nanoTime();
                }
                Thread.sleep(250);
            }
        } finally { if (file != null) file.close(); }
    }
}
