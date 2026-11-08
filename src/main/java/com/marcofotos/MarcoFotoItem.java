package com.marcofotos;

import com.marcofotos.client.ClientHooks;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class MarcoFotoItem extends BlockItem {
    public MarcoFotoItem(Block block, Properties props) {
        super(block, props);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientHooks.openItemScreen(hand, false));
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Player p = ctx.getPlayer();
        if (p != null && p.isShiftKeyDown()) {
            if (ctx.getLevel().isClientSide) {
                InteractionHand hand = ctx.getHand();
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientHooks.openItemScreen(hand, false));
            }
            return InteractionResult.sidedSuccess(ctx.getLevel().isClientSide);
        }
        return super.useOn(ctx);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tips, TooltipFlag flag) {
        tips.add(Component.literal("Click derecho en el aire (o Shift+click): elegir imagen").withStyle(net.minecraft.ChatFormatting.GRAY));
        tips.add(Component.literal("Click derecho en un bloque: colocar el marco").withStyle(net.minecraft.ChatFormatting.GRAY));
        var tag = stack.getTagElement("BlockEntityTag");
        if (tag != null && tag.contains("Media")) {
            tips.add(Component.literal("Imagen cargada").withStyle(net.minecraft.ChatFormatting.GREEN));
        }
    }
}
