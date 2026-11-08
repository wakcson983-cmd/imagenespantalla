package com.marcofotos;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.util.Mth;

public class MediaSettings {

    public enum Anim {
        NINGUNA("Ninguna"), FUNDIDO("Fundido"), IZQUIERDA("Desliz. izquierda"), DERECHA("Desliz. derecha"),
        ARRIBA("Desliz. arriba"), ABAJO("Desliz. abajo"), ZOOM("Zoom (crece)"), ZOOM_INV("Zoom inverso"),
        GIRO("Giro"), REBOTE("Rebote"), ELASTICO("Elastico"), PARPADEO("Parpadeo"), VOLTEO("Volteo"),
        PERSIANA("Persiana"), GLITCH("Glitch");
        public final String label;
        Anim(String label) { this.label = label; }
    }

    public enum Easing {
        LINEAL("Lineal"), SUAVE("Suave"), ACELERA("Acelera"), FRENA("Frena");
        public final String label;
        Easing(String label) { this.label = label; }
    }

    public enum Pos {
        CENTRO("Centro", 0, 0), ARRIBA_IZQ("Arriba izq.", -1, -1), ARRIBA("Arriba", 0, -1), ARRIBA_DER("Arriba der.", 1, -1),
        IZQ("Izquierda", -1, 0), DER("Derecha", 1, 0),
        ABAJO_IZQ("Abajo izq.", -1, 1), ABAJO("Abajo", 0, 1), ABAJO_DER("Abajo der.", 1, 1);
        public final String label;
        public final int ax, ay;
        Pos(String label, int ax, int ay) { this.label = label; this.ax = ax; this.ay = ay; }
    }

    public Anim enter = Anim.FUNDIDO;
    public Anim exit = Anim.FUNDIDO;
    public Easing easing = Easing.SUAVE;
    public int enterMs = 600;
    public int exitMs = 600;
    public int durationSec = 10; // 0 = infinita
    public float speed = 1f;
    public boolean reverse = false;
    public boolean loop = true;
    public boolean pingpong = false;
    public boolean mirror = false;
    public boolean fill = false; // solo marcos
    public int opacity = 100;
    public int size = 60;
    public int rotation = 0;
    public Pos pos = Pos.CENTRO;

    public int effExitMs() {
        return exit == Anim.NINGUNA ? 1 : exitMs;
    }

    public MediaSettings clamp() {
        if (enter == null) enter = Anim.FUNDIDO;
        if (exit == null) exit = Anim.FUNDIDO;
        if (easing == null) easing = Easing.SUAVE;
        if (pos == null) pos = Pos.CENTRO;
        enterMs = Mth.clamp(enterMs, 100, 5000);
        exitMs = Mth.clamp(exitMs, 100, 5000);
        durationSec = Mth.clamp(durationSec, 0, 600);
        if (Float.isNaN(speed)) speed = 1f;
        speed = Mth.clamp(speed, 0.1f, 8f);
        opacity = Mth.clamp(opacity, 5, 100);
        size = Mth.clamp(size, 5, 100);
        rotation = Mth.clamp(rotation, 0, 360);
        return this;
    }

    public MediaSettings copy() {
        MediaSettings c = new MediaSettings();
        c.enter = enter; c.exit = exit; c.easing = easing;
        c.enterMs = enterMs; c.exitMs = exitMs; c.durationSec = durationSec;
        c.speed = speed; c.reverse = reverse; c.loop = loop; c.pingpong = pingpong;
        c.mirror = mirror; c.fill = fill;
        c.opacity = opacity; c.size = size; c.rotation = rotation; c.pos = pos;
        return c;
    }

    private static <E extends Enum<E>> E en(Class<E> cls, String name, E def) {
        try {
            return Enum.valueOf(cls, name);
        } catch (Exception e) {
            return def;
        }
    }

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putString("enter", enter.name());
        t.putString("exit", exit.name());
        t.putString("easing", easing.name());
        t.putInt("enterMs", enterMs);
        t.putInt("exitMs", exitMs);
        t.putInt("durationSec", durationSec);
        t.putFloat("speed", speed);
        t.putBoolean("reverse", reverse);
        t.putBoolean("loop", loop);
        t.putBoolean("pingpong", pingpong);
        t.putBoolean("mirror", mirror);
        t.putBoolean("fill", fill);
        t.putInt("opacity", opacity);
        t.putInt("size", size);
        t.putInt("rotation", rotation);
        t.putString("pos", pos.name());
        return t;
    }

    public static MediaSettings load(CompoundTag t) {
        MediaSettings s = new MediaSettings();
        s.enter = en(Anim.class, t.getString("enter"), s.enter);
        s.exit = en(Anim.class, t.getString("exit"), s.exit);
        s.easing = en(Easing.class, t.getString("easing"), s.easing);
        if (t.contains("enterMs")) s.enterMs = t.getInt("enterMs");
        if (t.contains("exitMs")) s.exitMs = t.getInt("exitMs");
        if (t.contains("durationSec")) s.durationSec = t.getInt("durationSec");
        if (t.contains("speed")) s.speed = t.getFloat("speed");
        s.reverse = t.getBoolean("reverse");
        s.loop = !t.contains("loop") || t.getBoolean("loop");
        s.pingpong = t.getBoolean("pingpong");
        s.mirror = t.getBoolean("mirror");
        s.fill = t.getBoolean("fill");
        if (t.contains("opacity")) s.opacity = t.getInt("opacity");
        if (t.contains("size")) s.size = t.getInt("size");
        if (t.contains("rotation")) s.rotation = t.getInt("rotation");
        s.pos = en(Pos.class, t.getString("pos"), s.pos);
        return s.clamp();
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeEnum(enter);
        buf.writeEnum(exit);
        buf.writeEnum(easing);
        buf.writeVarInt(enterMs);
        buf.writeVarInt(exitMs);
        buf.writeVarInt(durationSec);
        buf.writeFloat(speed);
        buf.writeBoolean(reverse);
        buf.writeBoolean(loop);
        buf.writeBoolean(pingpong);
        buf.writeBoolean(mirror);
        buf.writeBoolean(fill);
        buf.writeVarInt(opacity);
        buf.writeVarInt(size);
        buf.writeVarInt(rotation);
        buf.writeEnum(pos);
    }

    public static MediaSettings read(FriendlyByteBuf buf) {
        MediaSettings s = new MediaSettings();
        s.enter = buf.readEnum(Anim.class);
        s.exit = buf.readEnum(Anim.class);
        s.easing = buf.readEnum(Easing.class);
        s.enterMs = buf.readVarInt();
        s.exitMs = buf.readVarInt();
        s.durationSec = buf.readVarInt();
        s.speed = buf.readFloat();
        s.reverse = buf.readBoolean();
        s.loop = buf.readBoolean();
        s.pingpong = buf.readBoolean();
        s.mirror = buf.readBoolean();
        s.fill = buf.readBoolean();
        s.opacity = buf.readVarInt();
        s.size = buf.readVarInt();
        s.rotation = buf.readVarInt();
        s.pos = buf.readEnum(Pos.class);
        return s.clamp();
    }
}
