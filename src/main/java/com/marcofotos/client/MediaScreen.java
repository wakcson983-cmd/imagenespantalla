package com.marcofotos.client;

import com.marcofotos.Animations;
import com.marcofotos.MediaSettings;
import com.marcofotos.MediaData;
import com.marcofotos.Net;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;

import java.nio.file.Path;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleFunction;

public class MediaScreen extends Screen {
    public enum Mode { IMAGEN, MARCO_ITEM, MARCO_BLOCK }

    private static final int W = 316;
    private static final int GRAY = 0xAAAAAA, RED = 0xFF5555, GREEN = 0x55FF55;

    private final Mode mode;
    private final InteractionHand hand;
    private final BlockPos blockPos;
    private String mediaId;
    private final MediaSettings s;
    private Importer.Result pending;
    private String status = "";
    private int statusColor = GRAY;
    private boolean busy;
    private String pathText = "";
    private EditBox pathBox;
    private int tab;
    private final long openTime = Util.getMillis();
    private int pvMode; // 0 reposo, 1 entrada, 2 salida, 3 completa
    private long pvStart;
    private int x0, y0;

    public MediaScreen(Mode mode, InteractionHand hand, BlockPos pos, MediaData data) {
        super(Component.literal(mode == Mode.IMAGEN ? "Imagen: mostrar en pantalla de todos" : "Marco de fotos"));
        this.mode = mode;
        this.hand = hand;
        this.blockPos = pos;
        this.mediaId = data.id == null ? "" : data.id;
        this.s = data.settings;
        if (hasMedia()) {
            ClientMediaCache.retryIfFailed(mediaId);
            ClientMediaCache.get(mediaId);
            status = "Imagen cargada. Importa otra o ajusta la configuracion.";
        } else {
            status = "Importa una imagen, gif o video (ruta, enlace o arrastrando el archivo).";
        }
    }

    private boolean hasMedia() {
        return mediaId != null && !mediaId.isEmpty();
    }

    private void setStatus(String t, int color) {
        status = t;
        statusColor = color;
    }

    // ------------------------------------------------------------------ UI

