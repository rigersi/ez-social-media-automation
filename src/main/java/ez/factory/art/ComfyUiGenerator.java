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
 * Rich-poster pass through local ComfyUI (free): Qwen-Image-Edit fp8 + Lightning LoRA.
 *
 * How it works (matches the brand pipeline):
 *   1. The deterministic renderer first draws the base layout (text blocks, table, safe zones).
 *   2. The base PNG is uploaded to ComfyUI and wired into the workflow's LoadImage node
 *      (config comfyui.baseImageNodeId — leave empty if your workflow starts from scratch).
 *   3. The plan's art_prompt is injected into the text node (config comfyui.promptNodeId /
 *      promptField, e.g. node "6", field "text").
 *   4. /prompt queues the job; /history is polled; the output image is fetched via /view.
 *
 * Export YOUR workflow from ComfyUI with "Save (API Format)" to comfyui/workflow.json and set
 * the two node ids in config.json — no code changes needed for your exact graph.
 */
public final class ComfyUiGenerator implements ArtGenerator {

    private static final String TAG = "comfyui";
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
    @SuppressWarnings("unchecked")
    public File generate(PostPlan plan, File baseLayout) throws IOException {
        File workflowFile = cfg.comfyWorkflowPath.toFile();
        if (!workflowFile.exists()) throw new IOException("ComfyUI workflow not found: " + workflowFile);
        Map<String, Object> workflow = Json.parseObject(IO.read(workflowFile.toPath()));

        // inject the art prompt
        Map<String, Object> promptNode = Json.obj(workflow.get(cfg.comfyPromptNodeId));
        if (promptNode.isEmpty()) throw new IOException("promptNodeId '" + cfg.comfyPromptNodeId + "' not in workflow");
        Map<String, Object> inputs = Json.obj(promptNode.get("inputs"));
        inputs.put(cfg.comfyPromptField, plan.artPrompt);

        // wire the deterministic base layout into the edit chain
        if (!cfg.comfyBaseImageNodeId.isEmpty()) {
            String uploaded = uploadImage(baseLayout);
            Map<String, Object> baseNode = Json.obj(workflow.get(cfg.comfyBaseImageNodeId));
            if (baseNode.isEmpty()) throw new IOException("baseImageNodeId '" + cfg.comfyBaseImageNodeId + "' not in workflow");
            Json.obj(baseNode.get("inputs")).put("image", uploaded);
        }

        Map<String, Object> req = new LinkedHashMap<>();
        req.put("prompt", workflow);
        req.put("client_id", UUID.randomUUID().toString());
        String queued = Http.postJson(cfg.comfyUrl + "/prompt", Json.stringify(req), null, 60);
        String promptId = Json.str(Json.parseObject(queued), "prompt_id", "");
        if (promptId.isEmpty()) throw new IOException("ComfyUI did not return prompt_id");
        Log.info(TAG, "queued prompt " + promptId);

        long deadline = System.currentTimeMillis() + cfg.comfyTimeoutSec * 1000L;
        while (System.currentTimeMillis() < deadline) {
            sleep(3000);
            String hist = Http.get(cfg.comfyUrl + "/history/" + promptId, null, 30);
            Map<String, Object> h = Json.parseObject(hist);
            if (!h.containsKey(promptId)) continue;
            Map<String, Object> entry = Json.sub(h, promptId);
            Map<String, Object> status = Json.sub(entry, "status");
            if (Json.bool(status, "is_error", false)) {
                throw new IOException("ComfyUI job errored: " + Json.stringify(status).substring(0, Math.min(400, Json.stringify(status).length())));
            }
            Map<String, Object> outputs = Json.sub(entry, "outputs");
            for (Object nodeOut : outputs.values()) {
                List<Object> images = Json.list(Json.obj(nodeOut), "images");
                if (images.isEmpty()) continue;
                Map<String, Object> img = Json.obj(images.get(0));
                byte[] data = fetchView(Json.str(img, "filename", ""), Json.str(img, "subfolder", ""), Json.str(img, "type", "output"));
                IO.writeBytes(outPath(plan, baseLayout).toPath(), data);
                Log.info(TAG, "poster received: " + Json.str(img, "filename", ""));
                return outPath(plan, baseLayout);
            }
        }
        throw new IOException("ComfyUI timeout after " + cfg.comfyTimeoutSec + "s");
    }

    private File outPath(PostPlan plan, File baseLayout) {
        return new File(baseLayout.getParentFile(), plan.id + "-comfy.png");
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
