package com.marcofotos.client;

import com.marcofotos.MediaData;
import com.marcofotos.Net;
import net.minecraft.client.Minecraft;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class UploadManager {
    private static class Job {
        final String id;
        final byte[] bytes;
        final int total;
        final Runnable done;
        int next;

        Job(String id, byte[] bytes, int total, Runnable done) {
            this.id = id; this.bytes = bytes; this.total = total; this.done = done;
        }
    }

    private static final ArrayDeque<Job> QUEUE = new ArrayDeque<>();
    private static final Set<String> ON_SERVER = new HashSet<>();

    public static boolean isOnServer(String id) {
        return ON_SERVER.contains(id);
    }

    public static void upload(String id, byte[] bytes, Runnable done) {
        int total = Math.max(1, (bytes.length + MediaData.CHUNK - 1) / MediaData.CHUNK);
        QUEUE.add(new Job(id, bytes, total, done));
    }

    public static float progress() {
        Job j = QUEUE.peek();
        return j == null ? -1f : (float) j.next / (float) j.total;
    }

    public static void tick() {
        Job j = QUEUE.peek();
        if (j == null) return;
        if (Minecraft.getInstance().getConnection() == null) {
            QUEUE.clear();
            return;
        }
        int sent = 0;
        while (j.next < j.total && sent < 4) {
            int from = j.next * MediaData.CHUNK;
            int to = Math.min(j.bytes.length, from + MediaData.CHUNK);
            Net.CHANNEL.sendToServer(new Net.UploadChunk(j.id, j.next, j.total, Arrays.copyOfRange(j.bytes, from, to)));
            j.next++;
            sent++;
        }
        if (j.next >= j.total) {
            QUEUE.poll();
            ON_SERVER.add(j.id);
            if (j.done != null) j.done.run();
        }
    }

    public static void clear() {
        QUEUE.clear();
        ON_SERVER.clear();
    }
}
