package ez.factory.art;

import ez.factory.config.Config;
import ez.factory.plan.PostPlan;
import ez.factory.util.Log;

import javax.imageio.ImageIO;
import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Deterministic offline poster renderer (brand spec §4 fallback path) AND the base layout
 * fed to Qwen-Image-Edit in the primary path.
 *
 * Layout v2 — "poster with a stage": headline block on top, then an empty HERO STAGE
 * (y 780-1140) where either a real cutout product photo (topic card "asset") is composited
 * or Qwen-Image-Edit paints the cinematic product visual; compact fact cards below; CTA bar
 * above the calm bottom strip. Guarantees: 1080x1920, empty top-left logo zone, calm
 * TikTok/Shorts safe zones (x>940 rail, y>1660 strip), on-image text only.
 */
public final class FallbackRenderer implements ArtGenerator {

    private static final String TAG = "renderer";
    private final Config cfg;

    public FallbackRenderer(Config cfg) { this.cfg = cfg; }

    private static final Color[] ACCENTS = {
            new Color(0x00, 0xE6, 0x76), new Color(0x29, 0x79, 0xFF), new Color(0xFF, 0x17, 0x44),
            new Color(0x00, 0xE5, 0xFF), new Color(0xFF, 0x91, 0x00), new Color(0xD5, 0x00, 0xF9)
    };

    private static final int LEFT = 64, RIGHT = 940;
    private static final int HERO_TOP = 780, HERO_BOT = 1140;
    private static final int BODY_TOP = 1160, BODY_BOT = 1470;
    private static final int CTA_Y = 1500;

    @Override
    public File generate(PostPlan plan, File outFile, File maskIgnored) throws IOException {
        int W = cfg.canvasW, H = cfg.canvasH;
        BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

        Color accent = ACCENTS[Math.abs(plan.id == null ? 0 : plan.id.hashCode()) % ACCENTS.length];

        g.setPaint(new GradientPaint(0, 0, new Color(0x04, 0x06, 0x0B), 0, H, new Color(0x0A, 0x10, 0x1E)));
        g.fillRect(0, 0, W, H);

        int y = 300;
        List<String> chips = new ArrayList<>();
        if (!plan.seriesTag.isEmpty()) chips.add(plan.seriesTag);
        if (plan.conceptVisual) chips.add("CONCEPT VISUAL");
        if (!chips.isEmpty()) {
            int cx = LEFT;
            for (String c : chips) {
                g.setFont(font(Font.BOLD, 30));
                int w = g.getFontMetrics().stringWidth(c) + 44;
                g.setColor(new Color(0x00, 0x00, 0x00, 150));
                g.fill(new RoundRectangle2D.Float(cx, y - 40, w, 56, 28, 28));
                g.setColor(accent);
                g.setStroke(new BasicStroke(2.5f));
                g.draw(new RoundRectangle2D.Float(cx, y - 40, w, 56, 28, 28));
                g.drawString(c, cx + 22, y - 1);
                cx += w + 18;
            }
            y += 60;
        }

        y = drawHeadline(g, plan.headline.toUpperCase(), y);
        y = drawBrush(g, plan.accent.toUpperCase(), LEFT, y + 40, accent);

        // ---- hero stage ----
        drawStage(g, accent);
        drawAsset(g, plan, accent);

        // ---- body ----
        switch (plan.archetype == null ? ez.factory.plan.Archetype.WEIRD_GADGET : plan.archetype) {
            case COMPARISON:
                drawTable(g, plan, LEFT, RIGHT, HERO_TOP, accent);
                break;
            case CREATOR_VERIFIED:
                drawQuote(g, plan, LEFT, RIGHT, HERO_TOP, accent);
                drawCallouts(g, plan, LEFT, RIGHT, 1020, accent, 4, 148);
                break;
            default:
                drawCallouts(g, plan, LEFT, RIGHT, BODY_TOP, accent, 4, 148);
        }

        drawCta(g, plan.cta.toUpperCase(), LEFT, RIGHT, CTA_Y, accent);

        g.dispose();
        ImageIO.write(img, "png", outFile);
        Log.info(TAG, "base layout rendered: " + outFile.getName());
        return outFile;
    }

    // ---------- hero stage ----------

