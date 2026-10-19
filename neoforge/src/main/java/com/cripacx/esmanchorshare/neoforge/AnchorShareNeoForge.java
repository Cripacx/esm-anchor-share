package com.cripacx.esmanchorshare.neoforge;

import com.cripacx.esmanchorshare.AnchorShare;
import com.cripacx.esmanchorshare.network.AnchorPayloads;
import com.cripacx.esmanchorshare.server.AdminCommands;
import com.cripacx.esmanchorshare.server.ServerAnchorManager;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

@Mod(AnchorShare.MOD_ID)
public class AnchorShareNeoForge {

    public AnchorShareNeoForge(IEventBus modBus) {
        modBus.addListener(AnchorShareNeoForge::registerPayloads);
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedInEvent event) -> {
            if (event.getEntity() instanceof ServerPlayer player) ServerAnchorManager.onJoin(player);
        });
        NeoForge.EVENT_BUS.addListener((RegisterCommandsEvent event) -> AdminCommands.register(event.getDispatcher()));
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        // optional: players and servers without the addon can still connect
        PayloadRegistrar registrar = event.registrar("1").optional();
        registrar.playToServer(AnchorPayloads.Share.TYPE, AnchorPayloads.Share.STREAM_CODEC,
                (payload, context) -> ServerAnchorManager.share((ServerPlayer) context.player(), payload.anchor()));
        registrar.playToServer(AnchorPayloads.Publish.TYPE, AnchorPayloads.Publish.STREAM_CODEC,
                (payload, context) -> ServerAnchorManager.publish((ServerPlayer) context.player(), payload.anchor()));
        // client handlers are registered in AnchorShareNeoForgeClient
        registrar.playToClient(AnchorPayloads.Shared.TYPE, AnchorPayloads.Shared.STREAM_CODEC);
        registrar.playToClient(AnchorPayloads.ServerAnchors.TYPE, AnchorPayloads.ServerAnchors.STREAM_CODEC);
    }
}
