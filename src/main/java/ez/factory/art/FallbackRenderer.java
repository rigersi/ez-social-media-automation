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
import java.awt.Font;
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
 * Guarantees by construction:
 *  - 1080x1920, 9:16
 *  - top-left logo zone left empty (brand stamp pastes the real logo later)
 *  - TikTok/Shorts safe zones calm: right button rail (x>940, y 850-1560) and the bottom
 *    caption strip (y>1660) carry no content
 *  - on-image text only: hook/headline/accent/callouts/table/tags/CTA — caption and hashtags
 *    are never drawn.
 */
public final class FallbackRenderer implements ArtGenerator {

    private static final String TAG = "renderer";
    private final Config cfg;

    public FallbackRenderer(Config cfg) { this.cfg = cfg; }

    private static final Color[] ACCENTS = {
            new Color(0x00, 0xE6, 0x76), new Color(0x29, 0x79, 0xFF), new Color(0xFF, 0x17, 0x44),
            new Color(0x00, 0xE5, 0xFF), new Color(0xFF, 0x91, 0x00), new Color(0xD5, 0x00, 0xF9)
    };

    @Override
    public File generate(PostPlan plan, File outFile) throws IOException {
        int W = cfg.canvasW, H = cfg.canvasH;
        BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

        Color accent = ACCENTS[Math.abs(plan.id == null ? 0 : plan.id.hashCode()) % ACCENTS.length];

        // background
        g.setPaint(new GradientPaint(0, 0, new Color(0x04, 0x06, 0x0B), 0, H, new Color(0x0A, 0x10, 0x1E)));
        g.fillRect(0, 0, W, H);
        radialGlow(g, W * 0.66, H * 0.40, 620, accent, 0.055f);
        radialGlow(g, W * 0.18, H * 0.82, 460, accent, 0.035f);

        int left = 64, right = 944; // right rail safe zone starts at 940
        int y = 300;

        // chips: series tag / concept visual
        List<String> chips = new ArrayList<>();
        if (!plan.seriesTag.isEmpty()) chips.add(plan.seriesTag);
        if (plan.conceptVisual) chips.add("CONCEPT VISUAL");
        if (!chips.isEmpty()) {
            int cx = left;
            for (String c : chips) {
                Font f = font(Font.BOLD, 30);
                g.setFont(f);
                int w = g.getFontMetrics().stringWidth(c) + 44;
                g.setColor(new Color(0x00, 0x00, 0x00, 150));
                g.fill(new RoundRectangle2D.Float(cx, y - 40, w, 56, 28, 28));
                g.setColor(accent);
                g.setStroke(new BasicStroke(2.5f));
                g.draw(new RoundRectangle2D.Float(cx, y - 40, w, 56, 28, 28));
                g.setColor(accent);
                g.drawString(c, cx + 22, y - 1);
                cx += w + 18;
            }
            y += 60;
        }

        // headline (hook) — big, wrapped, auto-fit
        y = drawHeadline(g, plan.headline.toUpperCase(), left, right, y, accent);

        // accent brush phrase
        y = drawBrush(g, plan.accent.toUpperCase(), left, y + 44, accent);

        // body zone by archetype (kept above y=1470)
        int bodyTop = Math.max(y + 70, 920);
        switch (plan.archetype == null ? ez.factory.plan.Archetype.WEIRD_GADGET : plan.archetype) {
            case COMPARISON:
                drawTable(g, plan, left, right, bodyTop, accent);
                break;
            case CREATOR_VERIFIED:
                drawQuote(g, plan, left, right, bodyTop, accent);
                drawCallouts(g, plan, left, right, bodyTop + 330, accent, 2);
                break;
            default:
                drawCallouts(g, plan, left, right, bodyTop, accent, 6);
        }

        // CTA bar (above the calm bottom strip)
        drawCta(g, plan.cta.toUpperCase(), left, right, 1500, accent);

        // logo zone stays empty: (36,36)-(376,226)
        g.dispose();
        ImageIO.write(img, "png", outFile);
        Log.info(TAG, "base layout rendered: " + outFile.getName());
        return outFile;
    }

    // ---------- pieces ----------

