package com.marcofotos;

import com.marcofotos.client.ClientHooks;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class Net {
    private static final String PROTOCOL = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(MarcoFotos.MODID, "main"), () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);

    public static void register() {
        int i = 0;
        CHANNEL.registerMessage(i++, UploadChunk.class, UploadChunk::encode, UploadChunk::decode, UploadChunk::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(i++, RequestMedia.class, RequestMedia::encode, RequestMedia::decode, RequestMedia::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(i++, ShowMedia.class, ShowMedia::encode, ShowMedia::decode, ShowMedia::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(i++, HideAll.class, HideAll::encode, HideAll::decode, HideAll::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(i++, SetItemMedia.class, SetItemMedia::encode, SetItemMedia::decode, SetItemMedia::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(i++, SetBlockMedia.class, SetBlockMedia::encode, SetBlockMedia::decode, SetBlockMedia::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(i++, MediaChunk.class, MediaChunk::encode, MediaChunk::decode, MediaChunk::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(i++, MediaMissing.class, MediaMissing::encode, MediaMissing::decode, MediaMissing::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(i++, ShowMediaS2C.class, ShowMediaS2C::encode, ShowMediaS2C::decode, ShowMediaS2C::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(i++, HideS2C.class, HideS2C::encode, HideS2C::decode, HideS2C::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    }

    private static void serverWork(Supplier<NetworkEvent.Context> sup, Consumer<ServerPlayer> work) {
        NetworkEvent.Context c = sup.get();
        c.enqueueWork(() -> {
            ServerPlayer p = c.getSender();
            if (p != null) work.accept(p);
        });
        c.setPacketHandled(true);
    }

    // ---------------- Cliente -> Servidor ----------------

    public static class UploadChunk {
        public final String id;
        public final int index, total;
        public final byte[] data;

        public UploadChunk(String id, int index, int total, byte[] data) {
            this.id = id; this.index = index; this.total = total; this.data = data;
        }

        public void encode(FriendlyByteBuf buf) {
            buf.writeUtf(id, 64);
            buf.writeVarInt(index);
            buf.writeVarInt(total);
            buf.writeByteArray(data);
        }

        public static UploadChunk decode(FriendlyByteBuf buf) {
            return new UploadChunk(buf.readUtf(64), buf.readVarInt(), buf.readVarInt(), buf.readByteArray(32000));
        }

        public void handle(Supplier<NetworkEvent.Context> ctx) {
            serverWork(ctx, p -> ServerMedia.receive(p, id, index, total, data));
        }
    }

    public static class RequestMedia {
        public final String id;

        public RequestMedia(String id) { this.id = id; }

        public void encode(FriendlyByteBuf buf) { buf.writeUtf(id, 64); }

        public static RequestMedia decode(FriendlyByteBuf buf) { return new RequestMedia(buf.readUtf(64)); }

        public void handle(Supplier<NetworkEvent.Context> ctx) {
            serverWork(ctx, p -> ServerMedia.sendTo(p, id));
        }
    }

    public static class ShowMedia {
        public final String id;
        public final MediaSettings settings;

        public ShowMedia(String id, MediaSettings settings) { this.id = id; this.settings = settings; }

        public void encode(FriendlyByteBuf buf) {
            buf.writeUtf(id, 64);
            settings.write(buf);
        }

        public static ShowMedia decode(FriendlyByteBuf buf) {
            return new ShowMedia(buf.readUtf(64), MediaSettings.read(buf));
        }

        public void handle(Supplier<NetworkEvent.Context> ctx) {
            serverWork(ctx, p -> ServerMedia.show(p, id, settings));
        }
    }

    public static class HideAll {
        public HideAll() {}

        public void encode(FriendlyByteBuf buf) {}

        public static HideAll decode(FriendlyByteBuf buf) { return new HideAll(); }

        public void handle(Supplier<NetworkEvent.Context> ctx) {
            serverWork(ctx, ServerMedia::hideAll);
        }
    }

    public static class SetItemMedia {
        public final int hand;
        public final String id;
        public final MediaSettings settings;

        public SetItemMedia(int hand, String id, MediaSettings settings) {
            this.hand = hand; this.id = id; this.settings = settings;
        }

        public void encode(FriendlyByteBuf buf) {
            buf.writeVarInt(hand);
            buf.writeUtf(id, 64);
            settings.write(buf);
        }

        public static SetItemMedia decode(FriendlyByteBuf buf) {
            return new SetItemMedia(buf.readVarInt(), buf.readUtf(64), MediaSettings.read(buf));
        }

        public void handle(Supplier<NetworkEvent.Context> ctx) {
            serverWork(ctx, p -> ServerMedia.setItem(p, hand, id, settings));
        }
    }

    public static class SetBlockMedia {
        public final BlockPos pos;
        public final String id;
        public final MediaSettings settings;

        public SetBlockMedia(BlockPos pos, String id, MediaSettings settings) {
            this.pos = pos; this.id = id; this.settings = settings;
        }

        public void encode(FriendlyByteBuf buf) {
            buf.writeBlockPos(pos);
            buf.writeUtf(id, 64);
            settings.write(buf);
        }

        public static SetBlockMedia decode(FriendlyByteBuf buf) {
            return new SetBlockMedia(buf.readBlockPos(), buf.readUtf(64), MediaSettings.read(buf));
        }

        public void handle(Supplier<NetworkEvent.Context> ctx) {
            serverWork(ctx, p -> ServerMedia.setBlock(p, pos, id, settings));
        }
    }

    // ---------------- Servidor -> Cliente ----------------

    public static class MediaChunk {
        public final String id;
        public final int index, total;
        public final byte[] data;

        public MediaChunk(String id, int index, int total, byte[] data) {
            this.id = id; this.index = index; this.total = total; this.data = data;
        }

        public void encode(FriendlyByteBuf buf) {
            buf.writeUtf(id, 64);
            buf.writeVarInt(index);
            buf.writeVarInt(total);
            buf.writeByteArray(data);
        }

        public static MediaChunk decode(FriendlyByteBuf buf) {
            return new MediaChunk(buf.readUtf(64), buf.readVarInt(), buf.readVarInt(), buf.readByteArray(900000));
        }

        public void handle(Supplier<NetworkEvent.Context> ctx) {
            NetworkEvent.Context c = ctx.get();
            c.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientHooks.onMediaChunk(this)));
            c.setPacketHandled(true);
        }
    }

    public static class MediaMissing {
        public final String id;

        public MediaMissing(String id) { this.id = id; }

        public void encode(FriendlyByteBuf buf) { buf.writeUtf(id, 64); }

        public static MediaMissing decode(FriendlyByteBuf buf) { return new MediaMissing(buf.readUtf(64)); }

        public void handle(Supplier<NetworkEvent.Context> ctx) {
            NetworkEvent.Context c = ctx.get();
            c.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientHooks.onMediaMissing(this)));
            c.setPacketHandled(true);
        }
    }

    public static class ShowMediaS2C {
        public final UUID showId;
        public final String id;
        public final MediaSettings settings;

        public ShowMediaS2C(UUID showId, String id, MediaSettings settings) {
            this.showId = showId; this.id = id; this.settings = settings;
        }

        public void encode(FriendlyByteBuf buf) {
            buf.writeUUID(showId);
            buf.writeUtf(id, 64);
            settings.write(buf);
        }

        public static ShowMediaS2C decode(FriendlyByteBuf buf) {
            return new ShowMediaS2C(buf.readUUID(), buf.readUtf(64), MediaSettings.read(buf));
        }

        public void handle(Supplier<NetworkEvent.Context> ctx) {
            NetworkEvent.Context c = ctx.get();
            c.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientHooks.onShow(this)));
            c.setPacketHandled(true);
        }
    }

    public static class HideS2C {
        public HideS2C() {}

        public void encode(FriendlyByteBuf buf) {}

        public static HideS2C decode(FriendlyByteBuf buf) { return new HideS2C(); }

        public void handle(Supplier<NetworkEvent.Context> ctx) {
            NetworkEvent.Context c = ctx.get();
            c.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientHooks.onHide()));
            c.setPacketHandled(true);
        }
    }
}
