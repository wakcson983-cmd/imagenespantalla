package com.marcofotos.client;

import com.marcofotos.Config;
import com.marcofotos.MarcoFotos;
import com.marcofotos.MediaData;
import com.marcofotos.ServerMedia;
import net.minecraft.client.Minecraft;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public class Importer {
    public static class Result {
        public byte[] bytes;
        public String id;
        public String info;
        public String error;
    }

    public interface Callback {
        void done(Result r);
    }

    private static final Set<String> VIDEO = Set.of("mp4", "webm", "mkv", "mov", "avi", "m4v", "flv", "wmv", "mpg", "mpeg");
    private static final long MAX_RAW = 64L * 1024 * 1024;

    public static void run(String src, Callback cb) {
        Thread t = new Thread(() -> {
            Result r = new Result();
            try {
                importSync(src == null ? "" : src.trim(), r);
            } catch (Throwable ex) {
                r.bytes = null;
                r.id = null;
                r.error = ex.getMessage() != null ? ex.getMessage() : ex.toString();
                MarcoFotos.LOGGER.warn("Importacion fallida", ex);
            }
            Minecraft.getInstance().execute(() -> cb.done(r));
        }, "MarcoFotos-Importer");
        t.setDaemon(true);
        t.start();
    }

    private static void importSync(String src, Result r) throws Exception {
        if (src.length() >= 2 && src.startsWith("\"") && src.endsWith("\"")) src = src.substring(1, src.length() - 1);
        if (src.isEmpty()) throw new IOException("Escribe una ruta o un enlace, o arrastra un archivo a esta ventana.");

        Path file;
        boolean temp = false;
        String name;
        if (src.startsWith("http://") || src.startsWith("https://")) {
            name = urlName(src);
            file = download(src, extOf(name));
            temp = true;
        } else {
            file = Paths.get(src);
            name = file.getFileName() == null ? "" : file.getFileName().toString();
            if (!Files.isRegularFile(file)) throw new IOException("No se encontro el archivo.");
        }
        Path gifTmp = null;
        try {
            String ext = extOf(name);
            byte[] data;
            if (VIDEO.contains(ext)) {
                gifTmp = videoToGif(file);
                data = Files.readAllBytes(gifTmp);
            } else {
                if (Files.size(file) > MAX_RAW) throw new IOException("El archivo es demasiado grande.");
                data = Files.readAllBytes(file);
            }
            data = normalize(data, r);
            r.bytes = data;
            r.id = ServerMedia.sha1(data);
        } finally {
            if (temp) Files.deleteIfExists(file);
            if (gifTmp != null) Files.deleteIfExists(gifTmp);
        }
    }

    private static String urlName(String url) {
        try {
            String p = URI.create(url).getPath();
            if (p == null) return "";
            int i = p.lastIndexOf('/');
            return i >= 0 ? p.substring(i + 1) : p;
        } catch (Exception e) {
            return "";
        }
    }

    private static String extOf(String name) {
        int i = name.lastIndexOf('.');
        return i < 0 ? "" : name.substring(i + 1).toLowerCase(Locale.ROOT);
    }

    private static Path download(String url, String ext) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setRequestProperty("User-Agent", "Mozilla/5.0 MarcoFotos");
        c.setConnectTimeout(10000);
        c.setReadTimeout(20000);
        c.setInstanceFollowRedirects(true);
        int code = c.getResponseCode();
        if (code / 100 != 2) throw new IOException("El enlace respondio con codigo " + code + ".");
        Path tmp = Files.createTempFile("marcofotos", "." + (ext.isEmpty() ? "bin" : ext));
        try (InputStream in = c.getInputStream(); OutputStream out = Files.newOutputStream(tmp)) {
            byte[] buf = new byte[16384];
            long total = 0;
            int n;
            while ((n = in.read(buf)) > 0) {
                total += n;
                if (total > MAX_RAW) throw new IOException("El archivo del enlace es demasiado grande.");
                out.write(buf, 0, n);
            }
        } catch (IOException ex) {
            Files.deleteIfExists(tmp);
            throw ex;
        }
        return tmp;
    }

    private static Path videoToGif(Path in) throws Exception {
        int[][] presets = {{360, 12}, {240, 10}, {160, 8}};
        Path out = Files.createTempFile("marcofotos", ".gif");
        try {
            for (int[] p : presets) {
                runFfmpeg(in, out, p[0], p[1]);
                if (Files.size(out) <= MediaData.MAX_BYTES) return out;
            }
        } catch (Exception ex) {
            Files.deleteIfExists(out);
            throw ex;
        }
        Files.deleteIfExists(out);
        throw new IOException("El video es demasiado pesado aun reducido. Usa uno mas corto.");
    }

    private static void runFfmpeg(Path in, Path out, int width, int fps) throws Exception {
        String vf = "fps=" + fps + ",scale='min(" + width + ",iw)':-2:flags=lanczos,split[a][b];"
                + "[a]palettegen=max_colors=128[p];[b][p]paletteuse=dither=bayer:bayer_scale=3";
        ProcessBuilder pb = new ProcessBuilder(Config.FFMPEG.get(), "-y", "-v", "error", "-t", "12",
                "-i", in.toString(), "-vf", vf, "-loop", "0", out.toString());
        pb.redirectErrorStream(true);
        pb.redirectOutput(ProcessBuilder.Redirect.DISCARD);
        Process proc;
        try {
            proc = pb.start();
        } catch (IOException e) {
            throw new IOException("No se encontro ffmpeg. Instalalo (ffmpeg.org) y agregalo al PATH, o pon su ruta en la config del mod.");
        }
        if (!proc.waitFor(180, TimeUnit.SECONDS)) {
            proc.destroyForcibly();
            throw new IOException("ffmpeg tardo demasiado.");
        }
        if (proc.exitValue() != 0) throw new IOException("ffmpeg no pudo convertir el video.");
    }

    private static byte[] normalize(byte[] data, Result r) throws IOException {
        if (MediaDecoder.isGif(data)) {
            if (data.length > MediaData.MAX_BYTES) {
                throw new IOException("El GIF pesa " + fmt(data.length) + " (maximo 8 MB).");
            }
            int w = (data[6] & 255) | ((data[7] & 255) << 8);
            int h = (data[8] & 255) | ((data[9] & 255) << 8);
            r.info = "GIF " + w + "x" + h + ", " + fmt(data.length);
            return data;
        }
        BufferedImage bi;
        try {
            bi = ImageIO.read(new ByteArrayInputStream(data));
        } catch (Throwable t) {
            bi = null;
        }
        if (bi == null) {
            throw new IOException("Formato no compatible. Usa PNG, JPG, BMP, GIF o un video (requiere ffmpeg).");
        }
        bi = MediaDecoder.limit(bi, MediaDecoder.STATIC_MAX_DIM);
        for (int attempt = 0; attempt < 7; attempt++) {
            byte[] png = write(bi, "png");
            if (png.length <= MediaData.MAX_BYTES) {
                r.info = "Imagen " + bi.getWidth() + "x" + bi.getHeight() + ", " + fmt(png.length);
                return png;
            }
            byte[] jpg = write(toRgb(bi), "jpg");
            if (jpg.length <= MediaData.MAX_BYTES) {
                r.info = "Imagen " + bi.getWidth() + "x" + bi.getHeight() + ", " + fmt(jpg.length);
                return jpg;
            }
            bi = MediaDecoder.limit(bi, (int) (Math.max(bi.getWidth(), bi.getHeight()) * 0.7));
        }
        throw new IOException("La imagen es demasiado pesada.");
    }

    private static BufferedImage toRgb(BufferedImage src) {
        BufferedImage rgb = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = rgb.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, rgb.getWidth(), rgb.getHeight());
        g.drawImage(src, 0, 0, null);
        g.dispose();
        return rgb;
    }

    private static byte[] write(BufferedImage bi, String fmt) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        if (!ImageIO.write(bi, fmt, out)) throw new IOException("No se pudo codificar la imagen.");
        return out.toByteArray();
    }

    private static String fmt(long bytes) {
        if (bytes < 1024 * 1024) return (bytes / 1024) + " KB";
        return String.format(Locale.ROOT, "%.1f MB", bytes / 1048576.0);
    }
}