    private int drawHeadline(Graphics2D g, String text, int left, int right, int y, Color accent) {
        int maxW = right - left;
        int size = 108;
        List<String> lines;
        while (true) {
            g.setFont(font(Font.BOLD, size));
            lines = wrap(g, text, maxW);
            if (lines.size() <= 4 || size <= 56) break;
            size -= 8;
        }
        g.setColor(Color.WHITE);
        for (String line : lines) {
            g.drawString(line, left, y + size);
            y += (int) (size * 1.12);
        }
        // accent underline stroke below the last line
        g.setColor(accent);
        g.setStroke(new BasicStroke(10f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.drawLine(left + 4, y + 12, left + 180, y + 12);
        return y + 26;
    }

    private int drawBrush(Graphics2D g, String text, int left, int y, Color accent) {
        g.setFont(font(Font.BOLD, 44));
        int w = g.getFontMetrics().stringWidth(text) + 60;
        g.rotate(Math.toRadians(-2), left, y);
        g.setColor(accent);
        g.fill(new RoundRectangle2D.Float(left, y - 46, w, 66, 14, 14));
        g.setColor(new Color(0x06, 0x08, 0x0C));
        g.drawString(text, left + 30, y + 2);
        g.rotate(Math.toRadians(2), left, y);
        return y + 40;
    }

    private void drawCallouts(Graphics2D g, PostPlan plan, int left, int right, int y, Color accent, int max) {
        int cols = 2, gap = 16;
        int cw = (right - left - gap) / cols;
        int ch = 156;
        int i = 0;
        for (String c : plan.callouts) {
            if (i >= max) break;
            int cx = left + (i % cols) * (cw + gap);
            int cy = y + (i / cols) * (ch + gap);
            if (cy + ch > 1460) break;
            g.setColor(new Color(0x0C, 0x12, 0x1E, 225));
            g.fill(new RoundRectangle2D.Float(cx, cy, cw, ch, 18, 18));
            g.setColor(new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 120));
            g.setStroke(new BasicStroke(2f));
            g.draw(new RoundRectangle2D.Float(cx, cy, cw, ch, 18, 18));
            g.setColor(accent);
            g.fill(new RoundRectangle2D.Float(cx + 18, cy + 26, 10, ch - 52, 5, 5));
            g.setFont(font(Font.BOLD, 27));
            List<String> lines = wrap(g, c, cw - 62);
            int ty = cy + 44;
            g.setColor(Color.WHITE);
            for (String line : lines.subList(0, Math.min(3, lines.size()))) {
                g.drawString(line, cx + 42, ty);
                ty += 36;
            }
            i++;
        }
    }

    private void drawTable(Graphics2D g, PostPlan plan, int left, int right, int y, Color accent) {
        int rows = Math.min(plan.table.size(), 6);
        int rh = 78;
        int h = rows * rh + 20;
        g.setColor(new Color(0x0A, 0x10, 0x1A, 230));
        g.fill(new RoundRectangle2D.Float(left, y, right - left, h, 20, 20));
        int[] colX = {left + 24, left + 300, left + 630};
        int[] colW = {260, 310, right - left - 640};
        for (int r = 0; r < rows; r++) {
            List<String> row = plan.table.get(r);
            int ry = y + 10 + r * rh;
            if (r == 0) {
                g.setColor(new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 60));
                g.fill(new RoundRectangle2D.Float(left + 6, ry, right - left - 12, rh - 6, 14, 14));
            }
            g.setFont(font(r == 0 ? Font.BOLD : Font.PLAIN, 28));
            for (int c = 0; c < Math.min(3, row.size()); c++) {
                g.setColor(r == 0 ? accent : (c == 0 ? new Color(0x9F, 0xB2, 0xC8) : Color.WHITE));
                List<String> lines = wrap(g, row.get(c), colW[c]);
                g.drawString(lines.get(0), colX[c], ry + 40);
                if (lines.size() > 1) g.drawString(lines.get(1), colX[c], ry + 68);
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
        int h = 260;
        g.setColor(new Color(0x0C, 0x12, 0x1E, 235));
        g.fill(new RoundRectangle2D.Float(left, y, right - left, h, 24, 24));
        g.setColor(accent);
        g.setStroke(new BasicStroke(3f));
        g.draw(new RoundRectangle2D.Float(left, y, right - left, h, 24, 24));
        g.setFont(font(Font.BOLD, 90));
        g.setColor(accent);
        g.drawString("\u201C", left + 26, y + 92);
        g.setFont(font(Font.ITALIC, 34));
        g.setColor(Color.WHITE);
        List<String> lines = wrap(g, plan.quote, right - left - 140);
        int ty = y + 92;
        for (String line : lines.subList(0, Math.min(3, lines.size()))) {
            g.drawString(line, left + 96, ty);
            ty += 44;
        }
        g.setFont(font(Font.BOLD, 28));
        g.setColor(accent);
        g.drawString("\u2713 VERIFIED \u2014 " + plan.quoteSource.toUpperCase(), left + 96, y + h - 30);
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
        for (int i = 0; i < 24; i++) {
            double rr = r * (1 - i / 24.0);
            g.setColor(c);
            g.fill(new java.awt.geom.Ellipse2D.Double(cx - rr, cy - rr, rr * 2, rr * 2));
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
