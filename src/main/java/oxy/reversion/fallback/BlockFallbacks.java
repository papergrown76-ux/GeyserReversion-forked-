package oxy.reversion.fallback;

import com.github.blackjack200.ouranos.converter.BlockStateDictionary;
import com.github.blackjack200.ouranos.converter.ItemTypeDictionary;
import com.github.blackjack200.ouranos.session.storage.BlockDictionaryStorage;
import com.github.blackjack200.ouranos.shaded.protocol.bedrock.data.definitions.SimpleBlockDefinition;
import com.github.blackjack200.ouranos.shaded.protocol.bedrock.data.inventory.ItemData;
import com.github.blackjack200.ouranos.shaded.protocol.bedrock.packet.BedrockPacket;
import com.github.blackjack200.ouranos.shaded.protocol.bedrock.packet.StartGamePacket;
import com.github.blackjack200.ouranos.shaded.protocol.bedrock.packet.ItemComponentPacket;
import org.cloudburstmc.nbt.NbtMap;
import oxy.reversion.config.FallbackConfig;
import oxy.reversion.user.ReversionUser;

import java.util.*;

/** Per-client visual substitutions; original Java block state and drops remain server-authoritative. */
public final class BlockFallbacks {
    public static final String ITEM_TOKEN = "__geyser_reversion_fallback";
    private final ReversionUser user;
    private final FallbackConfig config;
    private final Set<String> blockNames = new HashSet<>();
    private final Map<String, BlockStateDictionary.Dictionary.BlockEntry> resolved = new HashMap<>();
    private final Map<String, ItemView> itemViews = new LinkedHashMap<>();
    private final Map<ItemKey, String> itemTokens = new LinkedHashMap<>(16, 0.75f, true);

    public BlockFallbacks(ReversionUser user, FallbackConfig config) { this.user = user; this.config = config; }
    public void registerBlockNames(Collection<String> names) { blockNames.addAll(names); }
    public boolean replaces(String name) { return blockNames.contains(name) && config.replaces(name, user.getProtocolId()); }

    public void prepare(BedrockPacket packet) {
        if (!config.enabled()) return;
        if (packet instanceof StartGamePacket start) {
            registerBlockNames(start.getBlockProperties().stream().map(p -> p.getName()).toList());
            if (start.getBlockProperties().stream().anyMatch(p -> replaces(p.getName())) && user.getServerBlockDictionary() == null) {
                throw new IllegalStateException("Server block palette is required before enabling custom block fallbacks");
            }
            start.getBlockProperties().removeIf(p -> replaces(p.getName()));
            resolved.clear();
        } else if (packet instanceof ItemComponentPacket items) {
            items.getItems().removeIf(item -> replaces(item.getIdentifier()));
        }
    }

    public Integer translateBlock(int input, int output, int runtimeId) {
        if (!config.enabled() || input != user.getTargetVersion() || output != user.getProtocolId()) return null;
        var storage = user.get(BlockDictionaryStorage.class);
        if (storage == null) return null;
        var server = user.getServerBlockDictionary() == null ? storage.get(input) : user.getServerBlockDictionary();
        var source = user.isHashedBlockIds() ? server.toBlockStateHash(runtimeId) : server.toBlockState(runtimeId);
        if (source == null || !replaces(source.name())) return null;
        var destination = resolve(source.name(), storage.get(output));
        user.getDiagnostics().replacement();
        return user.isHashedBlockIds() && output >= 582 ? destination.currentStateHash()
                : storage.get(output).toRuntimeId(destination.latestStateHash());
    }

    public ItemData translateItem(int input, int output, ItemData item) {
        if (!config.enabled() || item == null || item.isNull() || !item.isValid()) return null;
        if (input == user.getProtocolId() && output == user.getTargetVersion()) {
            String token = item.getTag() == null ? "" : item.getTag().getString(ITEM_TOKEN);
            ItemView view = itemViews.get(token);
            if (view == null || !view.identifier.equals(item.getDefinition().getIdentifier())) return null;
            // Only restore a server-issued snapshot. Never reconstruct custom items from client-supplied NBT.
            return view.original.toBuilder().count(item.getCount()).build();
        }
        if (input != user.getTargetVersion() || output != user.getProtocolId()) return null;
        String blockName = item.getDefinition().getIdentifier();
        var storage = user.get(BlockDictionaryStorage.class);
        if (storage == null) return null;
        if (item.getBlockDefinition() != null) {
            var server = user.getServerBlockDictionary() == null ? storage.get(input) : user.getServerBlockDictionary();
            var state = user.isHashedBlockIds() ? server.toBlockStateHash(item.getBlockDefinition().getRuntimeId())
                    : server.toBlockState(item.getBlockDefinition().getRuntimeId());
            if (state != null) blockName = state.name();
        }
        if (!replaces(blockName)) return null;
        var state = resolve(blockName, storage.get(output));
        var itemType = ItemTypeDictionary.getInstance(output).getEntries().get(state.name());
        if (itemType == null) itemType = ItemTypeDictionary.getInstance(output).getEntries().get("minecraft:stone");
        String identifier = ItemTypeDictionary.getInstance(output).fromIntId(itemType.runtime_id());
        // Reuse a view across inventory refreshes and count changes; otherwise ordinary
        // updates would evict snapshots for items still visible in the inventory.
        var key = new ItemKey(item.toBuilder().count(1).build(), item.getNetId(), item.isUsingNetId());
        String token = itemTokens.get(key);
        if (token == null) {
            token = UUID.randomUUID().toString();
            if (itemTokens.size() >= 4096) {
                var eldest = itemTokens.entrySet().iterator();
                itemViews.remove(eldest.next().getValue());
                eldest.remove();
            }
            itemTokens.put(key, token);
            itemViews.put(token, new ItemView(identifier, item));
        }
        int blockId = user.isHashedBlockIds() && output >= 582 ? state.currentStateHash()
                : storage.get(output).toRuntimeId(state.latestStateHash());
        user.getDiagnostics().replacement();
        return item.toBuilder().definition(itemType.toDefinition(identifier)).damage(0)
                .blockDefinition(new SimpleBlockDefinition(state.name(), blockId, state.rawState()))
                .tag((item.getTag() == null ? NbtMap.builder() : item.getTag().toBuilder()).putString(ITEM_TOKEN, token).build()).build();
    }

    private BlockStateDictionary.Dictionary.BlockEntry resolve(String name, BlockStateDictionary.Dictionary dictionary) {
        return resolved.computeIfAbsent(name, key -> {
            String replacement = config.block(key);
            return dictionary.getKnownStates().stream().filter(s -> s.name().equals(replacement)).findFirst()
                    .orElseGet(() -> dictionary.getKnownStates().stream().filter(s -> s.name().equals("minecraft:stone"))
                            .findFirst().orElseThrow(() -> new IllegalStateException("Client palette lacks minecraft:stone")));
        });
    }
    public int configuredReplacements() { return (int) blockNames.stream().filter(this::replaces).count(); }
    private record ItemView(String identifier, ItemData original) {}
    private record ItemKey(ItemData normalized, int netId, boolean usingNetId) {}
}
