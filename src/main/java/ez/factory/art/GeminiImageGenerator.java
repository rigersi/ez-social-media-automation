package ez.factory.art;

import ez.factory.config.Config;
import ez.factory.plan.PostPlan;
import ez.factory.util.Http;
import ez.factory.util.Json;
import ez.factory.util.Log;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Paid-quality painter: Google Gemini image models ("Nano Banana" family) via the
 * Gemini API. Text-safety by construction — we NEVER send the whole poster: only the
 * hero-stage rectangle (same rect as HeroMask) leaves the machine, gets painted at full
 * creative strength, and is composited back in Java. Headline, cards, CTA and table
 * pixels are never touched by any model.
 *
 * Cost model: pay-per-image (no subscription). Model id is a config key, e.g.
 * gemini-3.1-flash-image (Nano Banana 2, cheap) or gemini-3-pro-image (Nano Banana Pro,
 * maximum quality).
 */
public final class GeminiImageGenerator implements ArtGenerator {

    private static final String TAG = "gemini";

    private final Config cfg;

    public GeminiImageGenerator(Config cfg) {
        this.cfg = cfg;
    }

    public String name() { return "Gemini " + cfg.geminiModel; }

    @Override
    public File generate(PostPlan plan, File baseLayout, File maskFile) throws IOException {
        int paintsSoFar = readOdometer();
        if (cfg.geminiMonthlyCap > 0 && paintsSoFar >= cfg.geminiMonthlyCap) {
            throw new IOException("monthly paint cap reached (" + paintsSoFar + "/" + cfg.geminiMonthlyCap
                    + ") — hero window stays skeleton until the 1st of next month (or raise art.gemini.monthlyCapImages)");
        }
        if ("designer".equalsIgnoreCase(cfg.artPosterMode)) {
            return generateDesigner(plan, baseLayout, paintsSoFar);
        }
        BufferedImage base = ImageIO.read(baseLayout);
        if (base == null) throw new IOException("cannot read base layout " + baseLayout);
        int[] r = HeroMask.rect(cfg);
        int x = Math.max(0, Math.min(r[0], base.getWidth() - 2));
        int y = Math.max(0, Math.min(r[1], base.getHeight() - 2));
        int w = Math.max(2, Math.min(r[2], base.getWidth() - x));
        int h = Math.max(2, Math.min(r[3], base.getHeight() - y));

        BufferedImage crop = base.getSubimage(x, y, w, h);
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        ImageIO.write(crop, "png", bo);
        String b64 = Base64.getEncoder().encodeToString(bo.toByteArray());

        String instruction =
                "This rectangle is the artwork window of a 9:16 social-media poster for a tech-gadget brand. "
              + "Paint the entire window, edge to edge, as one rich cinematic visual: "
              + (plan.artPrompt == null || plan.artPrompt.isEmpty() ? "a premium tech product hero scene" : plan.artPrompt)
              + " Style: high-end advertising photography merged with clean 3D render; dramatic studio lighting, "
              + "realistic materials and reflections, deep saturated colour grading, fine detail, subtle depth of field. "
              + "HARD RULES: absolutely no text, no letters, no numbers, no logos, no watermarks, no frames or borders; "
              + "the visual must fill the whole rectangle.";

        Map<String, Object> imageConfig = new LinkedHashMap<>();
        imageConfig.put("aspectRatio", w >= h ? "16:9" : "9:16");
        Map<String, Object> genCfg = new LinkedHashMap<>();
        genCfg.put("imageConfig", imageConfig);

        Map<String, Object> textPart = new LinkedHashMap<>();
        textPart.put("text", instruction);
        Map<String, Object> inline = new LinkedHashMap<>();
        inline.put("mimeType", "image/png");
        inline.put("data", b64);
        Map<String, Object> imgPart = new LinkedHashMap<>();
        imgPart.put("inlineData", inline);
        List<Object> parts = List.of(textPart, imgPart);

        Map<String, Object> content = new LinkedHashMap<>();
        content.put("parts", parts);
        List<Object> contents = List.of(content);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("contents", contents);
        body.put("generationConfig", genCfg);

        String url = "https://generativelanguage.googleapis.com/v1beta/models/" + cfg.geminiModel + ":generateContent";
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("x-goog-api-key", cfg.geminiApiKey);

        Log.info(TAG, "painting hero window via " + cfg.geminiModel + " (" + w + "x" + h + " crop)...");
        String resp = Http.postJson(url, Json.stringify(body), headers, 240);

        String imgB64 = extractImage(resp);
        if (imgB64 == null) {
            String snippet = resp.length() > 500 ? resp.substring(0, 500) + "..." : resp;
            throw new IOException("Gemini returned no image (model id wrong? quota? blocked?): " + snippet);
        }
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(imgB64);
        } catch (IllegalArgumentException e) {
            bytes = Base64.getMimeDecoder().decode(imgB64);
        }
        BufferedImage painted = ImageIO.read(new ByteArrayInputStream(bytes));
        if (painted == null) throw new IOException("Gemini image payload could not be decoded as PNG/JPEG");

        BufferedImage scaled = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D sg = scaled.createGraphics();
        sg.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        sg.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        sg.drawImage(painted, 0, 0, w, h, null);
        sg.dispose();

        Graphics2D g = base.createGraphics();
        g.drawImage(scaled, x, y, null);
        g.dispose();

