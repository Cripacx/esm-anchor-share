package com.cripacx.esmanchorshare;

import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class AnchorShare {
    public static final String MOD_ID = "esmanchorshare";
    public static final String ESM_MOD_ID = "extremesoundmuffler";
    public static final Logger LOG = LoggerFactory.getLogger("ESM Anchor Share");

    private AnchorShare() {
    }

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }
}
