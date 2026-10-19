package oxy.reversion.util.transformers;

import com.github.blackjack200.ouranos.shaded.protocol.bedrock.codec.BedrockCodec;
import net.lenni0451.classtransform.annotations.CTransformer;
import net.lenni0451.classtransform.annotations.injection.COverride;
import oxy.reversion.util.protocol.CodecUtil;

/** The bundled Ouranos codec cache must not cache mutable connection helpers. */
@CTransformer(com.github.blackjack200.ouranos.utils.CodecUtil.class)
public class OuranosCodecTransformer {
    @COverride
    public static BedrockCodec rebuildCodec(BedrockCodec codec) {
        return CodecUtil.rebuildCodec(codec);
    }
}