        String baseName = baseLayout.getName();
        String outName = baseName.endsWith("-layout.png")
                ? baseName.substring(0, baseName.length() - "-layout.png".length()) + "-gemini.png"
                : baseName.replace(".png", "-gemini.png");
        File out = new File(baseLayout.getParentFile(), outName);
        ImageIO.write(base, "png", out);
        writeOdometer(paintsSoFar + 1);
        Log.info(TAG, "hero painted by " + cfg.geminiModel + " -> " + out.getName()
                + " (paint #" + (paintsSoFar + 1) + (cfg.geminiMonthlyCap > 0 ? "/" + cfg.geminiMonthlyCap : "") + " this month)");
        return out;
    }

    /** Designer mode: Nano Banana designs the ENTIRE finished poster (text included) from the creative brief. */
    private File generateDesigner(PostPlan plan, File baseLayout, int paintsSoFar) throws IOException {
        String brief = ez.factory.plan.PosterBriefBuilder.build(plan);

        Map<String, Object> imageConfig = new LinkedHashMap<>();
        imageConfig.put("aspectRatio", "9:16");
        Map<String, Object> genCfg = new LinkedHashMap<>();
        genCfg.put("imageConfig", imageConfig);
        Map<String, Object> textPart = new LinkedHashMap<>();
        textPart.put("text", brief);
        Map<String, Object> content = new LinkedHashMap<>();
        content.put("parts", List.of(textPart));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("contents", List.of(content));
        body.put("generationConfig", genCfg);

        String url = "https://generativelanguage.googleapis.com/v1beta/models/" + cfg.geminiModel + ":generateContent";
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("x-goog-api-key", cfg.geminiApiKey);

        Log.info(TAG, "designer mode: " + cfg.geminiModel + " designing the full poster...");
        String resp = Http.postJson(url, Json.stringify(body), headers, 300);

        String imgB64 = extractImage(resp);
        if (imgB64 == null) {
            String snippet = resp.length() > 500 ? resp.substring(0, 500) + "..." : resp;
            throw new IOException("Gemini returned no image (model id wrong? quota? blocked?): " + snippet);
        }
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(imgB64);
        } catch (IllegalArgumentException e) {
            bytes = Base64.getMimeDecoder().decode(imgB64);
        }
        BufferedImage poster = ImageIO.read(new ByteArrayInputStream(bytes));
        if (poster == null) throw new IOException("Gemini image payload could not be decoded as PNG/JPEG");

        BufferedImage scaled = new BufferedImage(cfg.canvasW, cfg.canvasH, BufferedImage.TYPE_INT_RGB);
        Graphics2D sg = scaled.createGraphics();
        sg.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        sg.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        sg.drawImage(poster, 0, 0, cfg.canvasW, cfg.canvasH, null);
        sg.dispose();

        String baseName = baseLayout.getName();
        String outName = baseName.endsWith("-layout.png")
                ? baseName.substring(0, baseName.length() - "-layout.png".length()) + "-gemini.png"
                : baseName.replace(".png", "-gemini.png");
        File out = new File(baseLayout.getParentFile(), outName);
        ImageIO.write(scaled, "png", out);
        writeOdometer(paintsSoFar + 1);
        Log.info(TAG, "poster designed by " + cfg.geminiModel + " -> " + out.getName()
                + " (paint #" + (paintsSoFar + 1) + (cfg.geminiMonthlyCap > 0 ? "/" + cfg.geminiMonthlyCap : "") + " this month)");
        return out;
    }

    /** Paid-paint odometer: {count} for the current calendar month, stored in data/gemini-paints.json. */
    private int readOdometer() {
        String month = java.time.YearMonth.now().toString();
        try {
            File f = cfg.dataDir.resolve("gemini-paints.json").toFile();
            if (f.exists()) {
                Map<String, Object> m = Json.parseObject(ez.factory.util.IO.read(f.toPath()));
                if (month.equals(Json.str(m, "month", ""))) return Json.int_(m, "count", 0);
            }
        } catch (Exception e) {
            Log.warn(TAG, "odometer read failed: " + e.getMessage());
        }
        return 0;
    }

    private void writeOdometer(int count) {
        try {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("month", java.time.YearMonth.now().toString());
            m.put("count", count);
            File f = cfg.dataDir.resolve("gemini-paints.json").toFile();
            f.getParentFile().mkdirs();
            ez.factory.util.IO.write(f.toPath(), Json.pretty(m));
        } catch (Exception e) {
            Log.warn(TAG, "odometer write failed: " + e.getMessage());
        }
    }

    /** Walks candidates[].content.parts[] looking for an inlineData/inline_data image payload. */
    private static String extractImage(String resp) {
        try {
            Map<String, Object> m = Json.parseObject(resp);
            for (Object cand : Json.list(m, "candidates")) {
                Map<String, Object> content = Json.sub(cand, "content");
                for (Object part : Json.list(content, "parts")) {
                    if (!(part instanceof Map)) continue;
                    Map<String, Object> p = (Map<String, Object>) part;
                    Object id = p.get("inlineData");
                    if (id == null) id = p.get("inline_data");
                    if (id instanceof Map) {
                        String data = Json.str(id, "data", null);
                        if (data != null && !data.isEmpty()) return data;
                    }
                }
            }
        } catch (Exception e) {
            Log.warn(TAG, "response parse failed: " + e.getMessage());
        }
        return null;
    }
}
