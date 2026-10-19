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

@CTransformer(TypeConverter.class)
public class OuranosItemTransformer {
    @CInline
    @CInject(method = "translateItemData", target = @CTarget("HEAD"), cancellable = true)
    public static void translateCustomItem(OuranosSession session, int input, int output,
                                           ItemData item, InjectionCallback callback) {
        ItemData translated = CustomItemTranslator.translate(session, input, output, item);
        if (translated != null) {
            callback.setReturnValue(translated);
        }
    }
}
