package com.cripacx.esmanchorshare.client;

import com.cripacx.esmanchorshare.anchor.SharedAnchor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import java.util.List;
import java.util.Locale;

/** Server anchors received from the server; applied after the player's own ESM anchors. */
public final class ServerAnchorCache {

    private static final double DEFAULT_VOLUME = 1D;
    private static volatile List<SharedAnchor> anchors = List.of();

    private ServerAnchorCache() {
    }

    public static void set(List<SharedAnchor> received) {
        anchors = received.stream().filter(anchor -> anchor.validate().isEmpty()).toList();
    }

    public static void clear() {
        anchors = List.of();
    }

    /** Mirrors ESM's own anchor lookup, including its minecart special case. */
    public static double muffling(SoundInstance sound) {
        List<SharedAnchor> current = anchors;
        Minecraft minecraft = Minecraft.getInstance();
        if (current.isEmpty() || minecraft.level == null || minecraft.player == null) return DEFAULT_VOLUME;

        Identifier id = sound.getIdentifier();
        BlockPos soundPos = id.getPath().contains("entity.minecart.inside")
                ? minecraft.player.getOnPos()
                : new BlockPos((int) sound.getX(), (int) sound.getY(), (int) sound.getZ());
        Identifier dimension = minecraft.level.dimension().identifier();
        String soundId = id.toString();
        String modId = id.getNamespace().toLowerCase(Locale.ROOT);

        for (SharedAnchor anchor : current) {
            if (anchor.covers(dimension, soundPos)) {
                double volume = anchor.volumeFor(soundId, modId);
                if (volume != DEFAULT_VOLUME) return volume;
            }
        }
        return DEFAULT_VOLUME;
    }
}
