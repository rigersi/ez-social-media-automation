package ez.factory.llm;

import ez.factory.util.Http;
import ez.factory.util.Json;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Any OpenAI-compatible /chat/completions server: LM Studio, llama.cpp server, OpenRouter,
 * Groq free tier, etc. Configure baseUrl + apiKey + model in config.json.
 */
public final class OpenAiCompatClient implements LlmClient {

    private final String baseUrl;
    private final String apiKey;
    private final String model;
    private final double temperature;

    public OpenAiCompatClient(String baseUrl, String apiKey, String model, double temperature) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.apiKey = apiKey;
        this.model = model;
        this.temperature = temperature;
    }

    @Override
    public String name() { return "openai-compat:" + model; }

    @Override
    public String chat(String system, String user) throws IOException {
        Map<String, Object> req = new LinkedHashMap<>();
        req.put("model", model);
        req.put("temperature", temperature);
        req.put("max_tokens", 2500);
        List<Object> messages = new ArrayList<>();
        messages.add(msg("system", system));
        messages.add(msg("user", user));
        req.put("messages", messages);
        Map<String, Object> rf = new LinkedHashMap<>();
        rf.put("type", "json_object");
        req.put("response_format", rf);
        Map<String, String> headers = new LinkedHashMap<>();
        if (!apiKey.isEmpty()) headers.put("Authorization", "Bearer " + apiKey);
        String res = Http.postJson(baseUrl + "/chat/completions", Json.stringify(req), headers, 600);
        Map<String, Object> m = Json.parseObject(res);
        List<Object> choices = Json.list(m, "choices");
        if (choices.isEmpty()) throw new IOException("No choices from " + baseUrl);
        String content = Json.str(Json.sub(choices.get(0), "message"), "content", "");
        if (content.isEmpty()) throw new IOException("Empty content from " + baseUrl);
        return content;
    }

    private static Map<String, Object> msg(String role, String content) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("role", role);
        m.put("content", content);
        return m;
    }
}
