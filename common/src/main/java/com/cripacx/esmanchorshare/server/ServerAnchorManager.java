package com.cripacx.esmanchorshare.server;

import com.cripacx.esmanchorshare.anchor.SharedAnchor;
import com.cripacx.esmanchorshare.network.AnchorPayloads;
import com.cripacx.esmanchorshare.platform.Network;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.level.storage.LevelResource;
import java.io.IOException;
import java.io.Reader;
import java.lang.ref.WeakReference;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import static com.cripacx.esmanchorshare.AnchorShare.LOG;

/**
 * Owns the server anchors of the running world and the server side of chat shares.
 * Only touched from the server thread (payload handlers, commands and join events all run there).
 */
public final class ServerAnchorManager {

    private static final String FILE_NAME = "esmanchorshare_anchors.json";
    private static final long SHARE_COOLDOWN_MILLIS = 3000;
    private static final Codec<List<SharedAnchor>> LIST_CODEC = SharedAnchor.CODEC.listOf();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /** Keyed by lower-case name: names are unique ignoring case. */
    private static final Map<String, SharedAnchor> anchors = new LinkedHashMap<>();
    private static final Map<UUID, Long> lastShare = new HashMap<>();
    /** Weak, so a closed singleplayer world is not kept in memory. */
    private static WeakReference<MinecraftServer> loadedFor = new WeakReference<>(null);

    private ServerAnchorManager() {
    }

    public static List<SharedAnchor> list(MinecraftServer server) {
        ensureLoaded(server);
        return List.copyOf(anchors.values());
    }

    public static void onJoin(ServerPlayer player) {
        sync(player, list(player.level().getServer()));
    }

    public static void publish(ServerPlayer player, SharedAnchor anchor) {
        MinecraftServer server = player.level().getServer();
        if (!player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER)) {
            player.sendSystemMessage(message("esmanchorshare.publish.denied", "Only operators can create server anchors."));
            return;
        }
        Optional<String> error = anchor.validate();
        if (error.isPresent()) {
            player.sendSystemMessage(message("esmanchorshare.anchor.invalid", "Invalid anchor: %s", error.get()));
            return;
        }
        ensureLoaded(server);
        String key = key(anchor.name());
        if (!anchors.containsKey(key) && anchors.size() >= AnchorPayloads.MAX_SERVER_ANCHORS) {
            player.sendSystemMessage(message("esmanchorshare.publish.full", "The server already has %s anchors.", AnchorPayloads.MAX_SERVER_ANCHORS));
            return;
        }
        Map<String, SharedAnchor> updated = new LinkedHashMap<>(anchors);
        updated.put(key, anchor);
        if (AnchorPayloads.encodedSize(AnchorPayloads.ServerAnchors.STREAM_CODEC, new AnchorPayloads.ServerAnchors(List.copyOf(updated.values())))
                > AnchorPayloads.MAX_CLIENTBOUND_BYTES) {
            player.sendSystemMessage(message("esmanchorshare.publish.too_large", "Server anchors would get too large to sync. Remove some anchors or sounds first."));
            return;
        }
        boolean replaced = anchors.put(key, anchor) != null;
        saveAndSync(server);
        LOG.info("{} {} server anchor '{}'", player.getScoreboardName(), replaced ? "updated" : "created", anchor.name());
        player.sendSystemMessage(replaced
                ? message("esmanchorshare.publish.updated", "Server anchor \"%s\" updated for all players.", anchor.name())
                : message("esmanchorshare.publish.created", "Server anchor \"%s\" created for all players.", anchor.name()));
    }

    /** @return the removed anchor, if one with that name existed */
    public static Optional<SharedAnchor> remove(MinecraftServer server, String name) {
        ensureLoaded(server);
        SharedAnchor removed = anchors.remove(key(name));
        if (removed != null) saveAndSync(server);
        return Optional.ofNullable(removed);
    }

    public static void share(ServerPlayer player, SharedAnchor anchor) {
        if (anchor.validate().isPresent()) return; // our client validates before sending, so this is a tampered packet

        long now = System.currentTimeMillis();
        Long last = lastShare.get(player.getUUID());
        if (last != null && now - last < SHARE_COOLDOWN_MILLIS) {
            player.sendSystemMessage(message("esmanchorshare.share.cooldown", "Please wait a moment before sharing another anchor."));
            return;
        }
        lastShare.put(player.getUUID(), now);

        String sender = player.getScoreboardName();
        LOG.info("{} shared anchor '{}'", sender, anchor.name());
        AnchorPayloads.Shared payload = new AnchorPayloads.Shared(sender, anchor);
        for (ServerPlayer receiver : player.level().getServer().getPlayerList().getPlayers()) {
            if (Network.INSTANCE.canSendToPlayer(receiver, AnchorPayloads.Shared.TYPE)) {
                Network.INSTANCE.sendToPlayer(receiver, payload);
            } else {
                receiver.sendSystemMessage(message("esmanchorshare.share.no_addon",
                        "%s shared the sound muffler anchor \"%s\". Install ESM Anchor Share to copy it.", sender, anchor.name()));
            }
        }
    }

    /** Translatable for players with the addon, readable fallback for everyone else. */
    static Component message(String key, String fallback, Object... args) {
        return Component.translatableWithFallback(key, fallback, args);
    }

    private static void sync(ServerPlayer player, List<SharedAnchor> list) {
        if (Network.INSTANCE.canSendToPlayer(player, AnchorPayloads.ServerAnchors.TYPE))
            Network.INSTANCE.sendToPlayer(player, new AnchorPayloads.ServerAnchors(list));
    }

    private static void saveAndSync(MinecraftServer server) {
        save(server);
        List<SharedAnchor> list = List.copyOf(anchors.values());
        server.getPlayerList().getPlayers().forEach(player -> sync(player, list));
    }

    private static String key(String name) {
        return name.toLowerCase(Locale.ROOT);
    }

    private static Path file(MinecraftServer server) {
        return server.getWorldPath(LevelResource.DATA).resolve(FILE_NAME);
    }

    private static void ensureLoaded(MinecraftServer server) {
        if (loadedFor.get() == server) return;
        loadedFor = new WeakReference<>(server);
        anchors.clear();
        lastShare.clear();

        Path file = file(server);
        if (!Files.exists(file)) return;
        try (Reader reader = Files.newBufferedReader(file)) {
            JsonElement json = JsonParser.parseReader(reader);
            LIST_CODEC.parse(JsonOps.INSTANCE, json)
                    .resultOrPartial(error -> LOG.error("Skipping invalid server anchors in {}: {}", file, error))
                    .ifPresent(list -> list.forEach(anchor -> anchors.put(key(anchor.name()), anchor)));
        } catch (IOException | JsonParseException e) {
            // Keep the unreadable file so the next save cannot overwrite the admin's data
            Path corrupt = file.resolveSibling(FILE_NAME + ".corrupt");
            LOG.error("Could not read server anchors from {}, moving it to {}", file, corrupt, e);
            try {
                Files.move(file, corrupt, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException moveError) {
                LOG.error("Could not move {}", file, moveError);
            }
        }
    }

    private static void save(MinecraftServer server) {
        Path file = file(server);
        Path tmp = file.resolveSibling(FILE_NAME + ".tmp");
        try {
            Files.createDirectories(file.getParent());
            JsonElement json = LIST_CODEC.encodeStart(JsonOps.INSTANCE, List.copyOf(anchors.values())).getOrThrow();
            Files.writeString(tmp, GSON.toJson(json));
            try {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException | IllegalStateException e) {
            LOG.error("Could not save server anchors to {}", file, e);
        }
    }
}
