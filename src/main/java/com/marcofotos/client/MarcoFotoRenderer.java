package com.marcofotos.client;

import com.marcofotos.MarcoFotoBlock;
import com.marcofotos.MarcoFotoBlockEntity;
import com.marcofotos.MediaData;
import com.marcofotos.MediaSettings;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.Util;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

public class MarcoFotoRenderer implements BlockEntityRenderer<MarcoFotoBlockEntity> {

    public MarcoFotoRenderer(BlockEntityRendererProvider.Context ctx) {
    }

    @Override
    public void render(MarcoFotoBlockEntity be, float partialTick, PoseStack ps, MultiBufferSource buffers, int light, int overlay) {
        MediaData d = be.data;
        if (!d.hasMedia()) return;
        ClientMediaCache.Entry e = ClientMediaCache.get(d.id);
        if (e == null || e.state != ClientMediaCache.State.READY || e.frames.isEmpty() || e.w <= 0 || e.h <= 0) return;

        MediaSettings s = d.settings;
        int fi = e.frameIndex(Util.getMillis(), s);
        Direction f = be.getBlockState().getValue(MarcoFotoBlock.FACING);

        ps.pushPose();
        ps.translate(0.5, 0.5, 0.5);
        if (f == Direction.UP) {
            ps.mulPose(Axis.XP.rotationDegrees(90f));
        } else if (f == Direction.DOWN) {
            ps.mulPose(Axis.XP.rotationDegrees(-90f));
        } else {
            ps.mulPose(Axis.YP.rotationDegrees(-((f.toYRot() + 180f) % 360f)));
        }

        float hw = 0.375f, hh = 0.375f;
        if (!s.fill) {
            float ar = (float) e.w / (float) e.h;
            if (ar > 1f) hh = hw / ar;
            else hw = hh * ar;
        }
        float z = 0.4375f - 0.002f;
        float u0 = s.mirror ? 1f : 0f;
        float u1 = s.mirror ? 0f : 1f;

        VertexConsumer vc = buffers.getBuffer(RenderType.entityTranslucent(e.frames.get(Math.min(fi, e.frames.size() - 1))));
        PoseStack.Pose pose = ps.last();
        Matrix4f m = pose.pose();
        Matrix3f n = pose.normal();
        vertex(vc, m, n, hw, hh, z, u0, 0f, light);
        vertex(vc, m, n, hw, -hh, z, u0, 1f, light);
        vertex(vc, m, n, -hw, -hh, z, u1, 1f, light);
        vertex(vc, m, n, -hw, hh, z, u1, 0f, light);
        ps.popPose();
    }

    private static void vertex(VertexConsumer vc, Matrix4f m, Matrix3f n, float x, float y, float z, float u, float v, int light) {
        vc.vertex(m, x, y, z)
                .color(255, 255, 255, 255)
                .uv(u, v)
                .overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(light)
                .normal(n, 0f, 0f, -1f)
                .endVertex();
    }
}
