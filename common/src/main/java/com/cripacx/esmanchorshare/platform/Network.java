package com.cripacx.esmanchorshare.platform;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import java.util.ServiceLoader;

/** The only loader-specific behaviour common code needs. Client methods must only be called on the client. */
public interface Network {

    Network INSTANCE = ServiceLoader.load(Network.class, Network.class.getClassLoader()).findFirst()
            .orElseThrow(() -> new IllegalStateException("No " + Network.class.getName() + " implementation found"));

    boolean canSendToServer(CustomPacketPayload.Type<?> type);

    void sendToServer(CustomPacketPayload payload);

    boolean canSendToPlayer(ServerPlayer player, CustomPacketPayload.Type<?> type);

    void sendToPlayer(ServerPlayer player, CustomPacketPayload payload);
}
