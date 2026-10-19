package com.cripacx.esmanchorshare.fabric;

import com.cripacx.esmanchorshare.network.AnchorPayloads;
import com.cripacx.esmanchorshare.server.AdminCommands;
import com.cripacx.esmanchorshare.server.ServerAnchorManager;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

public class AnchorShareFabric implements ModInitializer {

    @Override
    public void onInitialize() {
        PayloadTypeRegistry.serverboundPlay().register(AnchorPayloads.Share.TYPE, AnchorPayloads.Share.STREAM_CODEC);
        PayloadTypeRegistry.serverboundPlay().register(AnchorPayloads.Publish.TYPE, AnchorPayloads.Publish.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(AnchorPayloads.Shared.TYPE, AnchorPayloads.Shared.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(AnchorPayloads.ServerAnchors.TYPE, AnchorPayloads.ServerAnchors.STREAM_CODEC);

        ServerPlayNetworking.registerGlobalReceiver(AnchorPayloads.Share.TYPE,
                (payload, context) -> ServerAnchorManager.share(context.player(), payload.anchor()));
        ServerPlayNetworking.registerGlobalReceiver(AnchorPayloads.Publish.TYPE,
                (payload, context) -> ServerAnchorManager.publish(context.player(), payload.anchor()));
        ServerPlayConnectionEvents.JOIN.register((listener, sender, server) -> ServerAnchorManager.onJoin(listener.player));
        CommandRegistrationCallback.EVENT.register((dispatcher, buildContext, selection) -> AdminCommands.register(dispatcher));
    }
}
