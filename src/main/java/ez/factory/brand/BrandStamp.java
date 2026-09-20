package ez.factory.brand;

import ez.factory.config.Config;
import ez.factory.util.Log;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

/**
 * BRAND STAMP (brand spec §1): the OFFICIAL logo is never redrawn by AI — it is composited
 * here, deterministically, on top of the finished artwork:
 *   - recoloured to the dominant colour of the artwork (blue art -> blue logo...)
 *   - the small power/house glyph (the near-white pixels of the asset) stays white
 *   - a dark halo keeps it legible on busy frames
 *   - fixed top-left position, inside the reserved logo zone.
 */
public final class BrandStamp {

    private static final String TAG = "brand";

    public static File stamp(Config cfg, File artwork) throws IOException {
        BufferedImage art = ImageIO.read(artwork);
        if (art == null) throw new IOException("Cannot read artwork " + artwork);
        if (art.getWidth() != cfg.canvasW || art.getHeight() != cfg.canvasH) {
            BufferedImage scaled = new BufferedImage(cfg.canvasW, cfg.canvasH, BufferedImage.TYPE_INT_RGB);
            Graphics2D g2 = scaled.createGraphics();
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g2.drawImage(art, 0, 0, cfg.canvasW, cfg.canvasH, null);
            g2.dispose();
            art = scaled;
        }

        File logoFile = cfg.logoPath.toFile();
        if (!logoFile.exists()) {
            Log.warn(TAG, "logo asset missing (" + logoFile + ") — posting WITHOUT brand stamp!");
            ImageIO.write(art, "png", artwork);
            return artwork;
        }
        BufferedImage logo = ImageIO.read(logoFile);
        Color dominant = dominantColor(art);

        int w = Math.min(cfg.logoMaxWidth, logo.getWidth());
        int h = (int) ((double) logo.getHeight() / logo.getWidth() * w);
        BufferedImage tinted = recolor(logo, w, h, dominant);

        Graphics2D g = art.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        int x = cfg.logoMargin, y = cfg.logoMargin;
        // dark halo
        g.setColor(new Color(0, 0, 0, 150));
        g.fill(new RoundRectangle2D.Float(x - 14, y - 14, w + 28, h + 28, 26, 26));
        g.drawImage(tinted, x, y, w, h, null);
        g.dispose();

        ImageIO.write(art, "png", artwork);
        Log.info(TAG, "logo stamped (dominant #" + Integer.toHexString(dominant.getRGB() & 0xFFFFFF) + ")");
        return artwork;
    }

    /** Dominant hue of the artwork via saturation-weighted hue histogram. */
    static Color dominantColor(BufferedImage img) {
        int bins = 12;
        double[] hueW = new double[bins];
        float[] hsb = new float[3];
        int step = Math.max(1, img.getWidth() / 120);
        for (int y = 0; y < img.getHeight(); y += step) {
            for (int x = 0; x < img.getWidth(); x += step) {
                int rgb = img.getRGB(x, y);
                Color c = new Color(rgb, false);
                Color.RGBtoHSB(c.getRed(), c.getGreen(), c.getBlue(), hsb);
                double weight = hsb[1] * hsb[2];
                if (weight < 0.06) continue; // ignore near-grey/black
                int b = (int) (hsb[0] * bins) % bins;
                hueW[b] += weight;
            }
        }
        int best = 0;
        for (int i = 1; i < bins; i++) if (hueW[i] > hueW[best]) best = i;
        if (hueW[best] <= 0) return new Color(0x29, 0x79, 0xFF);
        float hue = (best + 0.5f) / bins;
        return Color.getHSBColor(hue, 0.85f, 0.95f);
    }

    /** Recolour: keep near-white glyph pixels white, map everything else onto the dominant colour. */
    static BufferedImage recolor(BufferedImage logo, int w, int h, Color dom) {
        BufferedImage src = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = src.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(logo, 0, 0, w, h, null);
        g.dispose();
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int argb = src.getRGB(x, y);
                int a = (argb >>> 24) & 0xFF;
                if (a < 10) continue;
                int r = (argb >> 16) & 0xFF, gg = (argb >> 8) & 0xFF, b = argb & 0xFF;
                if (r > 225 && gg > 225 && b > 225) continue; // power/house glyph stays white
                double lum = (0.299 * r + 0.587 * gg + 0.114 * b) / 255.0;
                double shade = 0.55 + 0.45 * lum;
                int nr = clamp(dom.getRed() * shade), ng = clamp(dom.getGreen() * shade), nb = clamp(dom.getBlue() * shade);
                src.setRGB(x, y, (a << 24) | (nr << 16) | (ng << 8) | nb);
            }
        }
        return src;
    }

    private static int clamp(double v) { return Math.max(0, Math.min(255, (int) v)); }
}
