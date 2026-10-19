package com.cripacx.esmanchorshare.neoforge;

import com.cripacx.esmanchorshare.AnchorShare;
import com.cripacx.esmanchorshare.client.ChatShares;
import com.cripacx.esmanchorshare.client.ClientCommands;
import com.cripacx.esmanchorshare.client.ServerAnchorCache;
import com.cripacx.esmanchorshare.network.AnchorPayloads;
import net.minecraft.network.chat.ChatType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientChatReceivedEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;
import net.neoforged.neoforge.common.NeoForge;

@Mod(value = AnchorShare.MOD_ID, dist = Dist.CLIENT)
public class AnchorShareNeoForgeClient {

    public AnchorShareNeoForgeClient(IEventBus modBus) {
        modBus.addListener((RegisterClientPayloadHandlersEvent event) -> {
            event.register(AnchorPayloads.Shared.TYPE, (payload, context) -> ChatShares.show(payload.sender(), payload.anchor()));
            event.register(AnchorPayloads.ServerAnchors.TYPE, (payload, context) -> ServerAnchorCache.set(payload.anchors()));
        });
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut event) -> ServerAnchorCache.clear());
        NeoForge.EVENT_BUS.addListener(AnchorShareNeoForgeClient::onChat);
        NeoForge.EVENT_BUS.addListener((RegisterClientCommandsEvent event) -> ClientCommands.register(event.getDispatcher()));
    }

    private static void onChat(ClientChatReceivedEvent event) {
        if (event instanceof ClientChatReceivedEvent.System system && system.isOverlay()) return;
        ChatType.Bound chatType = event.getBoundChatType();
        if (ChatShares.onChatMessage(event.getMessage(), chatType != null ? chatType.name().getString() : null))
            event.setCanceled(true);
    }
}
