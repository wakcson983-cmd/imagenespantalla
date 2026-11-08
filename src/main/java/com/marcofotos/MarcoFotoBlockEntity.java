package com.marcofotos;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class MarcoFotoBlockEntity extends BlockEntity {
    public MediaData data = new MediaData("", new MediaSettings());

    public MarcoFotoBlockEntity(BlockPos pos, BlockState state) {
        super(ModRegistry.MARCO_BE.get(), pos, state);
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        data.writeTo(tag);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        data = MediaData.readFrom(tag);
    }

    @Override
    public CompoundTag getUpdateTag() {
        return saveWithoutMetadata();
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    public void setData(MediaData d) {
        this.data = d;
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }
}
