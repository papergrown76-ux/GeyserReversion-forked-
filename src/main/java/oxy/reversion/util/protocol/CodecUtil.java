package oxy.reversion.util.protocol;

import org.cloudburstmc.protocol.bedrock.codec.BedrockCodec;
import org.cloudburstmc.protocol.bedrock.codec.BedrockCodecHelper;
import org.cloudburstmc.protocol.bedrock.data.EncodingSettings;

public class CodecUtil {
    public static com.github.blackjack200.ouranos.shaded.protocol.bedrock.codec.BedrockCodec rebuildCodec(
            com.github.blackjack200.ouranos.shaded.protocol.bedrock.codec.BedrockCodec codec) {
        return codec.toBuilder().helper(() -> {
            var helper = codec.createHelper();
            helper.setEncodingSettings(com.github.blackjack200.ouranos.shaded.protocol.bedrock.data.EncodingSettings.builder()
                    .maxListSize(Integer.MAX_VALUE).maxByteArraySize(Integer.MAX_VALUE)
                    .maxNetworkNBTSize(Integer.MAX_VALUE).maxItemNBTSize(Integer.MAX_VALUE)
                    .maxStringLength(Integer.MAX_VALUE).build());
            return helper;
        }).build();
    }

    public static BedrockCodec rebuildCodec(BedrockCodec codec) {
        return codec.toBuilder().helper(() -> {
            BedrockCodecHelper helper = codec.createHelper();
            helper.setEncodingSettings(EncodingSettings.builder().maxListSize(Integer.MAX_VALUE).maxByteArraySize(Integer.MAX_VALUE).maxNetworkNBTSize(Integer.MAX_VALUE).maxItemNBTSize(Integer.MAX_VALUE).maxStringLength(Integer.MAX_VALUE).build());
            return helper;
        }).build();
    }
}
