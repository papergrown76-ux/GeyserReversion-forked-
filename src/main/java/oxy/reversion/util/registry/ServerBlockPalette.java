package oxy.reversion.util.registry;

import com.github.blackjack200.ouranos.converter.BlockStateDictionary;
import com.github.blackjack200.ouranos.shaded.allymc.updater.block.BlockStateUpdaters;
import com.github.blackjack200.ouranos.shaded.fastutil.ints.Int2ObjectRBTreeMap;
import com.github.blackjack200.ouranos.utils.HashUtils;
import org.geysermc.geyser.registry.type.BlockMappings;

/** Uses the host's actual IDs, including the exact ordering of custom permutations. */
public final class ServerBlockPalette {
    private ServerBlockPalette() {
    }

    public static BlockStateDictionary.Dictionary fromMappings(BlockMappings mappings) {
        var entries = new Int2ObjectRBTreeMap<BlockStateDictionary.Dictionary.BlockEntry>();
        for (var block : mappings.getBedrockRuntimeMap()) {
            var raw = block.getState();
            int currentHash = HashUtils.computeBlockStateHash(raw);
            // Geyser removes the NBT schema version. Re-upgrading that NBT would treat
            // modern states as legacy ones and can collapse distinct block variants.
            int latestHash = HashUtils.computeBlockStateHash(BlockStateUpgradeUtil.upgrade(raw, BlockStateUpdaters.LATEST_VERSION));
            entries.put(block.getRuntimeId(), new BlockStateDictionary.Dictionary.BlockEntry(
                    raw.getString("name"), raw, latestHash, currentHash));
        }
        return new BlockStateDictionary.Dictionary(entries);
    }
}
