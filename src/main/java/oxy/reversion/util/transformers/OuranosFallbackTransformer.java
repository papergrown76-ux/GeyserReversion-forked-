package oxy.reversion.util.transformers;

import com.github.blackjack200.ouranos.session.OuranosSession;
import com.github.blackjack200.ouranos.shaded.protocol.bedrock.packet.BedrockPacket;
import net.lenni0451.classtransform.InjectionCallback;
import net.lenni0451.classtransform.annotations.*;
import net.lenni0451.classtransform.annotations.injection.CInject;
import oxy.reversion.user.ReversionUser;

@CTransformer(OuranosSession.class)
public class OuranosFallbackTransformer {
    @CInline
    @CInject(method = "translateClientbound", target = @CTarget("HEAD"))
    public void prepareFallbackPalette(BedrockPacket packet, InjectionCallback callback) {
        if ((Object) this instanceof ReversionUser user) user.getFallbacks().prepare(packet);
    }
}
