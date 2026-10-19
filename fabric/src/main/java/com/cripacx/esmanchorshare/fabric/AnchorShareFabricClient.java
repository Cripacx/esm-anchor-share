package com.cripacx.esmanchorshare.fabric;

import com.cripacx.esmanchorshare.AnchorShare;
import com.cripacx.esmanchorshare.client.ChatShares;
import com.cripacx.esmanchorshare.client.ClientCommands;
import com.cripacx.esmanchorshare.client.ServerAnchorCache;
import com.cripacx.esmanchorshare.network.AnchorPayloads;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;

public class AnchorShareFabricClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        // Without ESM there is nothing to share or apply; not registering the receivers also tells
        // the server this client cannot handle our payloads, so it gets the plain-text fallback.
        if (!FabricLoader.getInstance().isModLoaded(AnchorShare.ESM_MOD_ID)) {
            AnchorShare.LOG.warn("Extreme Sound Muffler is not installed, ESM Anchor Share is inactive on this client");
            return;
        }

        ClientPlayNetworking.registerGlobalReceiver(AnchorPayloads.Shared.TYPE,
                (payload, context) -> ChatShares.show(payload.sender(), payload.anchor()));
        ClientPlayNetworking.registerGlobalReceiver(AnchorPayloads.ServerAnchors.TYPE,
                (payload, context) -> ServerAnchorCache.set(payload.anchors()));
        ClientPlayConnectionEvents.DISCONNECT.register((listener, minecraft) -> ServerAnchorCache.clear());

        ClientReceiveMessageEvents.ALLOW_CHAT.register((message, signedMessage, sender, chatType, receivedAt) ->
                !ChatShares.onChatMessage(message, chatType.name().getString()));
        ClientReceiveMessageEvents.ALLOW_GAME.register((message, overlay) ->
                overlay || !ChatShares.onChatMessage(message, null));

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, buildContext) -> ClientCommands.register(dispatcher));
    }
}
