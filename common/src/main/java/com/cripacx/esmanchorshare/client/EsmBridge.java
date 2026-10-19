package com.cripacx.esmanchorshare.client;

import com.cripacx.esmanchorshare.anchor.SharedAnchor;
import com.leobeliik.extremesoundmuffler.CommonConfig;
import com.leobeliik.extremesoundmuffler.Constants;
import com.leobeliik.extremesoundmuffler.interfaces.ISoundLists;
import com.leobeliik.extremesoundmuffler.utils.Anchor;
import com.leobeliik.extremesoundmuffler.utils.DataManager;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** The only place that touches ESM's own anchor list. */
public final class EsmBridge {

    private static final DynamicCommandExceptionType UNKNOWN = new DynamicCommandExceptionType(
            name -> Component.translatable("esmanchorshare.anchor.unknown", name));
    private static final DynamicCommandExceptionType NOT_PLACED = new DynamicCommandExceptionType(
            name -> Component.translatable("esmanchorshare.anchor.not_placed", name));
    private static final DynamicCommandExceptionType INVALID = new DynamicCommandExceptionType(
            error -> Component.translatable("esmanchorshare.anchor.invalid", error));
    private static final SimpleCommandExceptionType DISABLED = new SimpleCommandExceptionType(
            Component.translatable("esmanchorshare.anchors_disabled"));

    private EsmBridge() {
    }

    public static Stream<String> anchorNames() {
        return ISoundLists.anchorList.stream().map(Anchor::getName);
    }

    public static SharedAnchor export(String name) throws CommandSyntaxException {
        return export(ISoundLists.anchorList.stream().filter(a -> a.getName().equals(name)).findFirst()
                .or(() -> ISoundLists.anchorList.stream().filter(a -> a.getName().equalsIgnoreCase(name)).findFirst())
                .orElseThrow(() -> UNKNOWN.create(name)));
    }

    public static SharedAnchor export(Anchor anchor) throws CommandSyntaxException {
        if (anchor.getDimension() == null || anchor.getX().isEmpty())
            throw NOT_PLACED.create(anchor.getName());

        BlockPos pos = new BlockPos(Integer.parseInt(anchor.getX()), Integer.parseInt(anchor.getY()), Integer.parseInt(anchor.getZ()));
        Map<String, Double> sounds = new HashMap<>();
        anchor.getMuffledSounds().forEach((sound, volume) ->
                sounds.put(sound.equals(Constants.EVERYTHING) ? SharedAnchor.EVERYTHING : sound, volume));
        SharedAnchor shared = new SharedAnchor(anchor.getName(), anchor.getDimension(), pos, anchor.getRange(), sounds, anchor.getMuffledBlocks());

        Optional<String> error = shared.validate();
        if (error.isPresent()) throw INVALID.create(error.get());
        return shared;
    }

    /** Adds the anchor to the player's own ESM anchors and saves them. @return the name it was saved under */
    public static String importAnchor(SharedAnchor shared) throws CommandSyntaxException {
        CommonConfig.ConfigAccess config = CommonConfig.get();
        if (config.disableAnchors().get() || Constants.isCustomSkinLoader) throw DISABLED.create();

        String name = uniqueName(shared.name());
        int radius = Math.min(shared.radius(), config.maxAnchorRange().get());
        Anchor anchor = new Anchor(name, shared.pos(), shared.dimension(), radius);
        shared.sounds().forEach((sound, volume) ->
                anchor.addSound(sound.equals(SharedAnchor.EVERYTHING) ? Constants.EVERYTHING : sound, volume));
        shared.blocks().forEach(anchor::addBlock);

        ISoundLists.anchorList.add(anchor);
        DataManager.saveData();
        return name;
    }

    private static String uniqueName(String base) {
        Set<String> taken = anchorNames().map(n -> n.toLowerCase(Locale.ROOT)).collect(Collectors.toSet());
        String name = base;
        for (int i = 2; taken.contains(name.toLowerCase(Locale.ROOT)); i++) {
            name = base + " (" + i + ")";
        }
        return name;
    }
}
