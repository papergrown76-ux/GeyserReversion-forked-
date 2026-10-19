package oxy.reversion.util.registry;

import com.github.blackjack200.ouranos.converter.TypeConverter;
import com.github.blackjack200.ouranos.session.storage.BlockDictionaryStorage;
import com.github.blackjack200.ouranos.shaded.protocol.bedrock.data.BlockPropertyData;
import com.github.blackjack200.ouranos.shaded.protocol.bedrock.packet.StartGamePacket;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.cloudburstmc.math.vector.Vector3i;
import org.cloudburstmc.nbt.NbtMap;
import org.cloudburstmc.nbt.NbtType;
import org.cloudburstmc.protocol.bedrock.packet.UpdateBlockPacket;
import org.cloudburstmc.protocol.bedrock.packet.InventoryTransactionPacket;
import org.cloudburstmc.protocol.bedrock.data.inventory.ItemData;
import org.cloudburstmc.protocol.bedrock.data.inventory.transaction.InventoryTransactionType;
import org.cloudburstmc.protocol.bedrock.data.inventory.transaction.ItemUseTransaction;
import org.cloudburstmc.math.vector.Vector3f;
import org.cloudburstmc.protocol.common.util.VarInts;
import com.github.blackjack200.ouranos.converter.palette.Palette;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import oxy.reversion.user.ReversionUser;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CustomBlockTranslationTest {
    private static final int SERVER = 944;
    private static final String CUSTOM = "example:nexo_block";

    static ReversionUser user(int clientProtocol, boolean hashed) {
        var user = new ReversionUser(clientProtocol, SERVER, null);
        var start = new StartGamePacket();
        start.setBlockNetworkIdsHashed(hashed);
        start.getBlockProperties().add(new BlockPropertyData(CUSTOM, NbtMap.builder()
                .putList("properties", NbtType.COMPOUND, List.of(
                        NbtMap.builder().putString("name", "facing")
                                .putList("enum", NbtType.STRING, "north", "south").build(),
                        NbtMap.builder().putString("name", "lit")
                                .putList("enum", NbtType.BYTE, (byte) 0, (byte) 1).build()))
                .build()));
        assertNotNull(user.translateClientbound(start));
        return user;
    }

    @ParameterizedTest
    @ValueSource(ints = {419, 575, 898, 924})
    void resolvesEveryCustomPermutationAndRoundTripsIds(int client) {
        var user = user(client, false);
        var storage = user.get(BlockDictionaryStorage.class);
        var server = storage.get(SERVER);
        var customStates = server.getKnownStates().stream().filter(s -> s.name().equals(CUSTOM)).toList();
        assertEquals(4, customStates.size());
        for (var state : customStates) {
            int serverId = server.toRuntimeId(state.latestStateHash());
            var definition = user.getServerCodecHelper().getBlockDefinitions().getDefinition(serverId);
            assertInstanceOf(com.github.blackjack200.ouranos.shaded.protocol.bedrock.data.definitions.SimpleBlockDefinition.class, definition);
            assertEquals(CUSTOM, ((com.github.blackjack200.ouranos.shaded.protocol.bedrock.data.definitions.SimpleBlockDefinition) definition).getIdentifier());
            int clientId = TypeConverter.translateBlockRuntimeId(user, SERVER, client, serverId);
            assertEquals(serverId, TypeConverter.translateBlockRuntimeId(user, client, SERVER, clientId));
            var clientState = storage.get(client).toBlockState(clientId);
            assertEquals(state.rawState().getCompound("states"), clientState.rawState().getCompound("states"));
        }
    }

    @Test
    void resolvesUnknownIdsWithoutUnboxingNull() {
        var user = user(898, false);
        assertEquals(Integer.MAX_VALUE, user.getServerCodecHelper().getBlockDefinitions()
                .getDefinition(Integer.MAX_VALUE).getRuntimeId());
    }

    @Test
    void resolvesHashedCustomStates() {
        var user = user(898, true);
        var state = user.get(BlockDictionaryStorage.class).get(SERVER).getKnownStates().stream()
                .filter(s -> s.name().equals(CUSTOM)).findFirst().orElseThrow();
        var definition = user.getServerCodecHelper().getBlockDefinitions().getDefinition(state.currentStateHash());
        assertInstanceOf(com.github.blackjack200.ouranos.shaded.protocol.bedrock.data.definitions.SimpleBlockDefinition.class, definition);
        assertEquals(CUSTOM, ((com.github.blackjack200.ouranos.shaded.protocol.bedrock.data.definitions.SimpleBlockDefinition) definition).getIdentifier());
    }

    @Test
    void blockUpdateSurvivesBothCodecBoundaries() {
        var user = user(898, false);
        var storage = user.get(BlockDictionaryStorage.class);
        var state = storage.get(SERVER).getKnownStates().stream()
                .filter(s -> s.name().equals(CUSTOM)).findFirst().orElseThrow();
        int serverId = storage.get(SERVER).toRuntimeId(state.latestStateHash());
        int expectedClientId = storage.get(898).toRuntimeId(state.latestStateHash());
        var update = new UpdateBlockPacket();
        update.setBlockPosition(Vector3i.from(10, 64, -5));
        update.setDefinition(() -> serverId);
        ByteBuf input = Unpooled.buffer(), output = Unpooled.buffer();
        try {
            user.encodeServer(update, input);
            int packetId = user.getCloudburstServerCodec().getPacketDefinition(UpdateBlockPacket.class).getId();
            var translatedId = user.translateClientbound(input, output, packetId);
            assertNotNull(translatedId);
            var translated = (UpdateBlockPacket) user.decodeClient(output, translatedId);
            assertEquals(expectedClientId, translated.getDefinition().getRuntimeId());
            assertEquals(update.getBlockPosition(), translated.getBlockPosition());
            assertEquals(0, output.readableBytes());
        } finally {
            input.release();
            output.release();
        }
    }

    @Test
    void serverboundBlockInteractionRecoversTheServerRuntimeId() {
        var user = user(898, false);
        var storage = user.get(BlockDictionaryStorage.class);
        var state = storage.get(SERVER).getKnownStates().stream()
                .filter(s -> s.name().equals(CUSTOM)).findFirst().orElseThrow();
        int serverId = storage.get(SERVER).toRuntimeId(state.latestStateHash());
        int clientId = storage.get(898).toRuntimeId(state.latestStateHash());
        var packet = new InventoryTransactionPacket();
        packet.setTransactionType(InventoryTransactionType.ITEM_USE);
        packet.setActionType(2);
        packet.setBlockPosition(Vector3i.from(10, 64, -5));
        packet.setBlockFace(1);
        packet.setTriggerType(ItemUseTransaction.TriggerType.PLAYER_INPUT);
        packet.setClientInteractPrediction(ItemUseTransaction.PredictedResult.SUCCESS);
        packet.setItemInHand(ItemData.AIR);
        packet.setPlayerPosition(Vector3f.ZERO);
        packet.setClickPosition(Vector3f.ZERO);
        packet.setBlockDefinition(() -> clientId);
        ByteBuf input = Unpooled.buffer(), output = Unpooled.buffer();
        try {
            user.encodeClient(packet, input);
            int packetId = user.getCloudburstClientCodec().getPacketDefinition(InventoryTransactionPacket.class).getId();
            var translatedId = user.translateServerbound(input, output, packetId);
            assertNotNull(translatedId);
            var result = (InventoryTransactionPacket) user.decodeServer(output, translatedId);
            assertEquals(serverId, result.getBlockDefinition().getRuntimeId());
            assertEquals(2, result.getActionType());
            assertEquals(packet.getBlockPosition(), result.getBlockPosition());
            assertEquals(0, output.readableBytes());
        } finally {
            input.release();
            output.release();
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {419, 575, 898, 924})
    void rewritesCustomBlockIdsInChunkPalettes(int client) throws Exception {
        var user = user(client, false);
        var storage = user.get(BlockDictionaryStorage.class);
        var state = storage.get(SERVER).getKnownStates().stream()
                .filter(s -> s.name().equals(CUSTOM)).findFirst().orElseThrow();
        int serverId = storage.get(SERVER).toRuntimeId(state.latestStateHash());
        int clientId = storage.get(client).toRuntimeId(state.latestStateHash());
        ByteBuf input = Unpooled.buffer(), output = Unpooled.buffer();
        try {
            input.writeByte(9).writeByte(1).writeByte(4).writeByte(1);
            VarInts.writeInt(input, serverId);
            TypeConverter.rewriteSubChunk(user, SERVER, client, input, output);
            assertEquals(client < 465 ? 8 : 9, output.readUnsignedByte());
            assertEquals(1, output.readUnsignedByte());
            if (client >= 465) assertEquals(4, output.readUnsignedByte());
            var palette = Palette.readNetwork(output, id -> id);
            assertEquals(clientId, palette.get(0));
            assertEquals(clientId, palette.get(4095));
            assertEquals(0, input.readableBytes());
            assertEquals(0, output.readableBytes());
        } finally {
            input.release();
            output.release();
        }
    }
}
