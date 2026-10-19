package com.cripacx.esmanchorshare.mixin;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import org.spongepowered.asm.service.MixinService;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import static com.cripacx.esmanchorshare.AnchorShare.LOG;

/**
 * Fabric cannot declare a client-only dependency, so a client may run the addon without ESM.
 * Skip the ESM mixin in that case instead of crashing; any other mixin failure stays fatal.
 */
public class MixinPlugin implements IMixinConfigPlugin {

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        try {
            MixinService.getService().getBytecodeProvider().getClassNode(targetClassName);
            return true;
        } catch (ClassNotFoundException | IOException e) {
            LOG.warn("Extreme Sound Muffler is not installed, skipping {}", mixinClassName);
            return false;
        }
    }

    @Override
    public void onLoad(String mixinPackage) {
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }
}
