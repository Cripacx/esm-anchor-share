package com.cripacx.esmanchorshare.client;

import com.cripacx.esmanchorshare.AnchorShare;
import com.leobeliik.extremesoundmuffler.Constants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import java.util.function.Supplier;

/** 11x11 icon button drawn like ESM's own toolbar buttons, following ESM's light/dark theme. */
public class AnchorIconButton extends Button {

    public enum Icon {
        SHARE(0), PUBLISH(11);

        private final int u;

        Icon(int u) {
            this.u = u;
        }
    }

    private static final int SIZE = 11;
    private static final int TEXTURE_SIZE = 32;
    private static final Identifier DARK = AnchorShare.id("textures/gui/dark/icons.png");
    private static final Identifier LIGHT = AnchorShare.id("textures/gui/light/icons.png");

    private final Icon icon;

    public AnchorIconButton(int x, int y, Icon icon, Component tooltip, Runnable action) {
        super(x, y, SIZE, SIZE, tooltip, button -> action.run(), Supplier::get);
        this.icon = icon;
    }

    @Override
    protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        int v = isHovered() && active ? SIZE : 0;
        graphics.blit(RenderPipelines.GUI_TEXTURED, Constants.darkMode ? DARK : LIGHT, getX(), getY(), icon.u, v, SIZE, SIZE, TEXTURE_SIZE, TEXTURE_SIZE);

        // same tooltip placement as ESMButton
        if (isHovered() && isActive()) {
            Font font = Minecraft.getInstance().font;
            graphics.setTooltipForNextFrame(font, getMessage(), getX() - font.width(getMessage()) / 2, getY() - 1);
        }
    }
}
