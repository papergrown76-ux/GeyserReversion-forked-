package oxy.reversion.util.registry;

import com.github.blackjack200.ouranos.shaded.allymc.updater.block.BlockStateUpdaters;
import org.cloudburstmc.nbt.NbtList;
import org.cloudburstmc.nbt.NbtMap;
import org.cloudburstmc.nbt.NbtUtils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

/** Avoids reapplying legacy migration rules to states already present in the bridge palette. */
public final class BlockStateUpgradeUtil {
    private static final Set<NbtMap> CURRENT_STATES = loadCurrentStates();

    private BlockStateUpgradeUtil() {
    }

    public static NbtMap upgrade(NbtMap state, int version) {
        if (!state.getString("name").startsWith("minecraft:") || CURRENT_STATES.contains(key(state))) {
            return state;
        }
        return BlockStateUpdaters.updateBlockState(state, version);
    }

    private static NbtMap key(NbtMap state) {
        return NbtMap.builder().putString("name", state.getString("name"))
                .putCompound("states", state.getCompound("states")).build();
    }

    private static Set<NbtMap> loadCurrentStates() {
        var states = new HashSet<NbtMap>();
        try (var resource = BlockStateUpgradeUtil.class.getClassLoader()
                .getResourceAsStream("vanilla/v944/canonical_block_states.nbt")) {
            if (resource == null) throw new IOException("Bundled protocol 944 block palette is missing");
            try (var bytes = new ByteArrayInputStream(resource.readAllBytes());
                 var reader = NbtUtils.createNetworkReader(bytes)) {
                while (bytes.available() > 0) {
                    var tag = reader.readTag();
                    if (tag instanceof NbtMap state) {
                        states.add(key(state));
                    } else if (tag instanceof NbtList<?> list) {
                        for (var entry : list) states.add(key(((NbtMap) entry).getCompound("block")));
                    } else {
                        throw new IOException("Unexpected block palette tag");
                    }
                }
            }
        } catch (IOException e) {
            throw new ExceptionInInitializerError(e);
        }
        return Set.copyOf(states);
    }
}
