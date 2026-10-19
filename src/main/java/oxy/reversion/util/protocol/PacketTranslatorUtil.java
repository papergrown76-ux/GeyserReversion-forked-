package oxy.reversion.util.protocol;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;
import oxy.reversion.user.ReversionUser;
import oxy.reversion.diagnostics.PacketDiagnostics;

public class PacketTranslatorUtil {
    public static BedrockPacket translateServerbound(ReversionUser user, BedrockPacket packet) {
        final ByteBuf input = Unpooled.buffer(), output = Unpooled.buffer();
        try {
            user.encodeClient(packet, input);

            final int oldId = user.getCloudburstClientCodec().getPacketDefinition(packet.getClass()).getId();
            final Integer newId = user.translateServerbound(input, output, oldId);
            if (newId == null) {
                user.getDiagnostics().dropped("serverbound", packet.getClass().getSimpleName(), user.getProtocolId(), user.getTargetVersion());
                return null;
            }

            return user.decodeServer(output, newId);
        } catch (Exception exception) {
            PacketDiagnostics.failure(user, "translate-serverbound", packet, exception);
        } finally {
            input.release();
            output.release();
        }
        return null;
    }

    public static BedrockPacket translateClientbound(ReversionUser user, BedrockPacket packet) {
        final ByteBuf input = Unpooled.buffer(), output = Unpooled.buffer();
        try {
            user.encodeServer(packet, input);

            final int oldId = user.getCloudburstServerCodec().getPacketDefinition(packet.getClass()).getId();
            final Integer newId = user.translateClientbound(input, output, oldId);
            if (newId == null) {
                user.getDiagnostics().dropped("clientbound", packet.getClass().getSimpleName(), user.getProtocolId(), user.getTargetVersion());
                return null;
            }

            return user.decodeClient(output, newId);
        } catch (Exception exception) {
            PacketDiagnostics.failure(user, "translate-clientbound", packet, exception);
        } finally {
            input.release();
            output.release();
        }

        return null;
    }
}
