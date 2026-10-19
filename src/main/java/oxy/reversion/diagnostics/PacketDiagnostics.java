package oxy.reversion.diagnostics;

import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;
import org.cloudburstmc.protocol.bedrock.packet.InventoryTransactionPacket;
import org.cloudburstmc.protocol.bedrock.packet.UpdateBlockPacket;
import oxy.reversion.user.ReversionUser;

public final class PacketDiagnostics {
    private PacketDiagnostics() {}
    public static void failure(ReversionUser user, String stage, BedrockPacket packet, Throwable failure) {
        String block = "";
        if (packet instanceof UpdateBlockPacket update && update.getDefinition() != null) {
            block = "blockId=" + update.getDefinition().getRuntimeId() + " position=" + update.getBlockPosition();
        } else if (packet instanceof InventoryTransactionPacket transaction && transaction.getBlockDefinition() != null) {
            block = "blockId=" + transaction.getBlockDefinition().getRuntimeId() + " position=" + transaction.getBlockPosition();
        }
        user.getDiagnostics().failure(stage, packet.getClass().getSimpleName(), user.getProtocolId(), user.getTargetVersion(), block, failure);
    }
}
