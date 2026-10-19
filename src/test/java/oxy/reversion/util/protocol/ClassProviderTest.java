package oxy.reversion.util.protocol;

import net.lenni0451.classtransform.utils.ASMUtils;
import org.junit.jupiter.api.Test;
import oxy.reversion.util.transformers.GeyserExtensionClassProvider;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ClassProviderTest {
    @Test
    void readsJdkTypesWithoutAnExtensionLoaderLookup() throws Exception {
        var provider = new GeyserExtensionClassProvider();
        assertEquals("java/util/Iterator", ASMUtils.fromBytes(provider.getClass("java.util.Iterator")).name);
    }

    @Test
    void readsBundledAndExtensionClassesWithoutInitializingGeyser() throws Exception {
        var provider = new GeyserExtensionClassProvider();
        assertEquals("com/github/blackjack200/ouranos/converter/TypeConverter",
                ASMUtils.fromBytes(provider.getClass("com.github.blackjack200.ouranos.converter.TypeConverter")).name);
        assertEquals("oxy/reversion/user/ReversionUser", ASMUtils.fromBytes(provider.getClass("oxy.reversion.user.ReversionUser")).name);
    }
}
