package com.cripacx.esmanchorshare.anchor;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Loader- and language-independent copy of an ESM anchor, used for chat shares and server anchors.
 * Every anchor that crosses a trust boundary (network, chat text, save file) must pass {@link #validate()}.
 */
public record SharedAnchor(String name, Identifier dimension, BlockPos pos, int radius, Map<String, Double> sounds, List<String> blocks) {

    /** Stands in for ESM's localized "everything" entry, so anchors survive players using different languages. */
    public static final String EVERYTHING = "*";
    public static final int MAX_NAME_LENGTH = 32;
    public static final int MAX_RADIUS = 1024;
    public static final int MAX_ENTRIES = 512;
    public static final int MAX_KEY_LENGTH = 256;
    private static final double DEFAULT_VOLUME = 1D;

    public static final Codec<SharedAnchor> CODEC = RecordCodecBuilder.<SharedAnchor>create(i -> i.group(
            Codec.STRING.fieldOf("name").forGetter(SharedAnchor::name),
            Identifier.CODEC.fieldOf("dimension").forGetter(SharedAnchor::dimension),
            BlockPos.CODEC.fieldOf("pos").forGetter(SharedAnchor::pos),
            Codec.INT.fieldOf("radius").forGetter(SharedAnchor::radius),
            Codec.unboundedMap(Codec.STRING, Codec.DOUBLE).fieldOf("sounds").forGetter(SharedAnchor::sounds),
            Codec.STRING.listOf().optionalFieldOf("blocks", List.of()).forGetter(SharedAnchor::blocks)
    ).apply(i, SharedAnchor::new)).validate(anchor -> anchor.validate()
            .<DataResult<SharedAnchor>>map(error -> DataResult.error(() -> error))
            .orElseGet(() -> DataResult.success(anchor)));

    /** Bounded so a malicious peer cannot make us allocate unbounded memory; semantic checks stay in {@link #validate()}. */
    public static final StreamCodec<ByteBuf, SharedAnchor> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(MAX_NAME_LENGTH), SharedAnchor::name,
            Identifier.STREAM_CODEC, SharedAnchor::dimension,
            BlockPos.STREAM_CODEC, SharedAnchor::pos,
            ByteBufCodecs.VAR_INT, SharedAnchor::radius,
            ByteBufCodecs.map(HashMap::new, ByteBufCodecs.stringUtf8(MAX_KEY_LENGTH), ByteBufCodecs.DOUBLE, MAX_ENTRIES), SharedAnchor::sounds,
            ByteBufCodecs.stringUtf8(MAX_KEY_LENGTH).apply(ByteBufCodecs.list(MAX_ENTRIES)), SharedAnchor::blocks,
            SharedAnchor::new);

    public SharedAnchor {
        sounds = Map.copyOf(sounds);
        blocks = List.copyOf(blocks);
    }

    /** @return a description of the first problem, or empty if the anchor is safe to store and apply */
    public Optional<String> validate() {
        if (name.isBlank() || name.length() > MAX_NAME_LENGTH)
            return Optional.of("name must be 1-" + MAX_NAME_LENGTH + " characters");
        if (radius < 1 || radius > MAX_RADIUS)
            return Optional.of("radius must be 1-" + MAX_RADIUS);
        if (sounds.size() > MAX_ENTRIES || blocks.size() > MAX_ENTRIES)
            return Optional.of("at most " + MAX_ENTRIES + " sounds and blocks");
        for (Map.Entry<String, Double> entry : sounds.entrySet()) {
            double volume = entry.getValue();
            if (entry.getKey().isEmpty() || entry.getKey().length() > MAX_KEY_LENGTH)
                return Optional.of("invalid sound id");
            if (!(volume >= 0D && volume <= 1D))
                return Optional.of("volume of " + entry.getKey() + " must be between 0 and 1");
        }
        for (String block : blocks) {
            if (block.isEmpty() || block.length() > MAX_KEY_LENGTH)
                return Optional.of("invalid block name");
        }
        return Optional.empty();
    }

    /** Same precedence as ESM's own anchors: everything, then the exact sound, then the whole mod. */
    public double volumeFor(String soundId, String modId) {
        Double volume = sounds.get(EVERYTHING);
        if (volume == null) volume = sounds.get(soundId);
        if (volume == null) volume = sounds.get(modId);
        return volume != null ? volume : DEFAULT_VOLUME;
    }

    public boolean covers(Identifier dimension, BlockPos pos) {
        return this.dimension.equals(dimension) && pos.closerThan(this.pos, radius);
    }
}
