package ez.factory.feedback;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import ez.factory.config.Config;
import ez.factory.llm.OllamaClient;
import ez.factory.util.IO;
import ez.factory.util.Json;
import ez.factory.util.Log;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Feedback intake API — the single door through which instructions reach the factory:
 *
 *   POST /feedback   {"source":"human|qa","text":"...","runs_left":10,
 *                     "hints":{"archetype":"comparison","ban_topics":[...],"hashtag_add":[...]},
 *                     "screenshot_base64":"...","screenshot_ext":"png"}
 *                    (text/plain body also accepted = plain directive)
 *   GET  /feedback   -> all directives with status
 *   GET  /status     -> factory status (next slot, recent posts, active directives)
 *   GET  /health     -> ok
 *
 * The owner uses it from scripts/phone tools; the phase-2 Quality-Assurance module uses
 * the exact same endpoints to push adaptations (that is the "leave room for feedback"
 * contract). Optional shared-secret: feedback.token -> header X-Feedback-Token.
 */
public final class FeedbackServer {

    private static final String TAG = "feedback";
    private final Config cfg;
    private final DirectiveStore store;
    private final Supplier<Map<String, Object>> statusSupplier;
    private HttpServer server;

    public FeedbackServer(Config cfg, DirectiveStore store, Supplier<Map<String, Object>> statusSupplier) {
        this.cfg = cfg;
        this.store = store;
        this.statusSupplier = statusSupplier;
    }

    public void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("0.0.0.0", cfg.feedbackPort), 0);
        server.createContext("/feedback", this::handleFeedback);
        server.createContext("/status", ex -> send(ex, 200, Json.pretty(statusSupplier.get())));
        server.createContext("/health", ex -> send(ex, 200, "{\"ok\":true}"));
        server.setExecutor(java.util.concurrent.Executors.newFixedThreadPool(4));
        server.start();
        Log.info(TAG, "feedback API listening on :" + cfg.feedbackPort + " (POST /feedback, GET /status)");
    }

    public void stop() { if (server != null) server.stop(0); }

    private void handleFeedback(HttpExchange ex) throws IOException {
        try {
            if ("OPTIONS".equalsIgnoreCase(ex.getRequestMethod())) {
                ex.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
                ex.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type, X-Feedback-Token");
                ex.getResponseHeaders().add("Access-Control-Allow-Methods", "POST, GET, OPTIONS");
                send(ex, 204, "");
                return;
            }
            if (!authed(ex)) { send(ex, 401, "{\"error\":\"bad token\"}"); return; }
            if ("GET".equalsIgnoreCase(ex.getRequestMethod())) {
                List<Object> l = new ArrayList<>();
                for (Directive d : store.list()) l.add(d.toJson());
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("directives", l);
                send(ex, 200, Json.pretty(m));
                return;
            }
            if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) { send(ex, 405, "{\"error\":\"use POST or GET\"}"); return; }

            String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            Directive d = new Directive();
            d.runsLeft = cfg.directiveDefaultRuns;
            String ctype = ex.getRequestHeaders().getFirst("Content-Type");
            if (ctype != null && ctype.contains("application/json") && body.trim().startsWith("{")) {
                Map<String, Object> m = Json.parseObject(body);
                d.source = Json.str(m, "source", "human");
                d.text = Json.str(m, "text", "");
                d.runsLeft = Json.int_(m, "runs_left", cfg.directiveDefaultRuns);
                d.hints = Json.sub(m, "hints");
                String b64 = Json.str(m, "screenshot_base64", "");
                if (!b64.isEmpty()) d.screenshotPath = saveScreenshot(b64, Json.str(m, "screenshot_ext", "png"), d.id);
            } else {
                d.text = body.trim();
            }
            if (d.text.isEmpty() && d.screenshotPath.isEmpty()) { send(ex, 400, "{\"error\":\"empty feedback\"}"); return; }
            if (d.id == null || d.id.isEmpty()) d.id = DirectiveStore.newId();
            Directive saved = store.add(d);
            enrichWithVisionAsync(saved);
            Map<String, Object> res = new LinkedHashMap<>();
            res.put("ok", true);
            res.put("id", saved.id);
            send(ex, 200, Json.pretty(res));
        } catch (Exception e) {
            Log.err(TAG, "feedback request failed: " + e);
            send(ex, 500, "{\"error\":\"" + e.getMessage() + "\"}");
        }
    }

    private String saveScreenshot(String b64, String ext, String id) throws IOException {
        byte[] data = Base64.getDecoder().decode(b64);
        String name = "shot-" + id + "." + ext;
        IO.writeBytes(cfg.feedbackScreenshots.resolve(name), data);
        return cfg.feedbackScreenshots.resolve(name).toString();
    }

    /** If a local vision model is configured, turn the screenshot into a planner-readable note. */
    private void enrichWithVisionAsync(Directive d) {
        if (d.screenshotPath.isEmpty() || cfg.visionModel.isEmpty() || !"ollama".equals(cfg.llmProvider)) return;
        Thread t = new Thread(() -> {
            try {
                byte[] png = java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(d.screenshotPath));
                String note = new OllamaClient(cfg.ollamaUrl, cfg.llmModel, 0.2)
                        .vision(cfg.visionModel, png,
                                "Describe this social-media analytics/design screenshot in 3 sentences and state "
                              + "one concrete adaptation instruction for a content factory (what to change in future posts).");
                d.visionNote = note.trim();
                store.save();
                Log.info(TAG, "vision note for " + d.id + ": " + note.trim());
            } catch (Exception e) {
                Log.warn(TAG, "vision enrichment failed for " + d.id + ": " + e.getMessage());
            }
        });
        t.setDaemon(true);
        t.start();
    }

    private boolean authed(HttpExchange ex) {
        if (cfg.feedbackToken.isEmpty()) return true;
        String got = ex.getRequestHeaders().getFirst("X-Feedback-Token");
        if (got == null) {
            String q = ex.getRequestURI().getQuery();
            if (q != null && q.contains("token=")) got = q.substring(q.indexOf("token=") + 6);
        }
        return cfg.feedbackToken.equals(got);
    }

    private static void send(HttpExchange ex, int code, String body) throws IOException {
        byte[] b = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().add("Content-Type", "application/json");
        ex.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
        if (code == 204) { ex.sendResponseHeaders(204, -1); ex.close(); return; }
        ex.sendResponseHeaders(code, b.length);
        try (OutputStream os = ex.getResponseBody()) { os.write(b); }
        ex.close();
    }
}
