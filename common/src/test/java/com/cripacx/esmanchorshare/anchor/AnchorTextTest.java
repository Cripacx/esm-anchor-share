package com.cripacx.esmanchorshare.anchor;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;

class AnchorTextTest {

    private static final Identifier OVERWORLD = Identifier.withDefaultNamespace("overworld");
    private static final List<String> MOBS = List.of("allay", "armadillo", "axolotl", "bat", "bee", "blaze", "camel", "cat",
            "chicken", "cod", "cow", "creeper", "dolphin", "donkey", "drowned", "enderman", "evoker", "fox", "frog", "ghast",
            "goat", "guardian", "hoglin", "horse", "husk", "llama", "mooshroom", "ocelot", "panda", "parrot", "phantom", "pig",
            "piglin", "pillager", "rabbit", "ravager", "salmon", "sheep", "skeleton", "slime", "sniffer", "spider", "squid",
            "stray", "strider", "turtle", "villager", "witch", "wolf", "zombie");

    /** Realistic vanilla sound ids (up to 200); volumes on the 1/200 grid so they survive the compact format unchanged. */
    static SharedAnchor anchorWithSounds(int count) {
        if (count > MOBS.size() * 4) throw new IllegalArgumentException("at most " + MOBS.size() * 4 + " distinct sounds");
        Map<String, Double> sounds = new LinkedHashMap<>();
        String[] kinds = {"ambient", "hurt", "death", "step"};
        for (int i = 0; sounds.size() < count; i++) {
            sounds.put("minecraft:entity." + MOBS.get(i % MOBS.size()) + "." + kinds[i / MOBS.size()], (i % 19) * 10 / 200D);
        }
        return new SharedAnchor("Mob Farm", OVERWORLD, new BlockPos(-1520, 72, 3310), 24, sounds, List.of("Piston", "Hopper"));
    }

    private static Optional<SharedAnchor> receive(List<String> messages, String sender) {
        AnchorText.Assembler assembler = new AnchorText.Assembler();
        Optional<AnchorText.Assembled> result = Optional.empty();
        for (String message : messages) {
            AnchorText.Part part = AnchorText.findPart("<" + sender + "> " + message).orElseThrow();
            assertTrue(result.isEmpty(), "must not complete before the last part");
            result = assembler.accept(part, sender, 0);
        }
        return result.map(AnchorText.Assembled::anchor);
    }

    @Test
    void smallAnchorFitsInOneMessage() {
        SharedAnchor anchor = anchorWithSounds(8);
        List<String> messages = AnchorText.encode(anchor).orElseThrow();
        assertEquals(1, messages.size());
        assertEquals(anchor, receive(messages, "Steve").orElseThrow());
    }

    @Test
    void largeAnchorIsSplitAndReassembledInAnyOrder() {
        SharedAnchor anchor = anchorWithSounds(60);
        List<String> messages = new ArrayList<>(AnchorText.encode(anchor).orElseThrow());
        assertTrue(messages.size() > 1 && messages.size() <= AnchorText.MAX_PARTS, () -> "parts: " + messages.size());
        messages.forEach(m -> assertTrue(m.length() <= AnchorText.MAX_CHAT_LENGTH, () -> "too long: " + m.length()));

        Collections.reverse(messages);
        assertEquals(anchor, receive(messages, "Steve").orElseThrow());
    }

    @Test
    void partsOfDifferentSendersAndSharesDoNotMix() {
        List<String> messages = AnchorText.encode(anchorWithSounds(60)).orElseThrow();
        AnchorText.Assembler assembler = new AnchorText.Assembler();
        for (int i = 0; i < messages.size() - 1; i++) {
            assembler.accept(AnchorText.findPart(messages.get(i)).orElseThrow(), "Steve", 0);
        }
        AnchorText.Part last = AnchorText.findPart(messages.getLast()).orElseThrow();
        assertTrue(assembler.accept(last, "Alex", 0).isEmpty(), "Alex cannot complete Steve's share");
        assertTrue(assembler.accept(last, "Steve", 31_000).isEmpty(), "incomplete shares expire after 30 seconds");
    }

    @Test
    void tooLargeAnchorIsRefused() {
        assertTrue(AnchorText.encode(anchorWithSounds(200)).isEmpty());
    }

    @Test
    void garbageIsIgnored() {
        assertTrue(AnchorText.findPart("esma2:abcd:3/2:AAAA").isEmpty(), "part index above total");
        assertTrue(AnchorText.findPart("esma2:abcd:1/9:AAAA").isEmpty(), "more parts than allowed");
        assertTrue(AnchorText.findPart("just chatting").isEmpty());

        AnchorText.Part bogus = AnchorText.findPart("esma2:abcd:1/1:not-really-an-anchor").orElseThrow();
        assertTrue(new AnchorText.Assembler().accept(bogus, "Steve", 0).isEmpty());

        SharedAnchor invalid = new SharedAnchor("Huge", OVERWORLD, BlockPos.ZERO, 999_999, Map.of(), List.of());
        assertTrue(receive(AnchorText.encode(invalid).orElseThrow(), "Steve").isEmpty(), "invalid radius must be rejected");
    }

    @Test
    void compactFormatKeepsModEntriesAndEverything() {
        SharedAnchor anchor = new SharedAnchor("Base", Identifier.fromNamespaceAndPath("twilightforest", "twilight_forest"),
                new BlockPos(5, -60, -5), 8,
                Map.of(SharedAnchor.EVERYTHING, 0.25D, "create", 0.5D, "create:fan", 0D, "minecraft:block.anvil.land", 0.1D),
                List.of());
        assertEquals(anchor, receive(AnchorText.encode(anchor).orElseThrow(), "Steve").orElseThrow());
    }
}
