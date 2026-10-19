package oxy.reversion.util.registry;

import com.github.blackjack200.ouranos.converter.ItemTypeDictionary;
import com.github.blackjack200.ouranos.converter.TypeConverter;
import com.github.blackjack200.ouranos.session.OuranosSession;
import com.github.blackjack200.ouranos.session.storage.BlockDictionaryStorage;
import com.github.blackjack200.ouranos.shaded.protocol.bedrock.data.definitions.SimpleItemDefinition;
import com.github.blackjack200.ouranos.shaded.protocol.bedrock.data.inventory.ItemData;
import com.github.blackjack200.ouranos.shaded.protocol.common.SimpleDefinitionRegistry;
import com.github.blackjack200.ouranos.utils.ItemTypeDictionaryRegistry;
import net.lenni0451.classtransform.TransformerManager;
import net.lenni0451.classtransform.utils.ASMUtils;
import net.lenni0451.classtransform.utils.FailStrategy;
import net.lenni0451.classtransform.utils.tree.BasicClassProvider;
import org.cloudburstmc.nbt.NbtMap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import oxy.reversion.user.ReversionUser;
import oxy.reversion.util.transformers.OuranosItemTransformer;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

class CustomItemTranslationTest {
    private static Method translate;

    @BeforeAll
    static void loadActualTransformedConverter() throws Exception {
        var provider = new BasicClassProvider(CustomItemTranslationTest.class.getClassLoader());
        var manager = new TransformerManager(provider);
        manager.setFailStrategy(FailStrategy.CANCEL);
        manager.addTransformer(OuranosItemTransformer.class.getName());
        byte[] original = provider.getClass(TypeConverter.class.getName());
        byte[] transformed = manager.transform(TypeConverter.class.getName(), original);
        assertNotNull(transformed);
        // Loaded classes cannot gain methods during JVM retransformation.
        assertEquals(ASMUtils.fromBytes(original).methods.size(), ASMUtils.fromBytes(transformed).methods.size());
        var type = new ClassLoader(CustomItemTranslationTest.class.getClassLoader()) {
            Class<?> define(byte[] bytes) {
                return defineClass(TypeConverter.class.getName(), bytes, 0, bytes.length);
            }
        }.define(transformed);
        translate = type.getMethod("translateItemData", OuranosSession.class, int.class, int.class, ItemData.class);
    }

    private ReversionUser customUser() {
        var user = CustomBlockTranslationTest.user(898, false);
        var registry = SimpleDefinitionRegistry.<com.github.blackjack200.ouranos.shaded.protocol.bedrock.data.definitions.ItemDefinition>builder()
                .add(new SimpleItemDefinition("example:custom_block", 30000, true)).build();
        user.getServerCodecHelper().setItemDefinitions(registry);
        user.getClientCodecHelper().setItemDefinitions(new ItemTypeDictionaryRegistry(registry, 898));
        return user;
    }

    private ItemData convert(ReversionUser user, int from, int to, ItemData item) throws Exception {
        return (ItemData) translate.invoke(null, user, from, to, item);
    }

    @Test
    void registeredCustomBlockItemKeepsIdentityNbtAndBlockStateInBothDirections() throws Exception {
        var user = customUser();
        var serverDictionary = user.get(BlockDictionaryStorage.class).get(944);
        var state = serverDictionary.getKnownStates().stream()
                .filter(s -> s.name().equals("example:nexo_block")).findFirst().orElseThrow();
        int runtimeId = serverDictionary.toRuntimeId(state.latestStateHash());
        var item = ItemData.builder()
                .definition(user.getServerCodecHelper().getItemDefinitions().getDefinition(30000))
                .blockDefinition(user.getServerCodecHelper().getBlockDefinitions().getDefinition(runtimeId))
                .count(3).netId(42).usingNetId(true)
                .tag(NbtMap.builder().putString("custom_data", "preserve me").build()).build();

        var client = convert(user, 944, 898, item);
        assertEquals("example:custom_block", client.getDefinition().getIdentifier());
        assertEquals(30000, client.getDefinition().getRuntimeId());
        assertEquals(3, client.getCount());
        assertEquals(42, client.getNetId());
        assertEquals(item.getTag(), client.getTag());
        assertEquals(user.get(BlockDictionaryStorage.class).get(898).toRuntimeId(state.latestStateHash()),
                client.getBlockDefinition().getRuntimeId());
        var server = convert(user, 898, 944, client);
        assertEquals(runtimeId, server.getBlockDefinition().getRuntimeId());
        assertEquals(item.getDefinition(), server.getDefinition());
        assertEquals(item.getTag(), server.getTag());
    }

    @Test
    void vanillaItemStillUsesOriginalTranslation() throws Exception {
        var user = customUser();
        var stone = ItemTypeDictionary.getInstance(944).getEntries().get("minecraft:stone").toDefinition("minecraft:stone");
        var item = ItemData.builder().definition(stone).count(1).build();
        assertNull(CustomItemTranslator.translate(user, 944, 898, item));
        var expected = TypeConverter.translateItemData(user, 944, 898, item);
        var actual = convert(user, 944, 898, item);
        assertEquals(expected.getDefinition().getIdentifier(), actual.getDefinition().getIdentifier());
        assertEquals(expected.getDefinition().getRuntimeId(), actual.getDefinition().getRuntimeId());
        assertEquals(expected.getCount(), actual.getCount());
        assertEquals(expected.getDamage(), actual.getDamage());
        assertEquals(expected.getTag(), actual.getTag());
    }

    @Test
    void unannouncedCustomItemDoesNotBypassValidation() {
        var user = customUser();
        var item = ItemData.builder().definition(new SimpleItemDefinition("example:not_registered", 30000, true)).count(1).build();
        assertNull(CustomItemTranslator.translate(user, 944, 898, item));
    }
}
