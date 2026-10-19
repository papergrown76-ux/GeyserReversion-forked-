package oxy.reversion.util.transformers;

import com.github.blackjack200.ouranos.converter.BlockStateDictionary;
import net.lenni0451.classtransform.annotations.CTransformer;
import net.lenni0451.classtransform.annotations.injection.COverride;
import org.cloudburstmc.nbt.NbtMap;
import oxy.reversion.util.registry.BlockStateUpgradeUtil;

@CTransformer(BlockStateDictionary.class)
public class OuranosBlockStateTransformer {
    @COverride
    public static NbtMap hackedUpgradeBlockState(NbtMap state, int version) {
        return BlockStateUpgradeUtil.upgrade(state, version);
    }
}