    @Override
    protected void init() {
        x0 = (width - W) / 2;
        y0 = Math.max(2, (height - 226) / 2);

        pathBox = new EditBox(font, x0, y0 + 12, 168, 20, Component.literal("ruta"));
        pathBox.setMaxLength(600);
        pathBox.setHint(Component.literal("Ruta o enlace (http...)"));
        pathBox.setValue(pathText);
        pathBox.setResponder(t -> pathText = t);
        addRenderableWidget(pathBox);

        addRenderableWidget(Button.builder(Component.literal("Examinar..."), b -> browse())
                .bounds(x0 + 172, y0 + 12, 68, 20)
                .tooltip(Tooltip.create(Component.literal("Abrir el explorador de archivos")))
                .build());
        addRenderableWidget(Button.builder(Component.literal("Importar"), b -> startImport(pathText))
                .bounds(x0 + 244, y0 + 12, 72, 20)
                .tooltip(Tooltip.create(Component.literal("Importar la ruta o enlace escrito")))
                .build());

        // pestanas
        List<String> tabs = mode == Mode.IMAGEN ? List.of("Animacion", "Reproduc.", "Aspecto") : List.of("Reproduc.", "Aspecto");
        int tw = mode == Mode.IMAGEN ? 54 : 82;
        int gap = mode == Mode.IMAGEN ? 3 : 4;
        for (int i = 0; i < tabs.size(); i++) {
            final int idx = i;
            Component label = Component.literal(tabs.get(i)).withStyle(i == tab ? ChatFormatting.YELLOW : ChatFormatting.WHITE);
            addRenderableWidget(Button.builder(label, b -> {
                tab = idx;
                rebuildWidgets();
            }).bounds(x0 + i * (tw + gap), y0 + 50, tw, 20).build());
        }

        String key;
        if (mode == Mode.IMAGEN) key = new String[]{"anim", "play", "look"}[tab];
        else key = new String[]{"play", "look"}[tab];

        int r = 0;
        switch (key) {
            case "anim":
                cycleAnim(r++, true);
                cycleAnim(r++, false);
                cycleEasing(r++);
                slider(r++, v -> "Duracion de entrada: " + (int) v + " ms", 100, 3000, 50, s.enterMs, v -> s.enterMs = (int) v, () -> playPreview(1));
                slider(r++, v -> "Duracion de salida: " + (int) v + " ms", 100, 3000, 50, s.exitMs, v -> s.exitMs = (int) v, () -> playPreview(2));
                break;
            case "play":
                if (mode == Mode.IMAGEN) {
                    slider(r++, v -> "Duracion: " + (v <= 0 ? "infinita" : (int) v + " s"), 0, 120, 1, s.durationSec, v -> s.durationSec = (int) v, null);
                }
                slider(r++, v -> String.format(java.util.Locale.ROOT, "Velocidad: %.2fx", v), 0.25, 4.0, 0.05, s.speed, v -> s.speed = (float) v, null);
                toggle(r++, "Reversa", () -> s.reverse, v -> s.reverse = v);
                toggle(r++, "Repetir (loop)", () -> s.loop, v -> s.loop = v);
                toggle(r++, "Ida y vuelta", () -> s.pingpong, v -> s.pingpong = v);
                break;
            default: // look
                if (mode == Mode.IMAGEN) {
                    slider(r++, v -> "Opacidad: " + (int) v + "%", 5, 100, 5, s.opacity, v -> s.opacity = (int) v, null);
                    slider(r++, v -> "Tamano: " + (int) v + "%", 10, 100, 5, s.size, v -> s.size = (int) v, null);
                    slider(r++, v -> "Rotacion: " + (int) v + " grados", 0, 355, 5, s.rotation, v -> s.rotation = (int) v, null);
                    cyclePos(r++);
                    toggle(r++, "Espejo", () -> s.mirror, v -> s.mirror = v);
                } else {
                    toggle(r++, "Espejo", () -> s.mirror, v -> s.mirror = v);
                    toggle(r++, "Rellenar (estirar)", () -> s.fill, v -> s.fill = v);
                }
                break;
        }

        // vista previa
        if (mode == Mode.IMAGEN) {
            addRenderableWidget(Button.builder(Component.literal("> Entrada"), b -> playPreview(1))
                    .bounds(x0 + 176, y0 + 134, 68, 20).build());
            addRenderableWidget(Button.builder(Component.literal("> Salida"), b -> playPreview(2))
                    .bounds(x0 + 248, y0 + 134, 68, 20).build());
            addRenderableWidget(Button.builder(Component.literal("> Vista previa completa"), b -> playPreview(3))
                    .bounds(x0 + 176, y0 + 156, 140, 20).build());
        }

        // acciones
        if (mode == Mode.IMAGEN) {
            addRenderableWidget(Button.builder(Component.literal("Mostrar"), b -> actionShow())
                    .bounds(x0, y0 + 190, 77, 20)
                    .tooltip(Tooltip.create(Component.literal("Mostrar en la pantalla de todos los jugadores")))
                    .build());
            addRenderableWidget(Button.builder(Component.literal("Ocultar"), b -> actionHide())
                    .bounds(x0 + 79, y0 + 190, 77, 20)
                    .tooltip(Tooltip.create(Component.literal("Quitar de la pantalla de todos (con animacion de salida)")))
                    .build());
            addRenderableWidget(Button.builder(Component.literal("Guardar"), b -> actionSave(false))
                    .bounds(x0 + 158, y0 + 190, 77, 20)
                    .tooltip(Tooltip.create(Component.literal("Guardar imagen y ajustes dentro del item")))
                    .build());
            addRenderableWidget(Button.builder(Component.literal("Cerrar"), b -> onClose())
                    .bounds(x0 + 237, y0 + 190, 79, 20).build());
        } else {
            addRenderableWidget(Button.builder(Component.literal("Aplicar"), b -> actionSave(true))
                    .bounds(x0, y0 + 190, 156, 20).build());
            addRenderableWidget(Button.builder(Component.literal("Cerrar"), b -> onClose())
                    .bounds(x0 + 160, y0 + 190, 156, 20).build());
        }
    }

    private int rowY(int r) {
        return y0 + 74 + r * 22;
    }

    private void cycleAnim(int row, boolean isEnter) {
        addRenderableWidget(Button.builder(animLabel(isEnter), b -> {
            MediaSettings.Anim[] v = MediaSettings.Anim.values();
            MediaSettings.Anim cur = isEnter ? s.enter : s.exit;
            MediaSettings.Anim nxt = v[(cur.ordinal() + 1) % v.length];
            if (isEnter) s.enter = nxt;
            else s.exit = nxt;
            b.setMessage(animLabel(isEnter));
            playPreview(isEnter ? 1 : 2);
        }).bounds(x0, rowY(row), 168, 20)
                .tooltip(Tooltip.create(Component.literal("Click para cambiar; se muestra la vista previa")))
                .build());
    }

