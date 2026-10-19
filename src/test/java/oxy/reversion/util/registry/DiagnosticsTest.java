package oxy.reversion.util.registry;

import org.junit.jupiter.api.Test;
import oxy.reversion.diagnostics.TranslationDiagnostics;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.jupiter.api.Assertions.*;

class DiagnosticsTest {
    @Test
    void countsAllFailuresButRateLimitsLogsAndOmitsPayloads() {
        var logs = new ArrayList<String>();
        var clock = new AtomicLong();
        var diagnostics = new TranslationDiagnostics(true, 30, logs::add, clock::get);
        for (int i = 0; i < 100; i++) diagnostics.failure("encode", "UPDATE_BLOCK", 898, 944, "blockId=42", new IllegalArgumentException("secret-token"));
        assertEquals(100, diagnostics.snapshot().errors());
        assertEquals(1, logs.size());
        assertTrue(logs.getFirst().contains("client=898"));
        assertTrue(logs.getFirst().contains("blockId=42"));
        assertFalse(logs.getFirst().contains("secret-token"));
        clock.set(31_000_000_000L);
        diagnostics.failure("encode", "UPDATE_BLOCK", 898, 944, "", new IllegalArgumentException());
        assertEquals(2, logs.size());
    }
    @Test
    void disabledDebugStillCountsAndSeparatesOmissionsAndReplacements() {
        var logs = new ArrayList<String>();
        var diagnostics = new TranslationDiagnostics(false, 30, logs::add);
        diagnostics.failure("decode", "INVENTORY_TRANSACTION", 898, 944, "", new RuntimeException());
        diagnostics.dropped("clientbound", "LOCATOR_BAR", 898, 944);
        diagnostics.replacement();
        assertTrue(logs.isEmpty());
        assertEquals(1, diagnostics.snapshot().errors());
        assertEquals(1, diagnostics.snapshot().dropped());
        assertEquals(1, diagnostics.snapshot().replacements());
    }
    @Test
    void capsDistinctLogKeysWhileRetainingCounters() {
        var logs = new ArrayList<String>();
        var diagnostics = new TranslationDiagnostics(true, 30, logs::add);
        for (int i = 0; i < 100; i++) diagnostics.failure("test", "TYPE_" + i, 898, 944, "", new RuntimeException());
        assertEquals(64, logs.size());
        assertEquals(100, diagnostics.snapshot().errors());
    }
}
