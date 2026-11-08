package com.marcofotos;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.PacketDistributor;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ServerMedia {
    private static final int S2C_CHUNK = 500_000;

    private static Path dir;
    private static final Map<UUID, Map<String, Upload>> UPLOADS = new HashMap<>();

    private static class Upload {
        final byte[][] parts;
        int got;
        long size;

        Upload(int total) { parts = new byte[total][]; }
    }

    public static void init(MinecraftServer server) {
        dir = server.getWorldPath(LevelResource.ROOT).resolve("marcofotos");
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            MarcoFotos.LOGGER.error("No se pudo crear la carpeta de medios", e);
        }
        UPLOADS.clear();
    }

    public static void clearPlayer(UUID id) {
        UPLOADS.remove(id);
    }

    public static boolean exists(String id) {
        return dir != null && MediaData.validId(id) && Files.exists(dir.resolve(id + ".bin"));
    }

    private static boolean allowed(ServerPlayer p) {
        if (Config.REQUIRE_OP.get() && !p.hasPermissions(2)) {
            p.sendSystemMessage(Component.literal("Solo los operadores pueden hacer esto."));
            return false;
        }
        return true;
    }

    public static void receive(ServerPlayer p, String id, int index, int total, byte[] data) {
        if (dir == null || !MediaData.validId(id)) return;
        int maxChunks = MediaData.MAX_BYTES / MediaData.CHUNK + 2;
        if (total < 1 || total > maxChunks || index < 0 || index >= total || data.length > MediaData.CHUNK + 1024) return;

        Map<String, Upload> mine = UPLOADS.computeIfAbsent(p.getUUID(), k -> new HashMap<>());
        Upload up;
        if (index == 0) {
            if (mine.size() >= 4) mine.clear();
            up = new Upload(total);
            mine.put(id, up);
        } else {
            up = mine.get(id);
            if (up == null || up.parts.length != total) return;
        }
        if (up.parts[index] == null) {
            up.parts[index] = data;
            up.got++;
            up.size += data.length;
        }
        if (up.size > MediaData.MAX_BYTES) {
            mine.remove(id);
            return;
        }
        if (up.got == total) {
            mine.remove(id);
            try {
                ByteArrayOutputStream out = new ByteArrayOutputStream((int) up.size);
                for (byte[] part : up.parts) out.write(part);
                byte[] all = out.toByteArray();
                if (!sha1(all).equals(id)) {
                    MarcoFotos.LOGGER.warn("Medio rechazado: el hash no coincide ({})", id);
                    return;
                }
                Path file = dir.resolve(id + ".bin");
                if (!Files.exists(file)) Files.write(file, all);
            } catch (Exception e) {
                MarcoFotos.LOGGER.error("Error guardando medio", e);
            }
        }
    }

    public static String sha1(byte[] data) throws Exception {
        byte[] d = MessageDigest.getInstance("SHA-1").digest(data);
        StringBuilder sb = new StringBuilder();
        for (byte b : d) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    public static void sendTo(ServerPlayer p, String id) {
        if (!exists(id)) {
            Net.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), new Net.MediaMissing(id));
            return;
        }
        try {
            byte[] all = Files.readAllBytes(dir.resolve(id + ".bin"));
            int total = Math.max(1, (all.length + S2C_CHUNK - 1) / S2C_CHUNK);
            for (int i = 0; i < total; i++) {
                int from = i * S2C_CHUNK;
                int to = Math.min(all.length, from + S2C_CHUNK);
                byte[] part = new byte[to - from];
                System.arraycopy(all, from, part, 0, part.length);
                Net.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), new Net.MediaChunk(id, i, total, part));
            }
        } catch (IOException e) {
            Net.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), new Net.MediaMissing(id));
        }
    }

    public static void show(ServerPlayer p, String id, MediaSettings settings) {
        if (!allowed(p)) return;
        if (!exists(id)) {
            p.sendSystemMessage(Component.literal("La imagen todavia no esta en el servidor."));
            return;
        }
        settings.clamp();
        Net.CHANNEL.send(PacketDistributor.ALL.noArg(), new Net.ShowMediaS2C(UUID.randomUUID(), id, settings));
    }

    public static void hideAll(ServerPlayer p) {
        if (!allowed(p)) return;
        Net.CHANNEL.send(PacketDistributor.ALL.noArg(), new Net.HideS2C());
    }

    public static void setItem(ServerPlayer p, int handOrd, String id, MediaSettings settings) {
        if (!id.isEmpty() && !exists(id)) return;
        InteractionHand hand = handOrd == 1 ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        ItemStack st = p.getItemInHand(hand);
        settings.clamp();
        MediaData d = new MediaData(id, settings);
        if (st.getItem() instanceof ImagenItem) {
            d.writeTo(st.getOrCreateTag());
        } else if (st.getItem() instanceof MarcoFotoItem) {
            d.writeTo(st.getOrCreateTagElement("BlockEntityTag"));
        }
    }

    public static void setBlock(ServerPlayer p, net.minecraft.core.BlockPos pos, String id, MediaSettings settings) {
        if (!id.isEmpty() && !exists(id)) return;
        if (p.distanceToSqr(Vec3.atCenterOf(pos)) > 100) return;
        if (p.level().getBlockEntity(pos) instanceof MarcoFotoBlockEntity be) {
            settings.clamp();
            be.setData(new MediaData(id, settings));
        }
    }
}
