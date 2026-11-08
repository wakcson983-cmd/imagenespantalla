package com.marcofotos.client;

import com.mojang.blaze3d.platform.NativeImage;
import org.w3c.dom.Node;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.stream.ImageInputStream;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.AlphaComposite;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class MediaDecoder {
    public static final int MAX_FRAMES = 120;
    public static final int ANIM_MAX_DIM = 480;
    public static final int STATIC_MAX_DIM = 2048;

    public static class Decoded {
        public final List<NativeImage> frames = new ArrayList<>();
        public final List<Integer> delays = new ArrayList<>();
        public int w, h;
    }

    public static boolean isGif(byte[] b) {
        return b.length > 10 && b[0] == 'G' && b[1] == 'I' && b[2] == 'F' && b[3] == '8';
    }

    public static Decoded decode(byte[] bytes) throws IOException {
        if (isGif(bytes)) return decodeGif(bytes);
        BufferedImage bi = ImageIO.read(new ByteArrayInputStream(bytes));
        if (bi == null) throw new IOException("Formato de imagen no soportado");
        bi = limit(bi, STATIC_MAX_DIM);
        Decoded d = new Decoded();
        d.w = bi.getWidth();
        d.h = bi.getHeight();
        d.frames.add(toNative(bi));
        d.delays.add(1000);
        return d;
    }

    private static Decoded decodeGif(byte[] bytes) throws IOException {
        ImageReader reader = ImageIO.getImageReadersByFormatName("gif").next();
        Decoded d = new Decoded();
        try (ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            reader.setInput(in, false);
            int n = reader.getNumImages(true);
            int w = 0, h = 0;
            IIOMetadata sm = reader.getStreamMetadata();
            if (sm != null) {
                Node root = sm.getAsTree("javax_imageio_gif_stream_1.0");
                Node lsd = child(root, "LogicalScreenDescriptor");
                if (lsd != null) {
                    w = intAttr(lsd, "logicalScreenWidth", 0);
                    h = intAttr(lsd, "logicalScreenHeight", 0);
                }
            }
            if (w <= 0 || h <= 0) {
                BufferedImage f0 = reader.read(0);
                w = f0.getWidth();
                h = f0.getHeight();
            }
            BufferedImage canvas = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            BufferedImage saved = null;
            int prevDisposal = 0, pl = 0, pt = 0, pw = 0, ph = 0;
            int step = n > MAX_FRAMES ? (int) Math.ceil(n / (double) MAX_FRAMES) : 1;
            int acc = 0;

            for (int i = 0; i < n; i++) {
                BufferedImage img = reader.read(i);
                IIOMetadata md = reader.getImageMetadata(i);
                Node root = md.getAsTree("javax_imageio_gif_image_1.0");
                int delay = 10, disposal = 0, left = 0, top = 0;
                Node gce = child(root, "GraphicControlExtension");
                if (gce != null) {
                    delay = intAttr(gce, "delayTime", 10);
                    String dm = strAttr(gce, "disposalMethod");
                    if ("restoreToBackgroundColor".equals(dm)) disposal = 2;
                    else if ("restoreToPrevious".equals(dm)) disposal = 3;
                }
                Node idn = child(root, "ImageDescriptor");
                if (idn != null) {
                    left = intAttr(idn, "imageLeftPosition", 0);
                    top = intAttr(idn, "imageTopPosition", 0);
                }

                if (i > 0) {
                    if (prevDisposal == 2) {
                        Graphics2D g = canvas.createGraphics();
                        g.setComposite(AlphaComposite.Clear);
                        g.fillRect(pl, pt, pw, ph);
                        g.dispose();
                    } else if (prevDisposal == 3 && saved != null) {
                        Graphics2D g = canvas.createGraphics();
                        g.setComposite(AlphaComposite.Src);
                        g.drawImage(saved, 0, 0, null);
                        g.dispose();
                    }
                }
                if (disposal == 3) saved = copy(canvas);

                Graphics2D g = canvas.createGraphics();
                g.setComposite(AlphaComposite.SrcOver);
                g.drawImage(img, left, top, null);
                g.dispose();

                prevDisposal = disposal;
                pl = left; pt = top; pw = img.getWidth(); ph = img.getHeight();

                acc += Math.max(delay, 2) * 10;
                if (i % step == step - 1 || i == n - 1) {
                    BufferedImage snap = limit(copy(canvas), ANIM_MAX_DIM);
                    if (d.w == 0) {
                        d.w = snap.getWidth();
                        d.h = snap.getHeight();
                    }
                    d.frames.add(toNative(snap));
                    d.delays.add(acc);
                    acc = 0;
                }
            }
        } finally {
            reader.dispose();
        }
        if (d.frames.isEmpty()) throw new IOException("GIF vacio");
        return d;
    }

    private static Node child(Node parent, String name) {
        if (parent == null) return null;
        for (Node c = parent.getFirstChild(); c != null; c = c.getNextSibling()) {
            if (name.equals(c.getNodeName())) return c;
        }
        return null;
    }

    private static String strAttr(Node n, String attr) {
        Node a = n.getAttributes() == null ? null : n.getAttributes().getNamedItem(attr);
        return a == null ? "" : a.getNodeValue();
    }

    private static int intAttr(Node n, String attr, int def) {
        try {
            return Integer.parseInt(strAttr(n, attr));
        } catch (Exception e) {
            return def;
        }
    }

    public static BufferedImage copy(BufferedImage src) {
        BufferedImage c = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = c.createGraphics();
        g.setComposite(AlphaComposite.Src);
        g.drawImage(src, 0, 0, null);
        g.dispose();
        return c;
    }

    public static BufferedImage limit(BufferedImage bi, int max) {
        int w = bi.getWidth(), h = bi.getHeight();
        if (Math.max(w, h) <= max) return bi;
        double f = max / (double) Math.max(w, h);
        int nw = Math.max(1, (int) Math.round(w * f)), nh = Math.max(1, (int) Math.round(h * f));
        BufferedImage out = new BufferedImage(nw, nh, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.drawImage(bi, 0, 0, nw, nh, null);
        g.dispose();
        return out;
    }

    public static NativeImage toNative(BufferedImage bi) {
        int w = bi.getWidth(), h = bi.getHeight();
        int[] px = bi.getRGB(0, 0, w, h, null, 0, w);
        NativeImage ni = new NativeImage(w, h, false);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int c = px[y * w + x];
                int a = (c >>> 24) & 255, r = (c >> 16) & 255, g = (c >> 8) & 255, b = c & 255;
                ni.setPixelRGBA(x, y, (a << 24) | (b << 16) | (g << 8) | r);
            }
        }
        return ni;
    }
}
