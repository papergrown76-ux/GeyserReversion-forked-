package oxy.reversion.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;

import java.util.List;

public final class Config {
    public Config() {}
    public Config(FallbackConfig fallbacks) { this.fallbacks = fallbacks; }
    @JsonProperty("debug-mode")
    private boolean debugMode;
    @JsonProperty("blocked-protocols")
    private List<Integer> blockProtocols = List.of();
    @JsonProperty("min-protocol-id")
    private int minProtocolId = -1;
    @JsonProperty("min-protocol-kick")
    private String minProtocolKick;
    @JsonProperty("version-not-supported-kick")
    private String versionNotSupportedKick;
    @JsonProperty("block-protocol-kick")
    private String blockedProtocolKick;
    @JsonProperty("custom-blocks-not-supported-kick")
    private String customBlocksNotSupportedKick = "This server uses custom blocks. Please use Minecraft Bedrock 1.16.100 or newer.";
    @JsonProperty("timeout-seconds")
    @JsonSetter(nulls = Nulls.SKIP)
    private int timeoutSeconds = 120;
    @JsonProperty("diagnostics-log-interval-seconds")
    private int diagnosticsLogIntervalSeconds = 30;
    @JsonProperty("allow-untested-geyser")
    private boolean allowUntestedGeyser;
    @JsonProperty("custom-block-fallbacks")
    @JsonSetter(nulls = Nulls.SKIP)
    private FallbackConfig fallbacks = new FallbackConfig();

    public int diagnosticsLogIntervalSeconds() { return diagnosticsLogIntervalSeconds; }
    public boolean allowUntestedGeyser() { return allowUntestedGeyser; }
    public FallbackConfig fallbacks() { return fallbacks; }

    public void validate() {
        if (diagnosticsLogIntervalSeconds < 1 || diagnosticsLogIntervalSeconds > 3600) {
            throw new IllegalArgumentException("diagnostics-log-interval-seconds must be between 1 and 3600");
        }
        fallbacks.validate();
    }

    public boolean debugMode() {
        return debugMode;
    }

    public List<Integer> blockProtocols() {
        return blockProtocols;
    }

    public int minProtocolId() {
        return minProtocolId;
    }

    public String minProtocolKick() {
        return minProtocolKick;
    }

    public String versionNotSupportedKick() {
        return versionNotSupportedKick;
    }

    public String blockedProtocolKick() {
        return blockedProtocolKick;
    }

    public String customBlocksNotSupportedKick() {
        return customBlocksNotSupportedKick;
    }

    public int timeoutSeconds() {
        return timeoutSeconds;
    }
}
