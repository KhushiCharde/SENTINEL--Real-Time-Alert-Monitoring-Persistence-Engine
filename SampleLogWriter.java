package sentinel;

import java.nio.file.*;
import java.time.Instant;

/** Synthetic producer for a live demo; does not inspect real CPU or memory. */
public final class SampleLogWriter {
    public static void main(String[] args) throws Exception {
        Path file = Path.of(args.length == 0 ? "samples/live.log" : args[0]);
        Files.createDirectories(file.toAbsolutePath().getParent());
        for (int n = 1; n <= 10; n++) {
            Files.writeString(file, Instant.now() + "|web-01|database|ERROR|Database connection pool exhausted\n",
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            System.out.println("Appended synthetic error " + n + "/10");
            Thread.sleep(1000);
        }
    }
}
