package com.marcofotos.client;

import com.marcofotos.MarcoFotos;
import com.marcofotos.MediaSettings;
import com.marcofotos.Net;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.ResourceLocation;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ClientMediaCache {
    public enum State { LOADING, READY, FAILED }

    public static class Entry {
        public final String id;
        public volatile State state = State.LOADING;
        public final List<ResourceLocation> frames = new ArrayList<>();
        public final List<Integer> delays = new ArrayList<>();
        public int w, h, totalMs;
        byte[][] parts;
        int got;
        boolean decoding;

        Entry(String id) { this.id = id; }

        public int frameIndex(long elapsedMs, MediaSettings s) {
            int n = frames.size();
            if (n <= 1 || totalMs <= 0) return 0;
            double total = totalMs;
            double t = Math.max(0, elapsedMs) * (double) s.speed;
            double cycle = s.pingpong ? total * 2 : total;
            double tt = s.loop ? t % cycle : Math.min(t, cycle - 0.001);
            if (s.pingpong && tt >= total) tt = cycle - tt;
            if (tt < 0) tt = 0;
            if (tt >= total) tt = total - 0.001;
            if (s.reverse) tt = total - 0.001 - tt;
            double acc = 0;
            for (int i = 0; i < n; i++) {
                acc += delays.get(i);
                if (tt < acc) return i;
            }
            return n - 1;
        }
    }

    private static final Map<String, Entry> MAP = new HashMap<>();

    public static Entry get(String id) {
        if (id == null || id.isEmpty()) return null;
        Entry e = MAP.get(id);
        if (e == null) {
            e = new Entry(id);
            MAP.put(id, e);
            Net.CHANNEL.sendToServer(new Net.RequestMedia(id));
        }
        return e;
    }

    public static void retryIfFailed(String id) {
        Entry e = MAP.get(id);
        if (e != null && e.state == State.FAILED && !e.decoding) {
            release(e);
            MAP.remove(id);
        }
    }

    /** Medio importado localmente: se decodifica directo, sin pedirlo al servidor. */
    public static void putLocal(String id, byte[] bytes) {
        Entry e = MAP.get(id);
        if (e != null && e.state == State.READY) return;
        if (e == null) {
            e = new Entry(id);
            MAP.put(id, e);
        }
        decodeAsync(e, bytes);
    }

    public static void onChunk(String id, int index, int total, byte[] data) {
        Entry e = MAP.get(id);
        if (e == null || e.state != State.LOADING || e.decoding) return;
        if (e.parts == null) e.parts = new byte[total][];
        if (e.parts.length != total || index < 0 || index >= total) return;
        if (e.parts[index] == null) {
            e.parts[index] = data;
            e.got++;
        }
        if (e.got == total) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            for (byte[] p : e.parts) out.write(p, 0, p.length);
            e.parts = null;
            decodeAsync(e, out.toByteArray());
        }
    }

    public static void onMissing(String id) {
        Entry e = MAP.get(id);
        if (e != null && e.state == State.LOADING && !e.decoding) e.state = State.FAILED;
    }

    private static void decodeAsync(Entry e, byte[] bytes) {
        if (e.decoding) return;
        e.decoding = true;
        Thread t = new Thread(() -> {
            try {
                MediaDecoder.Decoded d = MediaDecoder.decode(bytes);
                Minecraft.getInstance().execute(() -> finish(e, d));
            } catch (Throwable ex) {
                MarcoFotos.LOGGER.warn("No se pudo decodificar el medio {}", e.id, ex);
                Minecraft.getInstance().execute(() -> {
                    e.state = State.FAILED;
                    e.decoding = false;
                });
            }
        }, "MarcoFotos-Decoder");
        t.setDaemon(true);
        t.start();
    }

    private static void finish(Entry e, MediaDecoder.Decoded d) {
        TextureManager tm = Minecraft.getInstance().getTextureManager();
        int i = 0;
        int total = 0;
        for (NativeImage img : d.frames) {
            DynamicTexture tex = new DynamicTexture(img);
            tex.setFilter(true, false);
            ResourceLocation rl = new ResourceLocation(MarcoFotos.MODID, "media/" + e.id + "/" + i);
            tm.register(rl, tex);
            e.frames.add(rl);
            i++;
        }
        for (int delay : d.delays) {
            e.delays.add(delay);
            total += delay;
        }
        e.w = d.w;
        e.h = d.h;
        e.totalMs = total;
        e.state = State.READY;
        e.decoding = false;
    }

    private static void release(Entry e) {
        TextureManager tm = Minecraft.getInstance().getTextureManager();
        for (ResourceLocation rl : e.frames) tm.release(rl);
        e.frames.clear();
    }

    public static void clear() {
        for (Entry e : MAP.values()) release(e);
        MAP.clear();
    }
}