    private Component animLabel(boolean isEnter) {
        return Component.literal((isEnter ? "Entrada: " : "Salida: ") + (isEnter ? s.enter.label : s.exit.label));
    }

    private void cycleEasing(int row) {
        addRenderableWidget(Button.builder(Component.literal("Suavizado: " + s.easing.label), b -> {
            MediaSettings.Easing[] v = MediaSettings.Easing.values();
            s.easing = v[(s.easing.ordinal() + 1) % v.length];
            b.setMessage(Component.literal("Suavizado: " + s.easing.label));
            playPreview(3);
        }).bounds(x0, rowY(row), 168, 20).build());
    }

    private void cyclePos(int row) {
        addRenderableWidget(Button.builder(Component.literal("Posicion: " + s.pos.label), b -> {
            MediaSettings.Pos[] v = MediaSettings.Pos.values();
            s.pos = v[(s.pos.ordinal() + 1) % v.length];
            b.setMessage(Component.literal("Posicion: " + s.pos.label));
        }).bounds(x0, rowY(row), 168, 20).build());
    }

    private void toggle(int row, String name, BooleanSupplier get, Consumer<Boolean> set) {
        addRenderableWidget(Button.builder(tgl(name, get.getAsBoolean()), b -> {
            boolean nv = !get.getAsBoolean();
            set.accept(nv);
            b.setMessage(tgl(name, nv));
        }).bounds(x0, rowY(row), 168, 20).build());
    }

    private static Component tgl(String name, boolean v) {
        return Component.literal(name + ": " + (v ? "Si" : "No"));
    }

    private void slider(int row, DoubleFunction<String> fmt, double min, double max, double step, double cur,
                        DoubleConsumer set, Runnable onRelease) {
        addRenderableWidget(new SettingSlider(x0, rowY(row), 168, 20, fmt, min, max, step, cur, set, onRelease));
    }

    // ------------------------------------------------------------------ importar

    private void startImport(String src) {
        if (busy) return;
        busy = true;
        setStatus("Importando...", GRAY);
        Importer.run(src, r -> {
            busy = false;
            if (r.error != null) {
                setStatus(r.error, RED);
            } else {
                pending = r;
                mediaId = r.id;
                ClientMediaCache.putLocal(r.id, r.bytes);
                setStatus("Listo: " + r.info, GREEN);
                playPreview(3);
            }
        });
    }

    private void browse() {
        if (busy) return;
        busy = true;
        setStatus("Abriendo el explorador de archivos...", GRAY);
        Thread t = new Thread(() -> {
            String path = null;
            boolean failed = false;
            try {
                path = FilePicker.pick();
            } catch (Throwable ex) {
                failed = true;
            }
            final String fp = path;
            final boolean ff = failed;
            Minecraft.getInstance().execute(() -> {
                busy = false;
                if (ff) {
                    setStatus("No se pudo abrir el explorador. Arrastra el archivo aqui o escribe la ruta.", RED);
                } else if (fp == null || fp.isEmpty()) {
                    setStatus("No se eligio ningun archivo.", GRAY);
                } else {
                    pathText = fp;
                    if (pathBox != null) pathBox.setValue(fp);
                    startImport(fp);
                }
            });
        }, "MarcoFotos-FilePicker");
        t.setDaemon(true);
        t.start();
    }

    @Override
    public void onFilesDrop(List<Path> paths) {
        if (paths == null || paths.isEmpty()) return;
        String p = paths.get(0).toString();
        pathText = p;
        if (pathBox != null) pathBox.setValue(p);
        startImport(p);
    }

    // ------------------------------------------------------------------ acciones

    /** Sube el medio al servidor si hace falta y luego ejecuta 'after'. */
    private void whenReady(String id, Runnable after) {
        if (id == null || id.isEmpty()) {
            setStatus("Primero importa una imagen, gif o video.", RED);
            return;
        }
        if (pending != null && pending.id.equals(id) && !UploadManager.isOnServer(id)) {
            setStatus("Subiendo al servidor...", GRAY);
            UploadManager.upload(id, pending.bytes, after);
        } else {
            after.run();
        }
    }

    private int handOrd() {
        return hand == InteractionHand.OFF_HAND ? 1 : 0;
    }

    private void actionShow() {
        final String id = mediaId;
        final MediaSettings sc = s.copy();
        final int ho = handOrd();
        whenReady(id, () -> {
            Net.CHANNEL.sendToServer(new Net.ShowMedia(id, sc));
            Net.CHANNEL.sendToServer(new Net.SetItemMedia(ho, id, sc));
            if (Minecraft.getInstance().screen == this) setStatus("Mostrando a todos los jugadores.", GREEN);
        });
    }

