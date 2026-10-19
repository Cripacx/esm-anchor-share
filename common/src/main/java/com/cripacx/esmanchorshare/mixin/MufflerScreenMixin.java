package com.cripacx.esmanchorshare.mixin;

import com.cripacx.esmanchorshare.client.AnchorActions;
import com.cripacx.esmanchorshare.client.AnchorIconButton;
import com.cripacx.esmanchorshare.client.EsmBridge;
import com.leobeliik.extremesoundmuffler.gui.MufflerScreen;
import com.leobeliik.extremesoundmuffler.gui.buttons.ESMButton;
import com.leobeliik.extremesoundmuffler.utils.Anchor;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Adds "share" and "publish to server" buttons to the anchor toolbar of ESM's screen, left of the edit button.
 * They follow the edit button's visibility, so they appear exactly when an anchor is selected.
 */
@Mixin(value = MufflerScreen.class, remap = false)
public abstract class MufflerScreenMixin extends Screen {

    @Shadow
    private Anchor anchor;
    @Shadow
    private ESMButton btnAnchorEdit;

    @Unique
    private AnchorIconButton esmanchorshare$share;
    @Unique
    private AnchorIconButton esmanchorshare$publish;

    protected MufflerScreenMixin(Component title) {
        super(title);
    }

    @Shadow
    private int getX() {
        throw new AssertionError();
    }

    @Shadow
    private int getY() {
        throw new AssertionError();
    }

    @Inject(method = "addAnchorButtons", at = @At("TAIL"))
    private void esmanchorshare$addButtons(CallbackInfo ci) {
        // ESM's edit and delete buttons sit at x+214 and x+226; continue that row to the left
        esmanchorshare$share = addRenderableWidget(new AnchorIconButton(getX() + 202, getY() + 20, AnchorIconButton.Icon.SHARE,
                Component.translatable("esmanchorshare.gui.share"),
                () -> AnchorActions.runFromScreen(() -> AnchorActions.share(EsmBridge.export(anchor)))));
        esmanchorshare$publish = addRenderableWidget(new AnchorIconButton(getX() + 190, getY() + 20, AnchorIconButton.Icon.PUBLISH,
                Component.translatable("esmanchorshare.gui.publish"),
                () -> AnchorActions.runFromScreen(() -> AnchorActions.publish(EsmBridge.export(anchor)))));
    }

    @Inject(method = "extractRenderState", at = @At("HEAD"))
    private void esmanchorshare$syncButtons(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTicks, CallbackInfo ci) {
        if (esmanchorshare$share == null || btnAnchorEdit == null) return;
        boolean shown = btnAnchorEdit.visible && anchor != null;
        esmanchorshare$share.visible = shown;
        esmanchorshare$share.active = btnAnchorEdit.active;
        esmanchorshare$publish.visible = shown && AnchorActions.canPublish();
        esmanchorshare$publish.active = btnAnchorEdit.active;
    }
}
