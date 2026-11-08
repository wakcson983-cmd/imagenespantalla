package com.marcofotos.client;

import com.marcofotos.Animations;
import com.marcofotos.MediaSettings;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

public class DisplayRenderer {

    /** Dibuja un medio dentro del rectangulo (bx,by,bw,bh) aplicando ajustes + estado de animacion. */
    public static void draw(GuiGraphics g, float bx, float by, float bw, float bh,
                            ClientMediaCache.Entry e, MediaSettings s, Animations.State st, long mediaElapsedMs) {
        if (e.frames.isEmpty() || e.w <= 0 || e.h <= 0) return;
        int fi = e.frameIndex(mediaElapsedMs, s);
        ResourceLocation tex = e.frames.get(Math.min(fi, e.frames.size() - 1));

        float maxW = bw * s.size / 100f;
        float maxH = bh * s.size / 100f;
        float fit = Math.min(maxW / e.w, maxH / e.h);
        float fx = s.fill ? maxW / e.w : fit;
        float fy = s.fill ? maxH / e.h : fit;
        float dw = e.w * fx, dh = e.h * fy;
        float m = Math.min(bw, bh) * 0.03f;

        float cx;
        if (s.pos.ax < 0) cx = bx + m + dw / 2f;
        else if (s.pos.ax > 0) cx = bx + bw - m - dw / 2f;
        else cx = bx + bw / 2f;
        float cy;
        if (s.pos.ay < 0) cy = by + m + dh / 2f;
        else if (s.pos.ay > 0) cy = by + bh - m - dh / 2f;
        else cy = by + bh / 2f;

        PoseStack ps = g.pose();
        ps.pushPose();
        ps.translate(cx + st.dx * bw, cy + st.dy * bh, 0f);
        ps.mulPose(Axis.ZP.rotationDegrees(s.rotation + st.rot));
        ps.scale(fx * st.sx * (s.mirror ? -1f : 1f), fy * st.sy, 1f);
        ps.translate(-e.w / 2f, -e.h / 2f, 0f);

        float a = Mth.clamp(st.alpha * s.opacity / 100f, 0f, 1f);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(1f, 1f, 1f, a);
        g.blit(tex, 0, 0, e.w, e.h, 0f, 0f, e.w, e.h, e.w, e.h);
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        RenderSystem.disableBlend();
        ps.popPose();
    }
}
