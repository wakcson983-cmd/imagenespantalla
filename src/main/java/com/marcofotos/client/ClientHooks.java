package com.marcofotos.client;

import com.marcofotos.MarcoFotoBlockEntity;
import com.marcofotos.MediaData;
import com.marcofotos.MediaSettings;
import com.marcofotos.Net;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

/** Todo lo que toca clases del cliente se llama desde aqui (via DistExecutor). */
public class ClientHooks {

    public static void openItemScreen(InteractionHand hand, boolean imagen) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        ItemStack st = mc.player.getItemInHand(hand);
        CompoundTag t = imagen ? st.getTag() : st.getTagElement("BlockEntityTag");
        MediaData d = t == null ? new MediaData("", new MediaSettings()) : MediaData.readFrom(t);
        mc.setScreen(new MediaScreen(imagen ? MediaScreen.Mode.IMAGEN : MediaScreen.Mode.MARCO_ITEM, hand, null, d));
    }

    public static void openBlockScreen(BlockPos pos) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        if (mc.level.getBlockEntity(pos) instanceof MarcoFotoBlockEntity be) {
            MediaData d = new MediaData(be.data.id, be.data.settings.copy());
            mc.setScreen(new MediaScreen(MediaScreen.Mode.MARCO_BLOCK, null, pos, d));
        }
    }

    public static void onMediaChunk(Net.MediaChunk m) {
        ClientMediaCache.onChunk(m.id, m.index, m.total, m.data);
    }

    public static void onMediaMissing(Net.MediaMissing m) {
        ClientMediaCache.onMissing(m.id);
    }

    public static void onShow(Net.ShowMediaS2C m) {
        ClientDisplays.add(m.showId, m.id, m.settings);
    }

    public static void onHide() {
        ClientDisplays.hideAll();
    }
}
