package oxy.reversion.util.registry;

import com.github.blackjack200.ouranos.shaded.allymc.updater.block.BlockStateUpdaters;
import org.cloudburstmc.nbt.NbtMap;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BlockStateUpgradeTest {
    @Test
    void preservesModernDirectionalVariants() {
        for (String direction : new String[] {"north", "south", "east", "west"}) {
            var state = NbtMap.builder().putString("name", "minecraft:campfire")
                    .putCompound("states", NbtMap.builder().putByte("extinguished", (byte) 0)
                            .putString("minecraft:cardinal_direction", direction).build()).build();
            assertSame(state, BlockStateUpgradeUtil.upgrade(state, BlockStateUpdaters.LATEST_VERSION));
        }
    }

    @Test
    void preservesCustomPropertiesWithoutVanillaMigration() {
        var state = NbtMap.builder().putString("name", "example:custom")
                .putCompound("states", NbtMap.builder().putString("facing", "west").putByte("lit", (byte) 1).build()).build();
        assertSame(state, BlockStateUpgradeUtil.upgrade(state, BlockStateUpdaters.LATEST_VERSION));
    }

    @Test
    void stillUpgradesLegacyVanillaStates() {
        var state = NbtMap.builder().putString("name", "minecraft:planks")
                .putCompound("states", NbtMap.builder().putString("wood_type", "oak").build()).build();
        var upgraded = BlockStateUpgradeUtil.upgrade(state, BlockStateUpdaters.LATEST_VERSION);
        assertEquals("minecraft:oak_planks", upgraded.getString("name"));
    }
}
