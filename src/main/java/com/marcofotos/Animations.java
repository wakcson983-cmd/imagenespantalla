package com.marcofotos;

import net.minecraft.util.Mth;

public final class Animations {
    private Animations() {}

    public static final class State {
        public float alpha = 1f;
        public float dx = 0f, dy = 0f; // fraccion del ancho/alto del area
        public float sx = 1f, sy = 1f;
        public float rot = 0f;
    }

    public static float ease(MediaSettings.Easing e, float p) {
        p = Mth.clamp(p, 0f, 1f);
        switch (e) {
            case SUAVE: return p * p * (3f - 2f * p);
            case ACELERA: return p * p;
            case FRENA: return 1f - (1f - p) * (1f - p);
            default: return p;
        }
    }

    private static float hash(int k) {
        int h = (k * 73856093) ^ (k * 19349663);
        return ((h >>> 4) & 1023) / 1023f;
    }

    /** p: 0 = oculto, 1 = totalmente visible. */
    public static State compute(MediaSettings.Anim a, float p) {
        p = Mth.clamp(p, 0f, 1f);
        State st = new State();
        switch (a) {
            case NINGUNA:
                break;
            case FUNDIDO:
                st.alpha = p;
                break;
            case IZQUIERDA:
                st.dx = -(1f - p);
                break;
            case DERECHA:
                st.dx = (1f - p);
                break;
            case ARRIBA:
                st.dy = -(1f - p);
                break;
            case ABAJO:
                st.dy = (1f - p);
                break;
            case ZOOM:
                st.sx = st.sy = Math.max(0.001f, p);
                st.alpha = Mth.clamp(p * 2f, 0f, 1f);
                break;
            case ZOOM_INV: {
                float s = 1f + (1f - p) * 2.5f;
                st.sx = st.sy = s;
                st.alpha = p;
                break;
            }
            case GIRO:
                st.rot = (1f - p) * 360f;
                st.sx = st.sy = Math.max(0.001f, p);
                st.alpha = Mth.clamp(p * 2f, 0f, 1f);
                break;
            case REBOTE: {
                float c1 = 1.70158f, c3 = c1 + 1f;
                float x = p - 1f;
                float s = 1f + c3 * x * x * x + c1 * x * x;
                st.sx = st.sy = Math.max(0.001f, s);
                st.alpha = Mth.clamp(p * 4f, 0f, 1f);
                break;
            }
            case ELASTICO: {
                float s;
                if (p <= 0f) s = 0f;
                else if (p >= 1f) s = 1f;
                else s = (float) (Math.pow(2, -10 * p) * Math.sin((p * 10 - 0.75) * (2 * Math.PI / 3)) + 1);
                st.sx = st.sy = Math.max(0.001f, s);
                st.alpha = Mth.clamp(p * 4f, 0f, 1f);
                break;
            }
            case PARPADEO:
                st.alpha = p >= 1f ? 1f : (((int) (p * 9f)) % 2 == 0 ? 0.1f : 1f);
                break;
            case VOLTEO:
                st.sx = Math.max(0.001f, p);
                st.alpha = Mth.clamp(p * 3f, 0f, 1f);
                break;
            case PERSIANA:
                st.sy = Math.max(0.001f, p);
                break;
            case GLITCH:
                if (p < 1f) {
                    int k = (int) (p * 40f);
                    float r1 = hash(k), r2 = hash(k + 99);
                    st.dx = (r1 - 0.5f) * 0.25f * (1f - p);
                    st.dy = (r2 - 0.5f) * 0.06f * (1f - p);
                    st.alpha = r2 < (1f - p) * 0.7f ? 0.15f : 1f;
                }
                break;
        }
        return st;
    }

    /** Devuelve null cuando la animacion de salida termino (ya no se debe dibujar). */
    public static State state(MediaSettings s, long t, long exitStart) {
        long exitMs = s.effExitMs();
        if (t >= exitStart) {
            float q = (float) (t - exitStart) / (float) exitMs;
            if (q >= 1f) return null;
            return compute(s.exit, ease(s.easing, 1f - q));
        }
        if (t < s.enterMs && s.enter != MediaSettings.Anim.NINGUNA) {
            return compute(s.enter, ease(s.easing, (float) t / (float) s.enterMs));
        }
        return new State();
    }

    public static long initialExitStart(MediaSettings s) {
        if (s.durationSec <= 0) return Long.MAX_VALUE;
        return Math.max(s.enterMs, s.durationSec * 1000L - s.effExitMs());
    }
}
