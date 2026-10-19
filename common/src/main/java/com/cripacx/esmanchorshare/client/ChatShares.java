package com.cripacx.esmanchorshare.client;

import com.cripacx.esmanchorshare.anchor.AnchorText;
import com.cripacx.esmanchorshare.anchor.SharedAnchor;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import org.jspecify.annotations.Nullable;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Turns received anchors (server payload or chat token) into a chat line with a [Copy] button. Main thread only. */
public final class ChatShares {

    private static final int MAX_REMEMBERED = 32;
    private static final int MAX_LISTED_SOUNDS = 8;
    private static final SimpleCommandExceptionType EXPIRED = new SimpleCommandExceptionType(
            Component.translatable("esmanchorshare.copy.expired"));

    private static final Map<Integer, SharedAnchor> received = new LinkedHashMap<>() {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Integer, SharedAnchor> eldest) {
            return size() > MAX_REMEMBERED;
        }
    };
    private static final AnchorText.Assembler assembler = new AnchorText.Assembler();
    private static int nextId;

    private ChatShares() {
    }

    /**
     * Hides every part of a chat-text share; once the last part arrives the anchor is shown with a [Copy] button.
     * @return true if the message was such a part and should be hidden
     */
    public static boolean onChatMessage(Component message, @Nullable String sender) {
        Optional<AnchorText.Part> part = AnchorText.findPart(message.getString());
        part.flatMap(p -> assembler.accept(p, sender, System.currentTimeMillis()))
                .ifPresent(done -> show(done.sender(), done.anchor()));
        return part.isPresent();
    }

    public static void show(@Nullable String sender, SharedAnchor anchor) {
        int id = nextId++;
        received.put(id, anchor);
        print(line(sender, anchor, id));
    }

    static SharedAnchor get(int id) throws CommandSyntaxException {
        SharedAnchor anchor = received.get(id);
        if (anchor == null) throw EXPIRED.create();
        return anchor;
    }

    static void print(Component message) {
        Minecraft.getInstance().gui.hud.getChat().addClientSystemMessage(message);
    }

    private static Component line(@Nullable String sender, SharedAnchor anchor, int id) {
        Component who = sender != null
                ? Component.literal(sender).withStyle(ChatFormatting.YELLOW)
                : Component.translatable("esmanchorshare.chat.someone");
        Component name = Component.literal(anchor.name()).withStyle(style -> style
                .withColor(ChatFormatting.AQUA)
                .withHoverEvent(new HoverEvent.ShowText(details(anchor))));
        Component copy = Component.translatable("esmanchorshare.chat.copy").withStyle(style -> style
                .withColor(ChatFormatting.GREEN)
                .withClickEvent(new ClickEvent.RunCommand("/esmanchor copy " + id))
                .withHoverEvent(new HoverEvent.ShowText(Component.translatable("esmanchorshare.chat.copy.hover"))));
        return Component.literal("[ESM] ").withStyle(ChatFormatting.GRAY)
                .append(Component.translatable("esmanchorshare.chat.shared", who, name).withStyle(ChatFormatting.WHITE))
                .append(" ")
                .append(copy);
    }

    private static Component details(SharedAnchor anchor) {
        MutableComponent text = Component.translatable("esmanchorshare.details",
                anchor.dimension().toString(), anchor.pos().getX(), anchor.pos().getY(), anchor.pos().getZ(), anchor.radius());
        List<String> sounds = anchor.sounds().keySet().stream().sorted().toList();
        for (String sound : sounds.subList(0, Math.min(sounds.size(), MAX_LISTED_SOUNDS))) {
            Component label = sound.equals(SharedAnchor.EVERYTHING)
                    ? Component.translatable("esmanchorshare.details.everything")
                    : Component.literal(sound);
            text.append("\n- ").append(label);
        }
        if (sounds.size() > MAX_LISTED_SOUNDS)
            text.append("\n").append(Component.translatable("esmanchorshare.details.more", sounds.size() - MAX_LISTED_SOUNDS));
        return text;
    }
}
