package ez.factory.art;

import ez.factory.config.Config;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.awt.image.ConvolveOp;
import java.awt.image.Kernel;
import java.awt.geom.RoundRectangle2D;
import java.io.File;
import java.io.IOException;

/**
 * Mask for the masked-inpaint paint pass: white over the hero stage (with feathered edges),
 * black everywhere else. The sampler regenerates ONLY the white area at full strength, so
 * headline/cards/CTA pixels are never resampled — text stays verbatim by construction,
 * while the product hero gets full creative freedom inside its stage.
 */
public final class HeroMask {

    /** The hero-stage rectangle {x, y, w, h} — shared by the mask (ComfyUI) and the crop (Gemini). */
    public static int[] rect(Config cfg) {
        return new int[] { 110, 760, cfg.canvasW - 220, 420 };
    }

    public static File make(Config cfg, File out) throws IOException {
        int W = cfg.canvasW, H = cfg.canvasH;
        int[] r = rect(cfg);
        BufferedImage m = new BufferedImage(W, H, BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D g = m.createGraphics();
        g.setColor(Color.BLACK);
        g.fillRect(0, 0, W, H);
        g.setColor(Color.WHITE);
        g.fill(new RoundRectangle2D.Float(r[0], r[1], r[2], r[3], 80, 80));
        g.dispose();
        m = blur(m, 25, 10f);
        ImageIO.write(m, "png", out);
        return out;
    }

    private static BufferedImage blur(BufferedImage src, int size, float sigma) {
        float[] k = new float[size * size];
        float sum = 0;
        int c = size / 2;
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                float d2 = (x - c) * (x - c) + (y - c) * (y - c);
                k[y * size + x] = (float) Math.exp(-d2 / (2 * sigma * sigma));
                sum += k[y * size + x];
            }
        }
        for (int i = 0; i < k.length; i++) k[i] /= sum;
        ConvolveOp op = new ConvolveOp(new Kernel(size, size, k), ConvolveOp.EDGE_NO_OP, null);
        return op.filter(src, null);
    }
}