    private void drawStage(Graphics2D g, Color accent) {
        radialGlow(g, 540, 950, 360, accent, 0.06f);
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.12f));
        g.setColor(accent);
        g.fill(new Ellipse2D.Double(540 - 330, 1105 - 42, 660, 84));
        g.setComposite(AlphaComposite.SrcOver);
    }

    /** Optional real product cutout (topic card "asset": transparent PNG) composited on the stage. */
    private void drawAsset(Graphics2D g, PostPlan plan, Color accent) {
        if (plan.assetPath == null || plan.assetPath.isEmpty()) return;
        File f = new File(plan.assetPath);
        if (!f.exists()) { Log.warn(TAG, "asset missing: " + plan.assetPath); return; }
        try {
            BufferedImage a = ImageIO.read(f);
            if (a == null) return;
            int boxW = RIGHT - LEFT - 160, boxH = HERO_BOT - HERO_TOP - 30;
            double s = Math.min((double) boxW / a.getWidth(), (double) boxH / a.getHeight());
            int w = (int) (a.getWidth() * s), h = (int) (a.getHeight() * s);
            int x = 540 - w / 2, yy = HERO_TOP + (HERO_BOT - HERO_TOP - h) / 2;
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.35f));
            g.setColor(Color.BLACK);
            g.fill(new Ellipse2D.Double(x + w * 0.1, yy + h - 18, w * 0.8, 36));
            g.setComposite(AlphaComposite.SrcOver);
            g.drawImage(a, x, yy, w, h, null);
            Log.info(TAG, "product asset composited: " + f.getName());
        } catch (IOException e) {
            Log.warn(TAG, "asset unreadable: " + e.getMessage());
        }
    }

    // ---------- pieces ----------

    private int drawHeadline(Graphics2D g, String text, int y) {
        int maxW = RIGHT - LEFT;
        int size = 104;
        List<String> lines;
        while (true) {
            g.setFont(font(Font.BOLD, size));
            lines = wrap(g, text, maxW);
            if (lines.size() <= 3 || size <= 60) break;
            size -= 8;
        }
        g.setColor(Color.WHITE);
        for (String line : lines) {
            g.drawString(line, LEFT, y + size);
            y += (int) (size * 1.10);
        }
        return y + 6;
    }

    private int drawBrush(Graphics2D g, String text, int left, int y, Color accent) {
        g.setFont(font(Font.BOLD, 42));
        int w = g.getFontMetrics().stringWidth(text) + 60;
        g.rotate(Math.toRadians(-2), left, y);
        g.setColor(accent);
        g.fill(new RoundRectangle2D.Float(left, y - 44, w, 64, 14, 14));
        g.setColor(new Color(0x06, 0x08, 0x0C));
        g.drawString(text, left + 30, y + 2);
        g.rotate(Math.toRadians(2), left, y);
        return y + 36;
    }

    private void drawCallouts(Graphics2D g, PostPlan plan, int left, int right, int y, Color accent, int max, int ch) {
        int cols = 2, gap = 14;
        int cw = (right - left - gap) / cols;
        int i = 0;
        for (String c : plan.callouts) {
            if (i >= max) break;
            int cx = left + (i % cols) * (cw + gap);
            int cy = y + (i / cols) * (ch + gap);
            if (cy + ch > BODY_BOT + 60) break;
            g.setColor(new Color(0x0C, 0x12, 0x1E, 225));
            g.fill(new RoundRectangle2D.Float(cx, cy, cw, ch, 18, 18));
            g.setColor(new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 120));
            g.setStroke(new BasicStroke(2f));
            g.draw(new RoundRectangle2D.Float(cx, cy, cw, ch, 18, 18));
            g.setColor(accent);
            g.fill(new RoundRectangle2D.Float(cx + 16, cy + 22, 9, ch - 44, 5, 5));
            g.setFont(font(Font.BOLD, 26));
            List<String> lines = wrap(g, c, cw - 56);
            int ty = cy + 40;
            g.setColor(Color.WHITE);
            for (String line : lines.subList(0, Math.min(3, lines.size()))) {
                g.drawString(line, cx + 38, ty);
                ty += 34;
            }
            i++;
        }
    }

    private void drawTable(Graphics2D g, PostPlan plan, int left, int right, int y, Color accent) {
        int rows = Math.min(plan.table.size(), 6);
        int rh = 74;
        int h = rows * rh + 20;
        g.setColor(new Color(0x0A, 0x10, 0x1A, 230));
        g.fill(new RoundRectangle2D.Float(left, y, right - left, h, 20, 20));
        int[] colX = {left + 24, left + 300, left + 620};
        int[] colW = {260, 300, right - left - 630};
        for (int r = 0; r < rows; r++) {
            List<String> row = plan.table.get(r);
            int ry = y + 10 + r * rh;
            if (r == 0) {
                g.setColor(new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 60));
                g.fill(new RoundRectangle2D.Float(left + 6, ry, right - left - 12, rh - 6, 14, 14));
            }
            g.setFont(font(r == 0 ? Font.BOLD : Font.PLAIN, 27));
            for (int c = 0; c < Math.min(3, row.size()); c++) {
                g.setColor(r == 0 ? accent : (c == 0 ? new Color(0x9F, 0xB2, 0xC8) : Color.WHITE));
                List<String> lines = wrap(g, row.get(c), colW[c]);
                g.drawString(lines.get(0), colX[c], ry + 38);
                if (lines.size() > 1) g.drawString(lines.get(1), colX[c], ry + 64);
            }
            if (r > 0) {
                g.setColor(new Color(0xFF, 0xFF, 0xFF, 26));
                g.drawLine(left + 20, ry, right - 20, ry);
            }
        }
        if (!plan.verdictA.isEmpty()) {
            g.setFont(font(Font.BOLD, 30));
            g.setColor(accent);
            g.drawString(safe(plan.verdictA), left + 8, y + h + 46);
            g.setColor(Color.WHITE);
            g.drawString(safe(plan.verdictB), left + 8, y + h + 90);
        }
    }

    private void drawQuote(Graphics2D g, PostPlan plan, int left, int right, int y, Color accent) {
        int h = 220;
        g.setColor(new Color(0x0C, 0x12, 0x1E, 235));
        g.fill(new RoundRectangle2D.Float(left, y, right - left, h, 24, 24));
        g.setColor(accent);
        g.setStroke(new BasicStroke(3f));
        g.draw(new RoundRectangle2D.Float(left, y, right - left, h, 24, 24));
        g.setFont(font(Font.BOLD, 80));
        g.setColor(accent);
        g.drawString("\u201C", left + 24, y + 84);
        g.setFont(font(Font.ITALIC, 32));
        g.setColor(Color.WHITE);
        List<String> lines = wrap(g, plan.quote, right - left - 130);
        int ty = y + 84;
        for (String line : lines.subList(0, Math.min(3, lines.size()))) {
            g.drawString(line, left + 90, ty);
            ty += 40;
        }
        g.setFont(font(Font.BOLD, 26));
        g.setColor(accent);
        g.drawString("\u2713 VERIFIED \u2014 " + plan.quoteSource.toUpperCase(), left + 90, y + h - 26);
    }

    private void drawCta(Graphics2D g, String text, int left, int right, int y, Color accent) {
        int h = 104;
        g.setColor(new Color(0x05, 0x08, 0x0D, 235));
        g.fill(new RoundRectangle2D.Float(left, y, right - left, h, 20, 20));
        g.setColor(accent);
        g.setStroke(new BasicStroke(3f));
        g.draw(new RoundRectangle2D.Float(left, y, right - left, h, 20, 20));
        int size = 36;
        g.setFont(font(Font.BOLD, size));
        while (g.getFontMetrics().stringWidth(text) > right - left - 80 && size > 22) {
            size -= 2;
            g.setFont(font(Font.BOLD, size));
        }
        FontMetrics fm = g.getFontMetrics();
        g.setColor(Color.WHITE);
        g.drawString(text, left + (right - left - fm.stringWidth(text)) / 2, y + h / 2 + size / 3);
    }

    private void radialGlow(Graphics2D g, double cx, double cy, double r, Color c, float alpha) {
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
        for (int i = 0; i < 20; i++) {
            double rr = r * (1 - i / 20.0);
            g.setColor(c);
            g.fill(new Ellipse2D.Double(cx - rr, cy - rr, rr * 2, rr * 2));
        }
        g.setComposite(AlphaComposite.SrcOver);
    }

    // ---------- text helpers ----------

    private static Font font(int style, int size) { return new Font("SansSerif", style, size); }

    private static List<String> wrap(Graphics2D g, String text, int maxW) {
        List<String> out = new ArrayList<>();
        FontMetrics fm = g.getFontMetrics();
        for (String paragraph : text.split("\n")) {
            StringBuilder cur = new StringBuilder();
            for (String word : paragraph.split("\\s+")) {
                if (word.isEmpty()) continue;
                String test = cur.length() == 0 ? word : cur + " " + word;
                if (fm.stringWidth(test) > maxW && cur.length() > 0) {
                    out.add(cur.toString());
                    cur = new StringBuilder(word);
                } else {
                    cur = new StringBuilder(test);
                }
            }
            if (cur.length() > 0) out.add(cur.toString());
        }
        if (out.isEmpty()) out.add("");
        return out;
    }

    private static String safe(String s) {
        return s.length() > 70 ? s.substring(0, 67) + "..." : s;
    }
}
