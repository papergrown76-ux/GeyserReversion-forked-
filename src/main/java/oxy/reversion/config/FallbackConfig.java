package oxy.reversion.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import java.util.Map;

public final class FallbackConfig {
    public FallbackConfig() {}
    public FallbackConfig(boolean enabled, int minimumClientProtocol, String defaultBlock) {
        this.enabled = enabled; this.minimumClientProtocol = minimumClientProtocol; this.defaultBlock = defaultBlock;
        validate();
    }
    @JsonProperty("enabled") private boolean enabled;
    @JsonProperty("minimum-client-protocol") private int minimumClientProtocol = 419;
    @JsonProperty("default-block") private String defaultBlock = "minecraft:stone";
    @JsonProperty("blocks") @JsonSetter(nulls = Nulls.SKIP)
    private Map<String, Rule> blocks = Map.of();

    public boolean enabled() { return enabled; }
    public Map<String, Rule> blocks() { return blocks; }
    public boolean replaces(String identifier, int clientProtocol) {
        Rule rule = blocks.get(identifier);
        int minimum = rule == null || rule.minimumClientProtocol == null ? minimumClientProtocol : rule.minimumClientProtocol;
        return enabled && clientProtocol < Math.max(419, minimum);
    }
    public String block(String identifier) {
        Rule rule = blocks.get(identifier);
        return rule == null || rule.block == null ? defaultBlock : rule.block;
    }
    public void validate() {
        validateBlock(defaultBlock);
        if (minimumClientProtocol < 0) throw new IllegalArgumentException("Fallback minimum-client-protocol cannot be negative");
        blocks.forEach((name, rule) -> {
            if (name == null || !name.matches("[a-z0-9_]+:[a-z0-9_./-]+") || rule == null) {
                throw new IllegalArgumentException("Invalid custom-block-fallbacks.blocks entry");
            }
            if (rule.block != null) validateBlock(rule.block);
            if (rule.minimumClientProtocol != null && rule.minimumClientProtocol < 0) {
                throw new IllegalArgumentException("Fallback minimum-client-protocol cannot be negative");
            }
        });
    }
    private static void validateBlock(String name) {
        if (name == null || !name.matches("minecraft:[a-z0-9_./-]+")) {
            throw new IllegalArgumentException("Fallback block must be a minecraft: identifier");
        }
    }
    public static final class Rule {
        @JsonProperty("minimum-client-protocol") private Integer minimumClientProtocol;
        @JsonProperty("block") private String block;
    }
}
