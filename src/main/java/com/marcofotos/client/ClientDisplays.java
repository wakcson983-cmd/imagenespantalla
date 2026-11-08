package com.marcofotos.client;

import com.marcofotos.Animations;
import com.marcofotos.MediaSettings;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

/** Medios que se estan mostrando en pantalla (los que mando alguien con el item Imagen). */
public class ClientDisplays {
    private static class Active {
        UUID show;
        String id;
        MediaSettings s;
        long start = -1;
        long exitStart;
    }

    private static final List<Active> LIST = new ArrayList<>();

    public static void add(UUID show, String id, MediaSettings s) {
        ClientMediaCache.retryIfFailed(id);
        ClientMediaCache.get(id);
        Active a = new Active();
        a.show = show;
        a.id = id;
        a.s = s;
        a.exitStart = Animations.initialExitStart(s);
        LIST.add(a);
    }

    public static void hideAll() {
        long now = Util.getMillis();
        Iterator<Active> it = LIST.iterator();
        while (it.hasNext()) {
            Active a = it.next();
            if (a.start < 0) {
                it.remove();
            } else {
                a.exitStart = Math.min(a.exitStart, now - a.start);
            }
        }
    }

    public static void clear() {
        LIST.clear();
    }

    public static void render(GuiGraphics g, int sw, int sh) {
        if (LIST.isEmpty()) return;
        long now = Util.getMillis();
        Iterator<Active> it = LIST.iterator();
        while (it.hasNext()) {
            Active a = it.next();
            ClientMediaCache.Entry e = ClientMediaCache.get(a.id);
            if (e == null || e.state == ClientMediaCache.State.FAILED) {
                it.remove();
                continue;
            }
            if (e.state != ClientMediaCache.State.READY) continue;
            if (a.start < 0) a.start = now;
            long t = now - a.start;
            Animations.State st = Animations.state(a.s, t, a.exitStart);
            if (st == null) {
                it.remove();
                continue;
            }
            DisplayRenderer.draw(g, 0, 0, sw, sh, e, a.s, st, t);
        }
    }
}
