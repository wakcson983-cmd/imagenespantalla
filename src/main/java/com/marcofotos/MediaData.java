package com.marcofotos;

import net.minecraft.nbt.CompoundTag;

/** Un medio (id = sha1 del archivo) + su configuracion. */
public class MediaData {
    public static final int CHUNK = 28 * 1024;
    public static final int MAX_BYTES = 8 * 1024 * 1024;

    public String id;
    public MediaSettings settings;

    public MediaData(String id, MediaSettings settings) {
        this.id = id == null ? "" : id;
        this.settings = settings;
    }

    public boolean hasMedia() {
        return id != null && !id.isEmpty();
    }

    public static boolean validId(String id) {
        if (id == null || id.length() != 40) return false;
        for (int i = 0; i < 40; i++) {
            char c = id.charAt(i);
            boolean ok = (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f');
            if (!ok) return false;
        }
        return true;
    }

    public void writeTo(CompoundTag t) {
        if (hasMedia()) t.putString("Media", id);
        else t.remove("Media");
        t.put("Settings", settings.save());
    }

    public static MediaData readFrom(CompoundTag t) {
        String id = t.getString("Media");
        MediaSettings s = t.contains("Settings", 10) ? MediaSettings.load(t.getCompound("Settings")) : new MediaSettings();
        return new MediaData(id, s);
    }
}
