package ez.factory.llm;

import ez.factory.util.Http;
import ez.factory.util.Json;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Free local LLM via Ollama (recommended: qwen2.5:7b for captions, minicpm-v or llava for
 * screenshot feedback). Runs entirely on the owner's PC — no API keys, no cost.
 */
public final class OllamaClient implements LlmClient {

    private final String baseUrl;
    private final String model;
    private final double temperature;

    public OllamaClient(String baseUrl, String model, double temperature) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.model = model;
        this.temperature = temperature;
    }

    @Override
    public String name() { return "ollama:" + model; }

    @Override
    public String chat(String system, String user) throws IOException {
        Map<String, Object> req = new LinkedHashMap<>();
        req.put("model", model);
        req.put("stream", Boolean.FALSE);
        req.put("format", "json");
        List<Object> messages = new ArrayList<>();
        messages.add(msg("system", system));
        messages.add(msg("user", user));
        req.put("messages", messages);
        Map<String, Object> options = new LinkedHashMap<>();
        options.put("temperature", temperature);
        req.put("options", options);
        String res = Http.postJson(baseUrl + "/api/chat", Json.stringify(req), null, 600);
        Map<String, Object> m = Json.parseObject(res);
        String content = Json.str(Json.sub(m, "message"), "content", "");
        if (content.isEmpty()) throw new IOException("Ollama returned empty content");
        return content;
    }

    /** Vision pass for feedback screenshots (optional; needs a vision model pulled in Ollama). */
    public String vision(String visionModel, byte[] png, String prompt) throws IOException {
        Map<String, Object> req = new LinkedHashMap<>();
        req.put("model", visionModel);
        req.put("stream", Boolean.FALSE);
        req.put("prompt", prompt);
        List<Object> imgs = new ArrayList<>();
        imgs.add(Base64.getEncoder().encodeToString(png));
        req.put("images", imgs);
        String res = Http.postJson(baseUrl + "/api/generate", Json.stringify(req), null, 600);
        return Json.str(Json.parseObject(res), "response", "");
    }

    private static Map<String, Object> msg(String role, String content) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("role", role);
        m.put("content", content);
        return m;
    }
}
