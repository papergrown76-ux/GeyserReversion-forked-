package oxy.reversion.util.protocol;

import org.cloudburstmc.protocol.bedrock.codec.v944.Bedrock_v944;
import org.cloudburstmc.protocol.bedrock.data.definitions.BlockDefinition;
import org.cloudburstmc.protocol.common.DefinitionRegistry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CodecIsolationTest {
    @Test
    void rebuiltCodecCreatesIndependentHelpers() {
        var codec = CodecUtil.rebuildCodec(Bedrock_v944.CODEC);
        assertNotSame(codec.createHelper(), codec.createHelper());
    }

    @Test
    void rebuiltOuranosCodecCreatesIndependentHelpers() {
        var codec = CodecUtil.rebuildCodec(com.github.blackjack200.ouranos.shaded.protocol.bedrock.codec.v944.Bedrock_v944.CODEC);
        assertNotSame(codec.createHelper(), codec.createHelper());
    }

    @Test
    void registeredCodecsDoNotShareConnectionRegistries() {
        var codec = ProtocolSet.getPacketCodec(944);
        var first = codec.createHelper();
        var second = codec.createHelper();
        var original = second.getBlockDefinitions();
        first.setBlockDefinitions(new DefinitionRegistry<>() {
            public BlockDefinition getDefinition(int id) { return () -> id; }
            public boolean isRegistered(BlockDefinition definition) { return true; }
        });
        assertNotSame(first, second);
        assertSame(original, second.getBlockDefinitions());
    }

    @Test
    void registersBoth26_2And26_20ProtocolFamilies() {
        assertEquals(924, ProtocolSet.getPacketCodec(924).getProtocolVersion());
        assertEquals(975, ProtocolSet.getPacketCodec(975).getProtocolVersion());
        assertNull(ProtocolSet.getPacketCodec(Integer.MAX_VALUE));
    }
}
