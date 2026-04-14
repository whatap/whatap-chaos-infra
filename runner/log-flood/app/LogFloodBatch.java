import java.io.*;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Random;
import java.util.UUID;

/**
 * Buggy Batch Process v2.1.0 - Log Flood Bug
 *
 * Simulates a batch processing application that has a bug introduced in a new release.
 * The bug: debug logging was accidentally left enabled in production, and the log rotation
 * configuration was removed during a config file refactor. Each batch iteration creates
 * a new log file instead of appending to a single rotated log.
 *
 * Result: thousands of log files rapidly fill the root partition.
 */
public class LogFloodBatch {

    private static final String LOG_DIR = "/var/log/batch";
    private static final DateTimeFormatter FILE_TS = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss_SSS");
    private static final DateTimeFormatter LOG_TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
    private static final Random RANDOM = new Random();

    // Configurable via environment variables
    private static int fileSizeKb;
    private static int intervalMs;
    private static int filesPerIteration;

    public static void main(String[] args) throws Exception {
        fileSizeKb = Integer.parseInt(System.getenv().getOrDefault("LOG_FILE_SIZE_KB", "512"));
        intervalMs = Integer.parseInt(System.getenv().getOrDefault("LOG_INTERVAL_MS", "200"));
        filesPerIteration = Integer.parseInt(System.getenv().getOrDefault("LOG_FILES_PER_ITERATION", "3"));

        Path logDir = Paths.get(LOG_DIR);
        Files.createDirectories(logDir);

        System.out.println("==============================================");
        System.out.println(" Batch Processor v2.1.0 Starting...");
        System.out.println("==============================================");
        System.out.println(" Log directory : " + LOG_DIR);
        System.out.println(" File size     : ~" + fileSizeKb + " KB each");
        System.out.println(" Interval      : " + intervalMs + " ms");
        System.out.println(" Files/iter    : " + filesPerIteration);
        System.out.println("==============================================");
        System.out.println();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("[SHUTDOWN] Batch Processor stopping...");
            printStats(logDir);
        }));

        int iteration = 0;
        while (true) {
            iteration++;
            processBatch(logDir, iteration);
            Thread.sleep(intervalMs);

            if (iteration % 50 == 0) {
                printStats(logDir);
            }
        }
    }

    /**
     * Simulates a batch processing iteration.
     * The "bug" is that each iteration creates new log files instead of
     * appending to a single managed log file.
     */
    private static void processBatch(Path logDir, int iteration) throws IOException {
        String timestamp = LocalDateTime.now().format(FILE_TS);

        for (int f = 0; f < filesPerIteration; f++) {
            // Bug: creates a brand new file every time instead of rotating
            String fileName = String.format("batch_%s_%04d_%02d.log", timestamp, iteration, f);
            Path logFile = logDir.resolve(fileName);

            try (BufferedWriter writer = Files.newBufferedWriter(logFile)) {
                // Write realistic-looking batch log content
                int targetBytes = fileSizeKb * 1024;
                int written = 0;

                writer.write(logHeader(iteration));
                written += 200;

                int recordNum = 0;
                while (written < targetBytes) {
                    String line = generateLogLine(iteration, ++recordNum);
                    writer.write(line);
                    written += line.length();
                }

                writer.write(logFooter(iteration, recordNum));
            }
        }
    }

    private static String logHeader(int iteration) {
        StringBuilder sb = new StringBuilder();
        String ts = LocalDateTime.now().format(LOG_TS);
        sb.append("================================================================================\n");
        sb.append(String.format("[%s] [INFO ] BatchProcessor - Starting batch iteration #%d%n", ts, iteration));
        sb.append(String.format("[%s] [DEBUG] BatchProcessor - Transaction ID: %s%n", ts, UUID.randomUUID()));
        sb.append(String.format("[%s] [DEBUG] BatchProcessor - Heap memory: %d MB / %d MB%n", ts,
                Runtime.getRuntime().totalMemory() / (1024 * 1024),
                Runtime.getRuntime().maxMemory() / (1024 * 1024)));
        sb.append("================================================================================\n");
        return sb.toString();
    }

    private static String generateLogLine(int iteration, int recordNum) {
        String ts = LocalDateTime.now().format(LOG_TS);
        int type = RANDOM.nextInt(100);

        if (type < 50) {
            // DEBUG: verbose record processing (the bug - debug should be off in prod)
            return String.format("[%s] [DEBUG] RecordProcessor - Processing record #%d in batch #%d | " +
                    "payload_hash=%s | source_table=customer_events | partition=%d | offset=%d | " +
                    "schema_version=3.2 | record_size=%d bytes | encoding=UTF-8 | " +
                    "validation_status=PASS | transform_applied=[normalize,deduplicate,enrich] | " +
                    "enrichment_source=customer_profile_cache | cache_hit=%b%n",
                    ts, recordNum, iteration,
                    UUID.randomUUID().toString().substring(0, 8),
                    RANDOM.nextInt(64), RANDOM.nextInt(1000000),
                    RANDOM.nextInt(4096) + 256,
                    RANDOM.nextBoolean());
        } else if (type < 75) {
            // DEBUG: SQL trace (should never be in prod)
            return String.format("[%s] [DEBUG] SQLTracer - Executing query: SELECT * FROM events WHERE " +
                    "batch_id = %d AND record_seq = %d AND status IN ('PENDING','RETRY') " +
                    "ORDER BY created_at DESC LIMIT 100 | elapsed=%dms | rows_returned=%d | " +
                    "connection_pool_active=%d | connection_pool_idle=%d%n",
                    ts, iteration, recordNum,
                    RANDOM.nextInt(50) + 1, RANDOM.nextInt(100),
                    RANDOM.nextInt(20) + 1, RANDOM.nextInt(10));
        } else if (type < 90) {
            // INFO: normal processing
            return String.format("[%s] [INFO ] BatchProcessor - Record %d processed successfully | " +
                    "duration=%dms | output_partition=%d%n",
                    ts, recordNum, RANDOM.nextInt(100) + 5, RANDOM.nextInt(16));
        } else if (type < 98) {
            // WARN: retryable issues
            return String.format("[%s] [WARN ] RetryHandler - Transient error for record %d, " +
                    "attempt %d/3 | error=ConnectionTimeout | target=analytics-sink | " +
                    "backoff=%dms%n",
                    ts, recordNum, RANDOM.nextInt(2) + 1, RANDOM.nextInt(1000) + 500);
        } else {
            // ERROR: occasional errors with stack trace snippet
            return String.format("[%s] [ERROR] BatchProcessor - Failed to process record %d | " +
                    "error=DataFormatException: Unexpected field type at column 'amount' | " +
                    "expected=DECIMAL, actual=VARCHAR | value='N/A' | " +
                    "stacktrace=com.chaos.batch.processor.RecordTransformer.transform(RecordTransformer.java:%d) " +
                    "-> com.chaos.batch.processor.BatchExecutor.execute(BatchExecutor.java:%d)%n",
                    ts, recordNum, RANDOM.nextInt(200) + 50, RANDOM.nextInt(300) + 100);
        }
    }

    private static String logFooter(int iteration, int totalRecords) {
        String ts = LocalDateTime.now().format(LOG_TS);
        StringBuilder sb = new StringBuilder();
        sb.append("--------------------------------------------------------------------------------\n");
        sb.append(String.format("[%s] [INFO ] BatchProcessor - Batch #%d completed: %d records processed%n",
                ts, iteration, totalRecords));
        sb.append(String.format("[%s] [DEBUG] GCMonitor - Post-batch GC stats: " +
                "young_gen_count=%d, old_gen_count=%d, total_pause=%dms%n",
                ts, RANDOM.nextInt(10), RANDOM.nextInt(3), RANDOM.nextInt(200)));
        sb.append("================================================================================\n\n");
        return sb.toString();
    }

    private static void printStats(Path logDir) {
        try {
            long fileCount = Files.list(logDir).count();
            long totalSize = Files.walk(logDir)
                    .filter(Files::isRegularFile)
                    .mapToLong(p -> {
                        try { return Files.size(p); } catch (IOException e) { return 0; }
                    })
                    .sum();
            System.out.printf("[STATS] Log files: %d | Total size: %.1f MB | Dir: %s%n",
                    fileCount, totalSize / (1024.0 * 1024.0), logDir);
        } catch (IOException e) {
            System.err.println("[STATS] Error reading stats: " + e.getMessage());
        }
    }
}
