package oxy.reversion.util.registry;

import org.junit.jupiter.api.Test;
import oxy.reversion.compatibility.CompatibilityReport;
import oxy.reversion.diagnostics.StatusReport;
import oxy.reversion.diagnostics.TranslationDiagnostics;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class CompatibilityAndStatusTest {
    @Test
    void distinguishesValidatedBuildFromOtherBuildsAndApiFamilies() {
        assertEquals(CompatibilityReport.State.TESTED, CompatibilityReport.evaluate("2.10.0-b1162", "1162", false).state());
        assertTrue(CompatibilityReport.evaluate("2.10.0-SNAPSHOT", "1200", false).allowed());
        assertEquals(CompatibilityReport.State.UNTESTED_BUILD, CompatibilityReport.evaluate("2.10.0", "1200", false).state());
        assertFalse(CompatibilityReport.evaluate("2.12.0", "1400", false).allowed());
        assertFalse(CompatibilityReport.evaluate("12.10.0", "1162", false).allowed());
        assertFalse(CompatibilityReport.evaluate(null, "unknown", false).allowed());
        assertTrue(CompatibilityReport.evaluate("2.12.0", "1400", true).allowed());
    }
    @Test
    void showsNativeAndTranslatedClientVersionsAndAggregatedCounters() {
        var report = StatusReport.format(CompatibilityReport.evaluate("2.10.0", "1162", false), true, true, 4, List.of(
                new StatusReport.Player("OldClient", 898, "1.21.130", true, new TranslationDiagnostics.Snapshot(3, 2, 7, "encode UPDATE_BLOCK"), 1),
                new StatusReport.Player("NativeClient", 975, "26.20", false, new TranslationDiagnostics.Snapshot(0, 0, 0, "none"), 0)));
        String text = String.join("\n", report);
        assertTrue(text.contains("Players: 2 | Translated: 1 | Native: 1"));
        assertTrue(text.contains("errors=3 omitted=2 replacements=7"));
        assertTrue(text.contains("OldClient: Bedrock 1.21.130 (898) translated"));
        assertTrue(text.contains("NativeClient: Bedrock 26.20 (975) native"));
        assertTrue(text.contains("Custom blocks: 4 | Fallbacks: enabled"));
    }
}
