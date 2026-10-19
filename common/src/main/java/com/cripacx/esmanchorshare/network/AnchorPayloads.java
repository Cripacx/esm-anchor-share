package com.cripacx.esmanchorshare.network;

import com.cripacx.esmanchorshare.AnchorShare;
import com.cripacx.esmanchorshare.anchor.SharedAnchor;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import java.util.List;

/** All payloads of the addon. Both sides treat the channel as optional, so vanilla peers can still connect. */
public final class AnchorPayloads {

    /** Upper bound for synced server anchors, keeps the sync packet bounded. */
    public static final int MAX_SERVER_ANCHORS = 128;
    /** Vanilla drops the connection above 32767 (serverbound) and 1 MiB (clientbound); leave room for framing. */
    public static final int MAX_SERVERBOUND_BYTES = 32000;
    public static final int MAX_CLIENTBOUND_BYTES = 1_000_000;

    private AnchorPayloads() {
    }

    public static <T> int encodedSize(StreamCodec<ByteBuf, T> codec, T value) {
        ByteBuf buf = Unpooled.buffer();
        try {
            codec.encode(buf, value);
            return buf.readableBytes();
        } finally {
            buf.release();
        }
    }

    /** Client asks the server to broadcast one of its anchors in chat. */
    public record Share(SharedAnchor anchor) implements CustomPacketPayload {
        public static final Type<Share> TYPE = new Type<>(AnchorShare.id("share"));
        public static final StreamCodec<ByteBuf, Share> STREAM_CODEC = SharedAnchor.STREAM_CODEC.map(Share::new, Share::anchor);

        @Override
        public Type<Share> type() {
            return TYPE;
        }
    }

    /** Admin client creates or replaces a server anchor. */
    public record Publish(SharedAnchor anchor) implements CustomPacketPayload {
        public static final Type<Publish> TYPE = new Type<>(AnchorShare.id("publish"));
        public static final StreamCodec<ByteBuf, Publish> STREAM_CODEC = SharedAnchor.STREAM_CODEC.map(Publish::new, Publish::anchor);

        @Override
        public Type<Publish> type() {
            return TYPE;
        }
    }

    /** Server delivers an anchor some player shared in chat. */
    public record Shared(String sender, SharedAnchor anchor) implements CustomPacketPayload {
        public static final Type<Shared> TYPE = new Type<>(AnchorShare.id("shared"));
        public static final StreamCodec<ByteBuf, Shared> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.stringUtf8(64), Shared::sender,
                SharedAnchor.STREAM_CODEC, Shared::anchor,
                Shared::new);

        @Override
        public Type<Shared> type() {
            return TYPE;
        }
    }

    /** Server replaces the client's complete list of server anchors. */
    public record ServerAnchors(List<SharedAnchor> anchors) implements CustomPacketPayload {
        public static final Type<ServerAnchors> TYPE = new Type<>(AnchorShare.id("server_anchors"));
        public static final StreamCodec<ByteBuf, ServerAnchors> STREAM_CODEC = SharedAnchor.STREAM_CODEC
                .apply(ByteBufCodecs.list(MAX_SERVER_ANCHORS))
                .map(ServerAnchors::new, ServerAnchors::anchors);

        public ServerAnchors {
            anchors = List.copyOf(anchors);
        }

        @Override
        public Type<ServerAnchors> type() {
            return TYPE;
        }
    }
}
