package oxy.reversion.diagnostics;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

/** Bounded, per-connection diagnostics. Packet contents and exception messages are never logged. */
public final class TranslationDiagnostics {
    private final boolean debug;
    private final long intervalNanos;
    private final Consumer<String> logger;
    private final LongSupplier clock;
    private final Map<String, Long> lastLog = new HashMap<>();
    private long errors, dropped, replacements;
    private String lastError = "none";

    public TranslationDiagnostics(boolean debug, int intervalSeconds, Consumer<String> logger) {
        this(debug, intervalSeconds, logger, System::nanoTime);
    }
    public TranslationDiagnostics(boolean debug, int intervalSeconds, Consumer<String> logger, LongSupplier clock) {
        this.debug = debug;
        this.intervalNanos = Math.max(1, intervalSeconds) * 1_000_000_000L;
        this.logger = logger;
        this.clock = clock;
    }
    public synchronized void failure(String stage, String packet, int client, int server, String block, Throwable failure) {
        errors++;
        lastError = "stage=" + stage + " packet=" + packet + " client=" + client + " server=" + server
                + " " + block + " exception=" + failure.getClass().getSimpleName();
        if (failure.getStackTrace().length > 0) lastError += " at=" + failure.getStackTrace()[0];
        log("error:" + stage + ":" + packet, lastError);
    }
    public synchronized void dropped(String direction, String packet, int client, int server) {
        dropped++;
        log("drop:" + direction + ":" + packet, "packet=" + packet + " direction=" + direction
                + " client=" + client + " server=" + server + " omitted by protocol translator");
    }
    public synchronized void replacement() { replacements++; }
    public synchronized Snapshot snapshot() { return new Snapshot(errors, dropped, replacements, lastError); }
    private void log(String key, String message) {
        if (!debug) return;
        long now = clock.getAsLong();
        Long previous = lastLog.get(key);
        if (previous != null && now - previous < intervalNanos) return;
        if (previous == null && lastLog.size() >= 64) return;
        lastLog.put(key, now);
        logger.accept("[Reversion diagnostic] " + message);
    }
    public record Snapshot(long errors, long dropped, long replacements, String lastError) {}
}
