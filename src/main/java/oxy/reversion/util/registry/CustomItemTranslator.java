package oxy.reversion.util.registry;

import com.github.blackjack200.ouranos.converter.ItemTypeDictionary;
import com.github.blackjack200.ouranos.converter.TypeConverter;
import com.github.blackjack200.ouranos.session.OuranosSession;
import com.github.blackjack200.ouranos.session.SpecialOuranosSession;
import com.github.blackjack200.ouranos.shaded.protocol.bedrock.data.inventory.ItemData;

/** Keeps announced custom items out of Ouranos' unknown-vanilla-item fallback. */
public final class CustomItemTranslator {
    private CustomItemTranslator() {
    }

    public static ItemData translate(OuranosSession session, int input, int output, ItemData item) {
        if (!(session instanceof SpecialOuranosSession special) || item.isNull() || !item.isValid()) {
            return null;
        }
        var definition = item.getDefinition();
        if (ItemTypeDictionary.getInstance(input).fromStringId(definition.getIdentifier()) != null) {
            return null;
        }
        var source = input == session.getProtocolId() ? special.getClientCodecHelper() : special.getServerCodecHelper();
        var target = output == session.getProtocolId() ? special.getClientCodecHelper() : special.getServerCodecHelper();
        if (source.getItemDefinitions() == null || target.getItemDefinitions() == null) {
            return null;
        }
        var registered = source.getItemDefinitions().getDefinition(definition.getRuntimeId());
        var translated = target.getItemDefinitions().getDefinition(definition.getRuntimeId());
        if (registered == null || translated == null
                || !definition.getIdentifier().equals(registered.getIdentifier())
                || !definition.getIdentifier().equals(translated.getIdentifier())) {
            return null;
        }
        var builder = item.toBuilder().definition(translated);
        if (item.getBlockDefinition() != null) {
            builder.blockDefinition(TypeConverter.translateBlockDefinition(session, input, output, item.getBlockDefinition()));
        }
        return builder.build();
    }
}
