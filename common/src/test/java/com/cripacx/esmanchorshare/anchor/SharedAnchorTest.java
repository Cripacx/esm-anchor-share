package com.cripacx.esmanchorshare.anchor;

import com.mojang.serialization.JsonOps;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class SharedAnchorTest {

    private static final Identifier OVERWORLD = Identifier.withDefaultNamespace("overworld");

    private static SharedAnchor farm() {
        return new SharedAnchor("Farm", OVERWORLD, new BlockPos(120, 64, -340), 16, Map.of(
                "minecraft:entity.chicken.ambient", 0D,
                "minecraft:entity.cow.ambient", 0.2D,
                "minecraft:entity.sheep.ambient", 0D,
                "minecraft:entity.pig.ambient", 0.1D,
                "minecraft:block.piston.extend", 0D,
                "minecraft:block.piston.contract", 0D), List.of("Piston"));
    }

    @Test
    void codecRoundTripsAndRejectsInvalidAnchors() {
        var json = SharedAnchor.CODEC.encodeStart(JsonOps.INSTANCE, farm()).getOrThrow();
        assertEquals(farm(), SharedAnchor.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow());

        SharedAnchor loud = new SharedAnchor("Loud", OVERWORLD, BlockPos.ZERO, 8, Map.of("minecraft:a", 5D), List.of());
        var loudJson = SharedAnchor.CODEC.encodeStart(JsonOps.INSTANCE, loud);
        assertTrue(loudJson.isError(), "volumes above 1 must not pass the codec");
    }

    @Test
    void validateChecksLimits() {
        assertTrue(farm().validate().isEmpty());
        assertTrue(new SharedAnchor(" ", OVERWORLD, BlockPos.ZERO, 8, Map.of(), List.of()).validate().isPresent());
        assertTrue(new SharedAnchor("x".repeat(SharedAnchor.MAX_NAME_LENGTH + 1), OVERWORLD, BlockPos.ZERO, 8, Map.of(), List.of()).validate().isPresent());
        assertTrue(new SharedAnchor("NaN", OVERWORLD, BlockPos.ZERO, 8, Map.of("minecraft:a", Double.NaN), List.of()).validate().isPresent());
        assertTrue(new SharedAnchor("Zero", OVERWORLD, BlockPos.ZERO, 0, Map.of(), List.of()).validate().isPresent());
    }

    @Test
    void volumeFollowsEsmPrecedence() {
        SharedAnchor anchor = new SharedAnchor("Mixed", OVERWORLD, BlockPos.ZERO, 8,
                Map.of("minecraft:entity.cow.ambient", 0.3D, "create", 0.5D), List.of());
        assertEquals(0.3D, anchor.volumeFor("minecraft:entity.cow.ambient", "minecraft"));
        assertEquals(0.5D, anchor.volumeFor("create:fan", "create"));
        assertEquals(1D, anchor.volumeFor("minecraft:entity.pig.ambient", "minecraft"));

        SharedAnchor everything = new SharedAnchor("All", OVERWORLD, BlockPos.ZERO, 8,
                Map.of(SharedAnchor.EVERYTHING, 0D, "minecraft:entity.cow.ambient", 0.3D), List.of());
        assertEquals(0D, everything.volumeFor("minecraft:entity.cow.ambient", "minecraft"));
    }

    @Test
    void coversOnlyItsDimensionAndRadius() {
        SharedAnchor anchor = farm();
        assertTrue(anchor.covers(OVERWORLD, new BlockPos(125, 64, -340)));
        assertFalse(anchor.covers(OVERWORLD, new BlockPos(140, 64, -340)));
        assertFalse(anchor.covers(Identifier.withDefaultNamespace("the_nether"), new BlockPos(120, 64, -340)));
    }
}
