package com.cripacx.esmanchorshare.client;

import com.cripacx.esmanchorshare.anchor.AnchorText;
import com.cripacx.esmanchorshare.anchor.SharedAnchor;
import com.cripacx.esmanchorshare.server.ServerAnchorManager;
import com.leobeliik.extremesoundmuffler.gui.MufflerScreen;
import com.leobeliik.extremesoundmuffler.interfaces.ISoundLists;
import com.leobeliik.extremesoundmuffler.utils.Anchor;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Drives the real client: ESM screen buttons, server round trip, copy, chat fallback and server anchor muffling. */
public class AnchorShareClientGameTest implements FabricClientGameTest {

    @Override
    public void runTest(ClientGameTestContext context) {
        try (TestSingleplayerContext world = context.worldBuilder().adjustSettings(settings -> settings.setAllowCommands(true)).create()) {
            world.getConnection().waitForChunksRender();
            BlockPos pos = context.computeOnClient(mc -> mc.player.blockPosition());

            // An own ESM anchor that mutes bat sounds around the player
            context.runOnClient(mc -> {
                Anchor anchor = new Anchor("Farm", pos, mc.level.dimension().identifier(), 16);
                anchor.addSound("minecraft:entity.bat.ambient", 0D);
                ISoundLists.anchorList.clear();
                ISoundLists.anchorList.add(anchor);
                MufflerScreen.open();
            });
            context.waitForScreen(MufflerScreen.class);
            context.clickScreenButton("main_screen.tab.anchors");
            context.waitTicks(2);
            hover(context, "esmanchorshare.gui.share");
            context.takeScreenshot("esmanchorshare-anchor-toolbar");

            // Share button -> server -> Shared payload -> chat line with [Copy]
            context.clickScreenButton("esmanchorshare.gui.share");
            context.waitFor(mc -> received(0));
            context.takeScreenshot("esmanchorshare-after-share");

            // Publish button (singleplayer owner is an operator) -> stored on the server -> synced back
            hover(context, "esmanchorshare.gui.publish");
            context.clickScreenButton("esmanchorshare.gui.publish");
            world.getServer().waitFor(server -> ServerAnchorManager.list(server).size() == 1);
            context.waitFor(mc -> ServerAnchorCache.muffling(sound(SoundEvents.BAT_AMBIENT, pos)) == 0D);

            // [Copy] runs this client command
            context.setScreen(() -> null);
            context.runOnClient(mc -> mc.player.connection.sendCommand("esmanchor copy 0"));
            context.waitFor(mc -> ISoundLists.anchorList.size() == 2
                    && ISoundLists.anchorList.stream().anyMatch(a -> a.getName().equals("Farm (2)")));

            // Chat-text fallback: a share split over several chat messages is hidden and reassembled into one [Copy] line
            Map<String, Double> sounds = new HashMap<>();
            String[] mobs = {"allay", "axolotl", "bee", "blaze", "camel", "cat", "chicken", "cow", "creeper", "dolphin",
                    "donkey", "drowned", "enderman", "fox", "frog", "goat", "horse", "llama", "panda", "parrot",
                    "pig", "rabbit", "sheep", "skeleton", "slime", "spider", "villager", "witch", "wolf", "zombie"};
            for (int i = 0; i < mobs.length; i++) sounds.put("minecraft:entity." + mobs[i] + ".ambient", i % 10 / 20D);
            sounds.put("create:fan", 0.5D);
            SharedAnchor big = new SharedAnchor("Big Farm", Identifier.withDefaultNamespace("overworld"), pos, 24, sounds, List.of());
            List<String> messages = AnchorText.encode(big).orElseThrow();
            check(messages.size() > 1, "test anchor should need several chat messages");
            context.runOnClient(mc -> messages.forEach(mc.player.connection::sendChat));
            context.waitFor(mc -> received(1));
            context.runOnClient(mc -> {
                try {
                    check(ChatShares.get(1).equals(big), "reassembled anchor differs");
                } catch (CommandSyntaxException e) {
                    throw new AssertionError(e);
                }
            });
            context.takeScreenshot("esmanchorshare-chat");

            // Server anchor muffles through ESM's own lookup once the personal anchors are gone
            context.runOnClient(mc -> {
                ISoundLists.anchorList.clear();
                check(Anchor.getMuffling(sound(SoundEvents.BAT_AMBIENT, pos)) == 0D, "server anchor should mute bats via ESM");
                check(Anchor.getMuffling(sound(SoundEvents.BLAZE_AMBIENT, pos)) == 1D, "other sounds must stay untouched");
                check(Anchor.getMuffling(sound(SoundEvents.BAT_AMBIENT, pos.offset(100, 0, 0))) == 1D, "outside the radius nothing changes");
            });

            // Admin removal reaches the client
            world.getServer().runCommand("esmadmin remove farm");
            context.waitFor(mc -> ServerAnchorCache.muffling(sound(SoundEvents.BAT_AMBIENT, pos)) == 1D);
        }
    }

    /** Asserts one of our buttons is shown and usable, and moves the cursor onto it so screenshots show its tooltip. */
    private static void hover(ClientGameTestContext context, String translationKey) {
        double[] cursor = context.computeOnClient(mc -> {
            AnchorIconButton button = mc.gui.screen().children().stream()
                    .filter(AnchorIconButton.class::isInstance).map(AnchorIconButton.class::cast)
                    .filter(b -> b.getMessage().getContents() instanceof TranslatableContents key && key.getKey().equals(translationKey))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError(translationKey + " button missing on " + mc.gui.screen()));
            check(button.visible && button.active, translationKey + " button should be visible and active");
            int scale = mc.getWindow().getGuiScale();
            return new double[]{(button.getX() + button.getWidth() / 2D) * scale, (button.getY() + button.getHeight() / 2D) * scale};
        });
        context.getInput().setCursorPos(cursor[0], cursor[1]);
        context.waitTicks(2);
    }

    private static SoundInstance sound(SoundEvent event, BlockPos pos) {
        return new SimpleSoundInstance(event, SoundSource.NEUTRAL, 1F, 1F, RandomSource.create(), pos);
    }

    private static boolean received(int id) {
        try {
            ChatShares.get(id);
            return true;
        } catch (CommandSyntaxException e) {
            return false;
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
