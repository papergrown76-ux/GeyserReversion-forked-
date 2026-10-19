package oxy.reversion.diagnostics;

import oxy.reversion.compatibility.CompatibilityReport;
import java.util.ArrayList;
import java.util.List;

public final class StatusReport {
    private StatusReport() {}
    public record Player(String name, int protocol, String version, boolean translated,
                         TranslationDiagnostics.Snapshot diagnostics, int replacedBlocks) {}
    public static List<String> format(CompatibilityReport host, boolean ready, boolean fallbacks,
                                      int customBlocks, List<Player> players) {
        var lines = new ArrayList<String>();
        lines.add("[Reversion status] Geyser " + host.version() + " build " + host.build() + " | " + host.state());
        lines.add("Bridge: " + (ready ? "active" : "disabled") + " | " + host.reason());
        lines.add("Custom blocks: " + customBlocks + " | Fallbacks: " + (fallbacks ? "enabled" : "disabled"));
        long translated = players.stream().filter(Player::translated).count();
        lines.add("Players: " + players.size() + " | Translated: " + translated + " | Native: " + (players.size() - translated));
        long errors = 0, dropped = 0, replacements = 0;
        for (Player player : players) {
            errors += player.diagnostics.errors(); dropped += player.diagnostics.dropped(); replacements += player.diagnostics.replacements();
        }
        lines.add("Active-session diagnostics: errors=" + errors + " omitted=" + dropped + " replacements=" + replacements);
        for (Player player : players.stream().limit(20).toList()) {
            lines.add(player.name + ": Bedrock " + player.version + " (" + player.protocol + ") "
                    + (player.translated ? "translated" : "native") + " | errors=" + player.diagnostics.errors()
                    + " fallback-blocks=" + player.replacedBlocks);
            if (player.diagnostics.errors() > 0) lines.add("  Last error: " + player.diagnostics.lastError());
        }
        if (players.size() > 20) lines.add("Showing the first 20 players.");
        return List.copyOf(lines);
    }
}
