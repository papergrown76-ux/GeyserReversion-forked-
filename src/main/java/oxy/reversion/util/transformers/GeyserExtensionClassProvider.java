package oxy.reversion.util.transformers;

import net.lenni0451.classtransform.utils.tree.IClassProvider;
import org.geysermc.geyser.GeyserImpl;
import org.geysermc.geyser.extension.GeyserExtensionLoader;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nonnull;
import java.io.InputStream;
import java.util.Map;
import java.util.function.Supplier;

import static net.lenni0451.classtransform.utils.ASMUtils.slash;

public class GeyserExtensionClassProvider implements IClassProvider {
    @Override
    @Nonnull
    public byte[] getClass(@NotNull String name) throws ClassNotFoundException {
        String resource = slash(name) + ".class";
        // The extension loader cannot find java.* classes. Reading resources also
        // avoids loading a class recursively while its bytecode is being transformed.
        InputStream stream = getClass().getClassLoader().getResourceAsStream(resource);
        try {
            if (stream == null) {
                Class<?> klass = ((GeyserExtensionLoader) GeyserImpl.getInstance()
                        .getExtensionManager().extensionLoader()).classByName(name);
                if (klass != null) stream = klass.getClassLoader().getResourceAsStream(resource);
            }
            if (stream == null) throw new ClassNotFoundException(name);
            try (InputStream input = stream) {
                return input.readAllBytes();
            }
        } catch (Throwable t) {
            throw new ClassNotFoundException(name, t);
        }
    }

    @Override
    @Nonnull
    public Map<String, Supplier<byte[]>> getAllClasses() {
        return Map.of();
    }
}
