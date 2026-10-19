package oxy.reversion.util.transformers;

import com.github.blackjack200.ouranos.converter.TypeConverter;
import com.github.blackjack200.ouranos.session.OuranosSession;
import com.github.blackjack200.ouranos.shaded.protocol.bedrock.data.inventory.ItemData;
import net.lenni0451.classtransform.InjectionCallback;
import net.lenni0451.classtransform.annotations.CInline;
import net.lenni0451.classtransform.annotations.CTarget;
import net.lenni0451.classtransform.annotations.CTransformer;
import net.lenni0451.classtransform.annotations.injection.CInject;
import oxy.reversion.util.registry.CustomItemTranslator;
import oxy.reversion.user.ReversionUser;

@CTransformer(TypeConverter.class)
public class OuranosItemTransformer {
    @CInline
    @CInject(method = "translateItemData", target = @CTarget("HEAD"), cancellable = true)
    public static void translateCustomItem(OuranosSession session, int input, int output,
                                           ItemData item, InjectionCallback callback) {
        ItemData translated = session instanceof ReversionUser user ? user.getFallbacks().translateItem(input, output, item) : null;
        if (translated == null) translated = CustomItemTranslator.translate(session, input, output, item);
        if (translated != null) {
            callback.setReturnValue(translated);
        }
    }

    @CInline
    @CInject(method = "translateBlockRuntimeId", target = @CTarget("HEAD"), cancellable = true)
    public static void translateFallbackBlock(OuranosSession session, int input, int output, int runtimeId, InjectionCallback callback) {
        if (session instanceof ReversionUser user) {
            Integer replacement = user.getFallbacks().translateBlock(input, output, runtimeId);
            if (replacement != null) callback.setReturnValue(replacement);
        }
    }
}
