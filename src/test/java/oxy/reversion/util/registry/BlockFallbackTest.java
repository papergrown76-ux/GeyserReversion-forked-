package oxy.reversion.util.registry;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.github.blackjack200.ouranos.session.storage.BlockDictionaryStorage;
import com.github.blackjack200.ouranos.shaded.protocol.bedrock.data.BlockPropertyData;
import com.github.blackjack200.ouranos.shaded.protocol.bedrock.data.inventory.ItemData;
import com.github.blackjack200.ouranos.shaded.protocol.bedrock.data.definitions.SimpleItemDefinition;
import com.github.blackjack200.ouranos.shaded.protocol.bedrock.packet.StartGamePacket;
import org.cloudburstmc.nbt.NbtMap;
import org.junit.jupiter.api.Test;
import oxy.reversion.config.Config;
import oxy.reversion.fallback.BlockFallbacks;
import oxy.reversion.user.ReversionUser;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class BlockFallbackTest {
    private static final String CUSTOM = "example:nexo_block";
    private Config config() throws Exception {
        return new ObjectMapper(new YAMLFactory()).readValue("""
                custom-block-fallbacks:
                  enabled: true
                  minimum-client-protocol: 419
                  default-block: minecraft:stone
                  blocks:
                    example:nexo_block:
                      minimum-client-protocol: 924
                      block: minecraft:gold_block
                """, Config.class);
    }
    private ReversionUser user(int client, boolean hashed) throws Exception {
        var original = CustomBlockTranslationTest.user(898, false);
        var user = new ReversionUser(client, 944, null, config());
        user.setServerBlockDictionary(original.get(BlockDictionaryStorage.class).get(944));
        var start = new StartGamePacket();
        start.getBlockProperties().add(new BlockPropertyData(CUSTOM, NbtMap.EMPTY));
        user.getFallbacks().prepare(start);
        user.put(new BlockDictionaryStorage(user, start.getBlockProperties()));
        user.setHashedBlockIds(hashed);
        return user;
    }
    @Test
    void disabledByDefaultAndProtocolRuleKeepsCapableClients() throws Exception {
        assertFalse(new Config().fallbacks().enabled());
        Config config = config(); config.validate();
        assertTrue(config.fallbacks().replaces(CUSTOM, 898));
        assertFalse(config.fallbacks().replaces(CUSTOM, 924));
        assertTrue(config.fallbacks().replaces("other:custom", 361));
        assertFalse(config.fallbacks().replaces("other:custom", 898));
    }
    @Test
    void rejectsInvalidReplacementAndDiagnosticIntervals() throws Exception {
        var mapper = new ObjectMapper(new YAMLFactory());
        assertThrows(IllegalArgumentException.class, () -> mapper.readValue("custom-block-fallbacks: {default-block: custom:unsafe}", Config.class).validate());
        assertThrows(IllegalArgumentException.class, () -> mapper.readValue("diagnostics-log-interval-seconds: 0", Config.class).validate());
    }
    @Test
    void hidesUnsupportedDefinitionsAndMapsBlocksToCorrectClientIds() throws Exception {
        var user = user(898, false);
        var source = user.getServerBlockDictionary().getKnownStates().stream().filter(s -> s.name().equals(CUSTOM)).findFirst().orElseThrow();
        int sourceId = user.getServerBlockDictionary().toRuntimeId(source.latestStateHash());
        int result = user.getFallbacks().translateBlock(944, 898, sourceId);
        assertEquals("minecraft:gold_block", user.get(BlockDictionaryStorage.class).get(898).toBlockState(result).name());
        assertEquals(1, user.getDiagnostics().snapshot().replacements());
        assertNull(user.getFallbacks().translateBlock(898, 944, result));
        var start = new StartGamePacket();
        start.getBlockProperties().add(new BlockPropertyData(CUSTOM, NbtMap.EMPTY));
        user.getFallbacks().prepare(start);
        assertTrue(start.getBlockProperties().isEmpty());
    }
    @Test
    void supportsLegacyVanillaPalettesAndHashedPalettes() throws Exception {
        for (int client : List.of(361, 408, 419, 898)) {
            var user = user(client, false);
            var state = user.getServerBlockDictionary().getKnownStates().stream().filter(s -> s.name().equals(CUSTOM)).findFirst().orElseThrow();
            int id = user.getFallbacks().translateBlock(944, client, user.getServerBlockDictionary().toRuntimeId(state.latestStateHash()));
            assertEquals("minecraft:gold_block", user.get(BlockDictionaryStorage.class).get(client).toBlockState(id).name());
        }
        var user = user(898, true);
        var state = user.getServerBlockDictionary().getKnownStates().stream().filter(s -> s.name().equals(CUSTOM)).findFirst().orElseThrow();
        int hash = user.getFallbacks().translateBlock(944, 898, state.currentStateHash());
        assertEquals("minecraft:gold_block", user.get(BlockDictionaryStorage.class).get(898).toBlockStateHash(hash).name());
    }
    @Test
    void replacesBlockItemsAndRestoresOnlyServerIssuedSnapshots() throws Exception {
        var user = user(898, false);
        var state = user.getServerBlockDictionary().getKnownStates().stream().filter(s -> s.name().equals(CUSTOM)).findFirst().orElseThrow();
        int id = user.getServerBlockDictionary().toRuntimeId(state.latestStateHash());
        var item = ItemData.builder().definition(new SimpleItemDefinition(CUSTOM, 30000, true))
                .blockDefinition(() -> id).count(3).netId(42).usingNetId(true)
                .tag(NbtMap.builder().putString("original", "preserve").build()).build();
        var view = user.getFallbacks().translateItem(944, 898, item);
        assertEquals("minecraft:gold_block", view.getDefinition().getIdentifier());
        var restored = user.getFallbacks().translateItem(898, 944, view);
        assertSame(item.getDefinition(), restored.getDefinition());
        assertEquals(item.getTag(), restored.getTag());
        assertEquals(42, restored.getNetId());
        var refresh = user.getFallbacks().translateItem(944, 898, item.toBuilder().count(2).build());
        assertEquals(view.getTag().getString(BlockFallbacks.ITEM_TOKEN), refresh.getTag().getString(BlockFallbacks.ITEM_TOKEN));
        assertEquals(2, user.getFallbacks().translateItem(898, 944, refresh).getCount());
        var forged = view.toBuilder().tag(NbtMap.builder().putString(BlockFallbacks.ITEM_TOKEN, "not-issued").build()).build();
        assertNull(user.getFallbacks().translateItem(898, 944, forged));
    }
}
