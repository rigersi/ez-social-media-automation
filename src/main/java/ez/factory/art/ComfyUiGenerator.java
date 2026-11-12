package ez.factory.art;

import ez.factory.config.Config;
import ez.factory.plan.PostPlan;
import ez.factory.util.Http;
import ez.factory.util.IO;
import ez.factory.util.Json;
import ez.factory.util.Log;

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Rich-poster pass through local ComfyUI (free): Qwen-Image-Edit fp8.
 *
 * v4 pipeline (masked inpaint + polish):
 *   1. factory renders the deterministic base layout (text, cards, safe zones)
 *   2. factory renders the hero mask (white = repaint zone over the empty stage)
 *   3. both PNGs are uploaded and wired into LoadImage nodes (baseImageNodeId, maskImageNodeId)
 *   4. art_prompt -> promptNodeId; a fixed polish prompt -> polishPromptNodeId
 *   5. KS1 repaints ONLY the masked zone at denoise 1.0 (text pixels never resampled)
 *      KS2 lays a gentle cinematic polish at denoise 0.3
 *   6. output image fetched via /view; logo stamped afterwards by BrandStamp
 */
public final class ComfyUiGenerator implements ArtGenerator {

    private static final String TAG = "comfyui";
    private static final String POLISH_PROMPT =
            "Global cinematic polish: add subtle film grain, soft volumetric light, glossy material "
          + "highlights and deep rich blacks across the whole poster; keep every letter of text "
          + "perfectly crisp and word-for-word unchanged; keep the top-left logo zone empty and the "
          + "right-side and bottom safe zones calm; add no new text.";
    private final Config cfg;

    public ComfyUiGenerator(Config cfg) { this.cfg = cfg; }

    public boolean reachable() {
        try {
            Http.get(cfg.comfyUrl + "/system_stats", null, 5);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public File generate(PostPlan plan, File baseLayout, File maskFile) throws IOException {
        File workflowFile = cfg.comfyWorkflowPath.toFile();
        if (!workflowFile.exists()) throw new IOException("ComfyUI workflow not found: " + workflowFile);
        Map<String, Object> workflow = Json.parseObject(IO.read(workflowFile.toPath()));

        Map<String, Object> promptNode = Json.obj(workflow.get(cfg.comfyPromptNodeId));
        if (promptNode.isEmpty()) throw new IOException("promptNodeId '" + cfg.comfyPromptNodeId + "' not in workflow");
        Json.obj(promptNode.get("inputs")).put(cfg.comfyPromptField, plan.artPrompt);

        Map<String, Object> polishNode = Json.obj(workflow.get(cfg.comfyPolishPromptNodeId));
        if (!polishNode.isEmpty()) Json.obj(polishNode.get("inputs")).put(cfg.comfyPromptField, POLISH_PROMPT);

        if (!cfg.comfyBaseImageNodeId.isEmpty()) {
            String base = uploadImage(baseLayout);
            Map<String, Object> n = Json.obj(workflow.get(cfg.comfyBaseImageNodeId));
            if (n.isEmpty()) throw new IOException("baseImageNodeId '" + cfg.comfyBaseImageNodeId + "' not in workflow");
            Json.obj(n.get("inputs")).put("image", base);
        }
        if (!cfg.comfyMaskImageNodeId.isEmpty() && maskFile != null && maskFile.exists()) {
            String mask = uploadImage(maskFile);
            Map<String, Object> n = Json.obj(workflow.get(cfg.comfyMaskImageNodeId));
            if (!n.isEmpty()) Json.obj(n.get("inputs")).put("image", mask);
        }

        Map<String, Object> req = new LinkedHashMap<>();
        req.put("prompt", workflow);
        req.put("client_id", UUID.randomUUID().toString());
        String queued = Http.postJson(cfg.comfyUrl + "/prompt", Json.stringify(req), null, 60);
        String promptId = Json.str(Json.parseObject(queued), "prompt_id", "");
        if (promptId.isEmpty()) throw new IOException("ComfyUI did not return prompt_id");
        Log.info(TAG, "queued prompt " + promptId + " (masked paint + polish)");

        long deadline = System.currentTimeMillis() + cfg.comfyTimeoutSec * 1000L;
        while (System.currentTimeMillis() < deadline) {
            sleep(3000);
            String hist = Http.get(cfg.comfyUrl + "/history/" + promptId, null, 30);
            Map<String, Object> h = Json.parseObject(hist);
            if (!h.containsKey(promptId)) continue;
            Map<String, Object> entry = Json.sub(h, promptId);
            Map<String, Object> status = Json.sub(entry, "status");
            if (Json.bool(status, "is_error", false)) {
                throw new IOException("ComfyUI job errored: " + Json.stringify(status));
            }
            Map<String, Object> outputs = Json.sub(entry, "outputs");
            for (Object nodeOut : outputs.values()) {
                List<Object> images = Json.list(Json.obj(nodeOut), "images");
                if (images.isEmpty()) continue;
                Map<String, Object> img = Json.obj(images.get(0));
                byte[] data = fetchView(Json.str(img, "filename", ""), Json.str(img, "subfolder", ""), Json.str(img, "type", "output"));
                File out = new File(baseLayout.getParentFile(), plan.id + "-comfy.png");
                IO.writeBytes(out.toPath(), data);
                Log.info(TAG, "poster received: " + Json.str(img, "filename", ""));
                return out;
            }
        }
        throw new IOException("ComfyUI timeout after " + cfg.comfyTimeoutSec + "s");
    }

    private String uploadImage(File f) throws IOException {
        String res = Http.postFile(cfg.comfyUrl + "/upload/image", f, "image", null, 120);
        String name = Json.str(Json.parseObject(res), "name", "");
        if (name.isEmpty()) throw new IOException("ComfyUI upload failed");
        return name;
    }

    private byte[] fetchView(String filename, String subfolder, String type) throws IOException {
        String url = cfg.comfyUrl + "/view?filename=" + enc(filename) + "&subfolder=" + enc(subfolder) + "&type=" + enc(type);
        return Http.getBytes(url, null, 120);
    }

    private static String enc(String s) {
        try { return java.net.URLEncoder.encode(s, "UTF-8"); } catch (Exception e) { return s; }
    }

    private static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}
