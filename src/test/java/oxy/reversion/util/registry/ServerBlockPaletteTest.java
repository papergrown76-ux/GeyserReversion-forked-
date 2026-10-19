package oxy.reversion.util.registry;

import com.github.blackjack200.ouranos.utils.HashUtils;
import org.cloudburstmc.nbt.NbtMap;
import org.geysermc.geyser.registry.type.BlockMappings;
import org.geysermc.geyser.registry.type.GeyserBedrockBlock;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ServerBlockPaletteTest {
    @Test
    void keepsHostIdsAndDistinctModernBlockStatesWithoutSchemaVersions() {
        NbtMap south = NbtMap.builder().putString("name", "minecraft:campfire")
                .putCompound("states", NbtMap.builder().putByte("extinguished", (byte) 0)
                        .putString("minecraft:cardinal_direction", "south").build()).build();
        NbtMap east = south.toBuilder().putCompound("states", south.getCompound("states").toBuilder()
                .putString("minecraft:cardinal_direction", "east").build()).build();
        NbtMap custom = NbtMap.builder().putString("name", "example:custom")
                .putCompound("states", NbtMap.builder().putInt("variant", 0).build()).build();
        var host = new GeyserBedrockBlock[] {
                new GeyserBedrockBlock(0, NbtMap.builder().putString("name", "minecraft:info_update")
                        .putCompound("states", NbtMap.EMPTY).build()),
                new GeyserBedrockBlock(1, custom),
                new GeyserBedrockBlock(2, south),
                new GeyserBedrockBlock(3, east)
        };
        var mappings = BlockMappings.builder().bedrockRuntimeMap(host).build();
        var dictionary = ServerBlockPalette.fromMappings(mappings);
        for (int id = 0; id < host.length; id++) {
            var state = dictionary.toBlockState(id);
            assertEquals(host[id].getState(), state.rawState());
            assertEquals(HashUtils.computeBlockStateHash(host[id].getState()), state.currentStateHash());
            assertEquals(id, dictionary.toRuntimeId(state.latestStateHash()));
        }
        assertNotEquals(dictionary.toLatestStateHash(2), dictionary.toLatestStateHash(3));
    }
}