    private void actionHide() {
        Net.CHANNEL.sendToServer(new Net.HideAll());
        setStatus("Ocultando para todos.", GRAY);
    }

    private void actionSave(boolean closeAfter) {
        final String id = mediaId;
        final MediaSettings sc = s.copy();
        final int ho = handOrd();
        final Mode m = mode;
        final BlockPos bp = blockPos;
        whenReady(id, () -> {
            if (m == Mode.MARCO_BLOCK) {
                Net.CHANNEL.sendToServer(new Net.SetBlockMedia(bp, id, sc));
            } else {
                Net.CHANNEL.sendToServer(new Net.SetItemMedia(ho, id, sc));
            }
            Minecraft mc = Minecraft.getInstance();
            if (mc.screen == this) {
                if (closeAfter) onClose();
                else setStatus("Guardado en el item.", GREEN);
            }
        });
    }

    // ------------------------------------------------------------------ vista previa

    private void playPreview(int m) {
        pvMode = m;
        pvStart = Util.getMillis();
    }

    private Animations.State previewState(long now) {
        if (mode != Mode.IMAGEN || pvMode == 0) return new Animations.State();
        long t = now - pvStart;
        long ex = s.effExitMs();
        switch (pvMode) {
            case 1:
                if (t >= s.enterMs + 300) {
                    pvMode = 0;
                    return new Animations.State();
                }
                return Animations.state(s, t, Long.MAX_VALUE);
            case 2:
                if (t >= ex + 500) {
                    pvMode = 0;
                    return new Animations.State();
                }
                return Animations.state(s, t + s.enterMs, s.enterMs);
            default: {
                long exitStart = s.enterMs + 1000L;
                if (t >= exitStart + ex + 500) {
                    pvMode = 0;
                    return new Animations.State();
                }
                return Animations.state(s, t, exitStart);
            }
        }
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        g.drawCenteredString(font, title, width / 2, y0, 0xFFFFFF);

        // estado
        g.drawString(font, font.plainSubstrByWidth(status, W), x0, y0 + 36, statusColor);

        // caja de vista previa
        int bx = x0 + 176, by = y0 + 50, bw = 140, bh = 80;
        g.fill(bx - 1, by - 1, bx + bw + 1, by + bh + 1, 0xFF666666);
        g.fill(bx, by, bx + bw, by + bh, 0xFF101010);

        ClientMediaCache.Entry e = hasMedia() ? ClientMediaCache.get(mediaId) : null;
        if (e == null) {
            g.drawCenteredString(font, "Sin imagen", bx + bw / 2, by + bh / 2 - 4, 0x888888);
        } else if (e.state == ClientMediaCache.State.LOADING) {
            g.drawCenteredString(font, "Cargando...", bx + bw / 2, by + bh / 2 - 4, 0x888888);
        } else if (e.state == ClientMediaCache.State.FAILED) {
            g.drawCenteredString(font, "No se pudo cargar", bx + bw / 2, by + bh / 2 - 4, 0xFF5555);
        } else {
            long now = Util.getMillis();
            long media = now - openTime;
            g.enableScissor(bx, by, bx + bw, by + bh);
            if (mode == Mode.IMAGEN) {
                Animations.State st = previewState(now);
                if (st != null) DisplayRenderer.draw(g, bx, by, bw, bh, e, s, st, media);
            } else {
                MediaSettings pv = s.copy();
                pv.size = 100;
                pv.rotation = 0;
                pv.opacity = 100;
                pv.pos = MediaSettings.Pos.CENTRO;
                DisplayRenderer.draw(g, bx + 30, by, 80, 80, e, pv, new Animations.State(), media);
            }
            g.disableScissor();
        }

        super.render(g, mx, my, pt);

        // pie
        float pr = UploadManager.progress();
        if (pr >= 0f) {
            g.drawString(font, "Subiendo al servidor: " + (int) (pr * 100) + "%", x0, y0 + 214, 0xFFFF55);
        } else {
            String hint = mode == Mode.IMAGEN
                    ? "Arrastra un archivo aqui | PNG JPG BMP GIF | video: requiere ffmpeg"
                    : "Arrastra un archivo aqui | PNG JPG BMP GIF | video: requiere ffmpeg";
            g.drawString(font, font.plainSubstrByWidth(hint, W), x0, y0 + 214, 0x777777);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
