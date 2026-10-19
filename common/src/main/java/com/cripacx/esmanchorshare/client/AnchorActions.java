package com.cripacx.esmanchorshare.client;

import com.cripacx.esmanchorshare.anchor.AnchorText;
import com.cripacx.esmanchorshare.anchor.SharedAnchor;
import com.cripacx.esmanchorshare.network.AnchorPayloads;
import com.cripacx.esmanchorshare.platform.Network;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.server.permissions.Permissions;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/** Share and publish, used by both the client commands and the buttons in ESM's screen. */
public final class AnchorActions {

    private static final SimpleCommandExceptionType TOO_LARGE_FOR_CHAT = new SimpleCommandExceptionType(
            Component.translatable("esmanchorshare.share.too_large"));
    private static final SimpleCommandExceptionType TOO_MANY_SOUNDS = new SimpleCommandExceptionType(
            Component.translatable("esmanchorshare.anchor.too_large"));
    private static final SimpleCommandExceptionType NO_SERVER_ADDON = new SimpleCommandExceptionType(
            Component.translatable("esmanchorshare.publish.no_server_addon"));
    private static final SystemToast.SystemToastId TOAST = new SystemToast.SystemToastId(5000L);
    private static final long PART_DELAY_MILLIS = 1000;

    private AnchorActions() {
    }

    /** Through the server when it runs the addon, otherwise as chat messages other addon users decode. */
    public static Component share(SharedAnchor anchor) throws CommandSyntaxException {
        checkSendable(anchor);
        if (Network.INSTANCE.canSendToServer(AnchorPayloads.Share.TYPE)) {
            Network.INSTANCE.sendToServer(new AnchorPayloads.Share(anchor));
            return Component.translatable("esmanchorshare.share.sent", anchor.name());
        }

        List<String> messages = AnchorText.encode(anchor).orElseThrow(TOO_LARGE_FOR_CHAT::create);
        Minecraft minecraft = Minecraft.getInstance();
        ClientPacketListener connection = minecraft.getConnection();
        connection.sendChat(messages.getFirst());
        for (int i = 1; i < messages.size(); i++) {
            String message = messages.get(i);
            // spaced out so vanilla's chat spam limit (and most anti-spam plugins) never trigger
            CompletableFuture.runAsync(() -> {
                if (minecraft.getConnection() == connection) connection.sendChat(message);
            }, CompletableFuture.delayedExecutor(i * PART_DELAY_MILLIS, TimeUnit.MILLISECONDS, minecraft));
        }
        return messages.size() == 1
                ? Component.translatable("esmanchorshare.share.sent", anchor.name())
                : Component.translatable("esmanchorshare.share.sent_parts", anchor.name(), messages.size());
    }

    /** The server checks the permission again and answers in chat. */
    public static Component publish(SharedAnchor anchor) throws CommandSyntaxException {
        checkSendable(anchor);
        if (!Network.INSTANCE.canSendToServer(AnchorPayloads.Publish.TYPE)) throw NO_SERVER_ADDON.create();
        Network.INSTANCE.sendToServer(new AnchorPayloads.Publish(anchor));
        return Component.translatable("esmanchorshare.publish.sent", anchor.name());
    }

    /** Whether to offer publishing at all; the server stays the authority. */
    public static boolean canPublish() {
        LocalPlayer player = Minecraft.getInstance().player;
        return player != null
                && player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER)
                && Network.INSTANCE.canSendToServer(AnchorPayloads.Publish.TYPE);
    }

    /** Runs a GUI action and reports the outcome as a toast, since the open screen hides the chat. */
    public static void runFromScreen(Action action) {
        Component message;
        try {
            message = action.run();
        } catch (CommandSyntaxException e) {
            message = ComponentUtils.fromMessage(e.getRawMessage());
        }
        Minecraft minecraft = Minecraft.getInstance();
        SystemToast.addOrUpdate(minecraft.gui.toastManager(), TOAST, Component.translatable("esmanchorshare.toast.title"), message);
    }

    private static void checkSendable(SharedAnchor anchor) throws CommandSyntaxException {
        if (AnchorPayloads.encodedSize(SharedAnchor.STREAM_CODEC, anchor) > AnchorPayloads.MAX_SERVERBOUND_BYTES)
            throw TOO_MANY_SOUNDS.create();
    }

    @FunctionalInterface
    public interface Action {
        Component run() throws CommandSyntaxException;
    }
}
