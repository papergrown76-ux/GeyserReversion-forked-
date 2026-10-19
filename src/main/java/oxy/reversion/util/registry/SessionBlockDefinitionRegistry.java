package oxy.reversion.util.registry;

import com.github.blackjack200.ouranos.converter.BlockStateDictionary;
import com.github.blackjack200.ouranos.session.OuranosSession;
import com.github.blackjack200.ouranos.session.storage.BlockDictionaryStorage;
import com.github.blackjack200.ouranos.shaded.protocol.bedrock.codec.v582.Bedrock_v582;
import com.github.blackjack200.ouranos.shaded.protocol.bedrock.data.definitions.BlockDefinition;
import com.github.blackjack200.ouranos.shaded.protocol.bedrock.data.definitions.SimpleBlockDefinition;
import com.github.blackjack200.ouranos.shaded.protocol.common.DefinitionRegistry;

/** Resolves IDs against the palette announced for this connection, including custom states. */
public record SessionBlockDefinitionRegistry(OuranosSession session, int protocol)
        implements DefinitionRegistry<BlockDefinition> {
    @Override
    public BlockDefinition getDefinition(int runtimeId) {
        BlockDictionaryStorage storage = session.get(BlockDictionaryStorage.class);
        BlockStateDictionary.Dictionary dictionary = storage == null
                ? BlockStateDictionary.getInstance(protocol) : storage.get(protocol);
        boolean hashed = session.isHashedBlockIds() && protocol >= Bedrock_v582.CODEC.getProtocolVersion();
        BlockStateDictionary.Dictionary.BlockEntry state = hashed
                ? dictionary.toBlockStateHash(runtimeId) : dictionary.toBlockState(runtimeId);
        // Unknown IDs must survive decoding so the translator can apply its fallback.
        return state == null ? () -> runtimeId
                : new SimpleBlockDefinition(state.name(), runtimeId, state.rawState());
    }

    @Override
    public boolean isRegistered(BlockDefinition definition) {
        return definition != null;
    }
}
