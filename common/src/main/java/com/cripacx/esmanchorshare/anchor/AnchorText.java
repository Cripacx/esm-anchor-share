package com.cripacx.esmanchorshare.anchor;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

/**
 * Plain chat-text form of an anchor, used when the server does not run this addon.
 * An anchor becomes one or more chat messages {@code [ESM] esma2:<id>:<part>/<total>:<data>}; the parts of all
 * messages joined are base64url(raw deflate({@link #COMPACT_CODEC})).
 */
public final class AnchorText {

    /** Vanilla rejects chat messages longer than this. */
    public static final int MAX_CHAT_LENGTH = 256;
    /** Parts are sent a second apart, so this also bounds how long a share takes. */
    public static final int MAX_PARTS = 5;
    private static final String LABEL = "[ESM] ";
    private static final String PREFIX = "esma2:";
    private static final int ID_LENGTH = 4;
    private static final int MAX_DATA_PER_PART = MAX_CHAT_LENGTH - (LABEL + PREFIX + "abcd:1/5:").length();
    private static final Pattern PART = Pattern.compile(Pattern.quote(PREFIX)
            + "([0-9a-z]{" + ID_LENGTH + "}):([1-9])/([1-9]):([A-Za-z0-9_-]{1," + MAX_DATA_PER_PART + "})");
    /** Upper bound for the inflated payload; far above any valid anchor, stops decompression bombs. */
    private static final int MAX_RAW_BYTES = 256 * 1024;

    private static final String VANILLA_NAMESPACE = "minecraft:";
    /** ESM's slider only reaches 0.9 and has no finer resolution than its width in pixels. */
    private static final double VOLUME_STEPS = 200D;
    private static final StreamCodec<ByteBuf, String> NAME = ByteBufCodecs.stringUtf8(SharedAnchor.MAX_NAME_LENGTH);
    private static final StreamCodec<ByteBuf, String> KEY = ByteBufCodecs.stringUtf8(SharedAnchor.MAX_KEY_LENGTH);

    /**
     * Smaller than {@link SharedAnchor#STREAM_CODEC} because every character counts in chat:
     * vanilla ids drop "minecraft:" (see {@link #shorten}), coordinates are zigzag varints and volumes one byte in 1/200 steps.
     */
    static final StreamCodec<ByteBuf, SharedAnchor> COMPACT_CODEC = StreamCodec.of(AnchorText::write, AnchorText::read);

    private AnchorText() {
    }

    /** @return the chat messages to send in order, or empty if the anchor needs more than {@link #MAX_PARTS} */
    public static Optional<List<String>> encode(SharedAnchor anchor) {
        String data = Base64.getUrlEncoder().withoutPadding().encodeToString(deflate(toBytes(anchor)));
        int total = (data.length() + MAX_DATA_PER_PART - 1) / MAX_DATA_PER_PART;
        if (total > MAX_PARTS) return Optional.empty();

        String id = randomId();
        List<String> messages = new ArrayList<>(total);
        for (int i = 0; i < total; i++) {
            String chunk = data.substring(i * MAX_DATA_PER_PART, Math.min(data.length(), (i + 1) * MAX_DATA_PER_PART));
            messages.add(LABEL + PREFIX + id + ":" + (i + 1) + "/" + total + ":" + chunk);
        }
        return Optional.of(messages);
    }

    /** Finds a well-formed part in a chat line; whether it decodes is only known once all parts arrived. */
    public static Optional<Part> findPart(String chatLine) {
        Matcher matcher = PART.matcher(chatLine);
        if (!matcher.find()) return Optional.empty();
        int index = Integer.parseInt(matcher.group(2));
        int total = Integer.parseInt(matcher.group(3));
        if (index > total || total > MAX_PARTS) return Optional.empty();
        return Optional.of(new Part(matcher.group(1), index, total, matcher.group(4)));
    }

    public record Part(String id, int index, int total, String data) {
    }

    public record Assembled(SharedAnchor anchor, @Nullable String sender) {
    }

    /** Collects parts per sender and share id until a share is complete. Not thread-safe. */
    public static final class Assembler {

        private static final long TIMEOUT_MILLIS = 30_000;
        private static final int MAX_PENDING = 16;

        private final Map<String, Pending> pending = new LinkedHashMap<>();

        private record Pending(String[] parts, long started, @Nullable String sender) {
        }

        /** @return the anchor once this part completed a share and the share decoded to a valid anchor */
        public Optional<Assembled> accept(Part part, @Nullable String sender, long nowMillis) {
            pending.values().removeIf(p -> nowMillis - p.started() > TIMEOUT_MILLIS);

            // keyed by sender too, so nobody can slip parts into another player's share
            String key = (sender != null ? sender : "") + "|" + part.id();
            Pending share = pending.get(key);
            if (share == null || share.parts().length != part.total()) {
                share = new Pending(new String[part.total()], nowMillis, sender);
                pending.put(key, share);
                if (pending.size() > MAX_PENDING) {
                    Iterator<String> eldest = pending.keySet().iterator();
                    eldest.next();
                    eldest.remove();
                }
            }
            share.parts()[part.index() - 1] = part.data();
            if (Arrays.stream(share.parts()).anyMatch(p -> p == null)) return Optional.empty();

            pending.remove(key);
            Pending complete = share;
            return decode(String.join("", complete.parts())).map(anchor -> new Assembled(anchor, complete.sender()));
        }
    }

    static byte[] toBytes(SharedAnchor anchor) {
        ByteBuf buf = Unpooled.buffer();
        try {
            COMPACT_CODEC.encode(buf, anchor);
            byte[] raw = new byte[buf.readableBytes()];
            buf.readBytes(raw);
            return raw;
        } finally {
            buf.release();
        }
    }

