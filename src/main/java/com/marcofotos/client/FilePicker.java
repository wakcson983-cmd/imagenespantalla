package com.marcofotos.client;

import java.lang.reflect.Method;

/** Abre el explorador de archivos nativo usando tinyfd de LWJGL (via reflexion para no depender de el al compilar). */
public class FilePicker {
    /** Devuelve la ruta elegida, o null si se cancelo. Lanza RuntimeException si no se pudo abrir. */
    public static String pick() {
        try {
            Class<?> c = Class.forName("org.lwjgl.util.tinyfd.TinyFileDialogs");
            Class<?> pb = Class.forName("org.lwjgl.PointerBuffer");
            Method m = c.getMethod("tinyfd_openFileDialog", CharSequence.class, CharSequence.class, pb, CharSequence.class, boolean.class);
            Object r = m.invoke(null, "Elige una imagen, gif o video", "", null, null, false);
            return r == null ? null : r.toString();
        } catch (Throwable t) {
            throw new RuntimeException("No se pudo abrir el explorador de archivos", t);
        }
    }
}
