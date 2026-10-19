package oxy.reversion.integration;

import com.github.blackjack200.ouranos.converter.TypeConverter;
import com.github.blackjack200.ouranos.session.storage.BlockDictionaryStorage;
import com.github.blackjack200.ouranos.shaded.protocol.bedrock.data.BlockPropertyData;
import com.github.blackjack200.ouranos.shaded.protocol.bedrock.data.definitions.SimpleItemDefinition;
import com.github.blackjack200.ouranos.shaded.protocol.bedrock.data.inventory.ItemData;
import com.github.blackjack200.ouranos.shaded.protocol.bedrock.packet.StartGamePacket;
import com.github.blackjack200.ouranos.shaded.protocol.common.SimpleDefinitionRegistry;
import com.github.blackjack200.ouranos.utils.HashUtils;
import org.geysermc.event.subscribe.Subscribe;
import org.geysermc.geyser.api.block.custom.CustomBlockData;
import org.geysermc.geyser.api.block.custom.component.CustomBlockComponents;
import org.geysermc.geyser.api.event.lifecycle.GeyserDefineCustomBlocksEvent;
import org.geysermc.geyser.api.event.lifecycle.GeyserPostInitializeEvent;
import org.geysermc.geyser.api.extension.Extension;
import org.geysermc.geyser.registry.BlockRegistries;
import oxy.reversion.user.ReversionUser;
import oxy.reversion.util.registry.ServerBlockPalette;
import oxy.reversion.config.Config;
import oxy.reversion.config.FallbackConfig;

import java.util.List;
import java.util.Arrays;

/** Loaded only by the optional standalone smoke test, never included in the release JAR. */
public class CustomContentFixture implements Extension {
    private CustomBlockData block;

    @Subscribe
    public void register(GeyserDefineCustomBlocksEvent event) {
        block = CustomBlockData.builder().name("reversion_smoke")
                .components(CustomBlockComponents.builder().build())
                .booleanProperty("lit").stringProperty("facing", List.of("north", "south")).build();
        event.register(block);
    }

    @Subscribe
    public void validate(GeyserPostInitializeEvent event) {
        try {
            var mappings = BlockRegistries.BLOCKS.forVersion(944);
            long customStates = Arrays.stream(mappings.getBedrockRuntimeMap())
                    .filter(state -> block.identifier().equals(state.getState().getString("name"))).count();
            if (customStates != 4) {
                throw new AssertionError("Expected four registered custom block states, got " + customStates);
            }
            var user = new ReversionUser(898, 944, null, new Config());
            user.setServerBlockDictionary(ServerBlockPalette.fromMappings(mappings));
            var start = new StartGamePacket();
            for (var property : mappings.getBlockProperties()) {
                start.getBlockProperties().add(new BlockPropertyData(property.getName(), property.getProperties()));
            }
            user.translateClientbound(start);
            var second = new ReversionUser(898, 944, null);
            if (user.getClientCodecHelper() == second.getClientCodecHelper()
                    || user.getServerCodecHelper() == second.getServerCodecHelper()) {
                throw new AssertionError("Ouranos codec helpers are shared between connections");
            }
            var dictionary = user.get(BlockDictionaryStorage.class).get(944);
            if (dictionary != user.getServerBlockDictionary()) {
                throw new AssertionError("Runtime server palette transformer is not active");
            }
            var palette = mappings.getBedrockRuntimeMap();
            if (palette.length != dictionary.getKnownStates().size()) {
                throw new AssertionError("Geyser and Ouranos palette sizes differ");
            }
            for (int id = 0; id < palette.length; id++) {
                if (HashUtils.computeBlockStateHash(palette[id].getState()) != dictionary.toBlockState(id).currentStateHash()) {
                    throw new AssertionError("Geyser and Ouranos palettes differ at runtime ID " + id
                            + "; host=" + palette[id].getState() + "; translated=" + dictionary.toBlockState(id));
                }
            }
            var items = SimpleDefinitionRegistry.<com.github.blackjack200.ouranos.shaded.protocol.bedrock.data.definitions.ItemDefinition>builder()
                    .add(new SimpleItemDefinition(block.identifier(), 30000, true)).build();
            user.getServerCodecHelper().setItemDefinitions(items);
            user.getClientCodecHelper().setItemDefinitions(items);
            var item = ItemData.builder().definition(items.getDefinition(30000)).count(1).build();
            var translated = TypeConverter.translateItemData(user, 944, 898, item);
            if (!block.identifier().equals(translated.getDefinition().getIdentifier())) {
                throw new AssertionError("Runtime CustomItem transformer did not preserve the custom item");
            }
            var fallback = new ReversionUser(898, 944, null,
                    new Config(new FallbackConfig(true, 924, "minecraft:gold_block")));
            fallback.setServerBlockDictionary(user.getServerBlockDictionary());
            var fallbackStart = new StartGamePacket();
            for (var property : mappings.getBlockProperties()) {
                fallbackStart.getBlockProperties().add(new BlockPropertyData(property.getName(), property.getProperties()));
            }
            var outputStart = (StartGamePacket) fallback.translateClientbound(fallbackStart);
            if (!outputStart.getBlockProperties().isEmpty()) throw new AssertionError("Unsupported custom definitions still announced");
            var customState = dictionary.getKnownStates().stream().filter(s -> s.name().equals(block.identifier())).findFirst().orElseThrow();
            int serverId = dictionary.toRuntimeId(customState.latestStateHash());
            int clientId = TypeConverter.translateBlockRuntimeId(fallback, 944, 898, serverId);
            if (!"minecraft:gold_block".equals(fallback.get(BlockDictionaryStorage.class).get(898).toBlockState(clientId).name())) {
                throw new AssertionError("Runtime block fallback did not use the replacement palette");
            }
            var blockItem = item.toBuilder().blockDefinition(fallback.getServerCodecHelper().getBlockDefinitions().getDefinition(serverId)).build();
            var itemView = TypeConverter.translateItemData(fallback, 944, 898, blockItem);
            if (!"minecraft:gold_block".equals(itemView.getDefinition().getIdentifier())) throw new AssertionError("Block item was not replaced");
            var restored = TypeConverter.translateItemData(fallback, 898, 944, itemView);
            if (!block.identifier().equals(restored.getDefinition().getIdentifier())) throw new AssertionError("Original custom item not restored");
            logger().info("FALLBACK_SMOKE_PASS: palette filtering, block IDs and server-issued item restoration verified");
            logger().info("CUSTOM_CONTENT_SMOKE_PASS: " + palette.length + " states match; custom item transformer active");
        } catch (Throwable failure) {
            logger().severe("CUSTOM_CONTENT_SMOKE_FAIL", failure);
            throw new RuntimeException(failure);
        }
    }
}