    private static Optional<SharedAnchor> decode(String data) {
        ByteBuf buf = null;
        try {
            buf = Unpooled.wrappedBuffer(inflate(Base64.getUrlDecoder().decode(data)));
            SharedAnchor anchor = COMPACT_CODEC.decode(buf);
            if (buf.isReadable() || anchor.validate().isPresent()) return Optional.empty();
            return Optional.of(anchor);
        } catch (RuntimeException | DataFormatException e) {
            // Anything that merely looks like a share is untrusted chat input: ignore it
            return Optional.empty();
        } finally {
            if (buf != null) buf.release();
        }
    }

    private static void write(ByteBuf buf, SharedAnchor anchor) {
        NAME.encode(buf, anchor.name());
        KEY.encode(buf, shorten(anchor.dimension().toString()));
        writeSigned(buf, anchor.pos().getX());
        writeSigned(buf, anchor.pos().getY());
        writeSigned(buf, anchor.pos().getZ());
        ByteBufCodecs.VAR_INT.encode(buf, anchor.radius());

        // sorted, so shared prefixes sit next to each other and compress better
        Map<String, Double> sounds = new TreeMap<>(anchor.sounds());
        ByteBufCodecs.VAR_INT.encode(buf, sounds.size());
        sounds.forEach((sound, volume) -> {
            KEY.encode(buf, shorten(sound));
            buf.writeByte((int) Math.round(Math.clamp(volume, 0D, 1D) * VOLUME_STEPS));
        });
        ByteBufCodecs.VAR_INT.encode(buf, anchor.blocks().size());
        anchor.blocks().forEach(block -> KEY.encode(buf, block));
    }

    private static SharedAnchor read(ByteBuf buf) {
        String name = NAME.decode(buf);
        Identifier dimension = Identifier.parse(expand(KEY.decode(buf)));
        BlockPos pos = new BlockPos(readSigned(buf), readSigned(buf), readSigned(buf));
        int radius = ByteBufCodecs.VAR_INT.decode(buf);

        int soundCount = readCount(buf);
        Map<String, Double> sounds = new HashMap<>(soundCount);
        for (int i = 0; i < soundCount; i++) {
            sounds.put(expand(KEY.decode(buf)), buf.readUnsignedByte() / VOLUME_STEPS);
        }
        int blockCount = readCount(buf);
        List<String> blocks = new ArrayList<>(blockCount);
        for (int i = 0; i < blockCount; i++) {
            blocks.add(KEY.decode(buf));
        }
        return new SharedAnchor(name, dimension, pos, radius, sounds, blocks);
    }

    /**
     * Vanilla ids lose their namespace ("entity.bat.ambient"), other mods keep theirs ("create:fan").
     * ESM's mod-wide entries ("create") get a trailing ':' so they are not mistaken for vanilla ids;
     * sound paths and mod ids cannot contain ':' themselves, so this stays unambiguous.
     */
    private static String shorten(String id) {
        if (id.equals(SharedAnchor.EVERYTHING)) return id;
        if (id.startsWith(VANILLA_NAMESPACE)) return id.substring(VANILLA_NAMESPACE.length());
        return id.contains(":") ? id : id + ":";
    }

    private static String expand(String id) {
        if (id.equals(SharedAnchor.EVERYTHING)) return id;
        if (id.endsWith(":")) return id.substring(0, id.length() - 1);
        return id.contains(":") ? id : VANILLA_NAMESPACE + id;
    }

    private static void writeSigned(ByteBuf buf, int value) {
        ByteBufCodecs.VAR_INT.encode(buf, (value << 1) ^ (value >> 31));
    }

    private static int readSigned(ByteBuf buf) {
        int zigzag = ByteBufCodecs.VAR_INT.decode(buf);
        return (zigzag >>> 1) ^ -(zigzag & 1);
    }

    private static int readCount(ByteBuf buf) {
        int count = ByteBufCodecs.VAR_INT.decode(buf);
        if (count < 0 || count > SharedAnchor.MAX_ENTRIES) throw new DecoderException("too many entries: " + count);
        return count;
    }

    private static String randomId() {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        StringBuilder id = new StringBuilder(ID_LENGTH);
        for (int i = 0; i < ID_LENGTH; i++) {
            id.append(Character.forDigit(random.nextInt(36), 36));
        }
        return id.toString();
    }

    private static byte[] deflate(byte[] raw) {
        Deflater deflater = new Deflater(Deflater.BEST_COMPRESSION, true);
        try {
            deflater.setInput(raw);
            deflater.finish();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] chunk = new byte[512];
            while (!deflater.finished()) {
                out.write(chunk, 0, deflater.deflate(chunk));
            }
            return out.toByteArray();
        } finally {
            deflater.end();
        }
    }

    private static byte[] inflate(byte[] compressed) throws DataFormatException {
        Inflater inflater = new Inflater(true);
        try {
            // nowrap mode wants one trailing dummy byte (see Inflater javadoc)
            inflater.setInput(Arrays.copyOf(compressed, compressed.length + 1));
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] chunk = new byte[1024];
            while (!inflater.finished()) {
                int n = inflater.inflate(chunk);
                if (n == 0 && (inflater.needsInput() || inflater.needsDictionary()))
                    throw new DataFormatException("truncated anchor data");
                out.write(chunk, 0, n);
                if (out.size() > MAX_RAW_BYTES) throw new DataFormatException("anchor data too large");
            }
            return out.toByteArray();
        } finally {
            inflater.end();
        }
    }
}
