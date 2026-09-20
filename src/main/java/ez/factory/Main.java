package ez.factory;

import ez.factory.art.ComfyUiGenerator;
import ez.factory.config.Config;
import ez.factory.feedback.Directive;
import ez.factory.feedback.DirectiveStore;
import ez.factory.feedback.FeedbackServer;
import ez.factory.llm.LlmClient;
import ez.factory.llm.OllamaClient;
import ez.factory.llm.OpenAiCompatClient;
import ez.factory.plan.Planner;
import ez.factory.plan.PostPlan;
import ez.factory.plan.TopicPool;
import ez.factory.plan.TrendPool;
import ez.factory.publish.DryRunPublisher;
import ez.factory.publish.PostizPublisher;
import ez.factory.publish.Publisher;
import ez.factory.schedule.SlotClock;
import ez.factory.state.StateStore;
import ez.factory.util.Http;
import ez.factory.util.Json;
import ez.factory.util.Log;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * EZ GOOD FINDS — CONTENT FACTORY (module 1 of 3).
 *
 * Commands:
 *   daemon                 run forever: feedback API + inbox scan + slot scheduler
 *   run-once [--at ISO]    produce one post now for the given/next slot (default: next slot)
 *   plan                   plan only, print the post package as JSON (no render/publish)
 *   status                 slots, recent posts, active directives
 *   feedback --text "..."  add a directive from the command line (--source qa|human,
 *                          --screenshot path, --runs N)
 *   serve                  feedback API only (useful while building the QA module)
 */
public final class Main {

    private static final String TAG = "main";

    public static void main(String[] args) throws Exception {
        String cmd = args.length > 0 ? args[0] : "daemon";
        Map<String, String> opts = parseOpts(args);
        Path cfgPath = Paths.get(opts.getOrDefault("config", "config/config.json"));
        Config cfg = Config.load(cfgPath);

        StateStore state = new StateStore(cfg.dataDir.resolve("state.json"));
        DirectiveStore directives = new DirectiveStore(cfg.dataDir.resolve("directives.json"), cfg.feedbackInbox);
        TopicPool topics = new TopicPool(cfg.topicsDir, cfg.topicsDir.resolve("seed-topics.json"));
        TrendPool trends = new TrendPool(cfg.hashtagPoolPath);
        LlmClient llm = buildLlm(cfg);
        Planner planner = new Planner(cfg, state, topics, trends, directives, llm);
        Publisher publisher = cfg.livePublish() ? new PostizPublisher(cfg) : new DryRunPublisher(cfg);
        ComfyUiGenerator comfy = buildComfy(cfg);

        switch (cmd) {
            case "daemon":
                daemon(cfg, state, directives, planner, publisher, comfy);
                break;
            case "run-once": {
                directives.scanInbox(cfg.feedbackScreenshots);
                Instant slot = slotFor(opts, cfg);
                Map<String, Object> rec = new Pipeline(cfg, state, planner, directives, publisher, comfy).run(slot);
                System.out.println(Json.pretty(rec));
                break;
            }
            case "plan": {
                PostPlan p = planner.plan();
                System.out.println(Json.pretty(p.toJson()));
                break;
            }
            case "status":
                System.out.println(Json.pretty(status(cfg, state, directives)));
                break;
            case "feedback": {
                Directive d = new Directive();
                d.source = opts.getOrDefault("source", "human");
                d.text = opts.getOrDefault("text", "");
                d.runsLeft = Integer.parseInt(opts.getOrDefault("runs", String.valueOf(cfg.directiveDefaultRuns)));
                if (opts.containsKey("screenshot")) d.screenshotPath = opts.get("screenshot");
                if (opts.containsKey("archetype")) d.hints.put("archetype", opts.get("archetype"));
                if (opts.containsKey("series")) d.hints.put("series", opts.get("series"));
                directives.add(d);
                System.out.println("directive added: " + d.id);
                break;
            }
            case "serve":
                serve(cfg, state, directives);
                break;
            default:
                System.out.println("unknown command: " + cmd
                        + "\nusage: factory daemon|run-once|plan|status|feedback|serve [--config path] [--at ISO]");
        }
    }

    // ---------- wiring ----------

    private static LlmClient buildLlm(Config cfg) {
        if ("none".equalsIgnoreCase(cfg.llmProvider)) {
            Log.warn(TAG, "LLM disabled — deterministic template planner only");
            return null;
        }
        if ("ollama".equalsIgnoreCase(cfg.llmProvider)) {
            OllamaClient c = new OllamaClient(cfg.ollamaUrl, cfg.llmModel, cfg.llmTemperature);
            try {
                Http.get(cfg.ollamaUrl + "/api/tags", null, 3);
                Log.info(TAG, "LLM: " + c.name());
                return c;
            } catch (Exception e) {
                Log.warn(TAG, "Ollama unreachable at " + cfg.ollamaUrl + " — falling back to template planner");
                return null;
            }
        }
        Log.info(TAG, "LLM: openai-compatible " + cfg.openaiModel + " @ " + cfg.openaiBaseUrl);
        return new OpenAiCompatClient(cfg.openaiBaseUrl, cfg.openaiApiKey, cfg.openaiModel, cfg.llmTemperature);
    }

    private static ComfyUiGenerator buildComfy(Config cfg) {
        if ("fallback".equalsIgnoreCase(cfg.artMode)) return null;
        ComfyUiGenerator g = new ComfyUiGenerator(cfg);
        if (!g.reachable()) {
            Log.warn(TAG, "ComfyUI unreachable at " + cfg.comfyUrl + " — deterministic renderer only");
            if ("comfy".equalsIgnoreCase(cfg.artMode)) Log.err(TAG, "art mode is 'comfy' but server is down; will still fall back");
            return "auto".equalsIgnoreCase(cfg.artMode) ? null : g;
        }
        if (!cfg.comfyWorkflowPath.toFile().exists()) {
            Log.warn(TAG, "ComfyUI workflow missing (" + cfg.comfyWorkflowPath + ") — deterministic renderer only");
            return null;
        }
        Log.info(TAG, "art: ComfyUI Qwen-Image-Edit pass enabled");
        return g;
    }

    private static void daemon(Config cfg, StateStore state, DirectiveStore directives,
                               Planner planner, Publisher publisher, ComfyUiGenerator comfy) throws Exception {
        FeedbackServer server = new FeedbackServer(cfg, directives, () -> status(cfg, state, directives));
        if (cfg.feedbackEnabled) server.start();
        Log.info(TAG, "daemon up. slots " + cfg.slotTimes + " " + cfg.timezone
                + " | publish=" + cfg.publishMode + " | lead=" + cfg.leadMinutes + "min");
        long lastInboxScan = 0;
        while (true) {
            try {
                if (System.currentTimeMillis() - lastInboxScan > 60_000) {
                    int n = directives.scanInbox(cfg.feedbackScreenshots);
                    if (n > 0) Log.info(TAG, "inbox: " + n + " new directive(s) from folder");
                    lastInboxScan = System.currentTimeMillis();
                }
                ZonedDateTime now = ZonedDateTime.now(SlotClock.zone(cfg));
                String last = state.lastScheduledSlot();
                ZonedDateTime target = SlotClock.nextProductionSlot(cfg, last, now, cfg.leadMinutes);
                ZonedDateTime fireAt = target.minusMinutes(cfg.leadMinutes);
                if (!now.isBefore(fireAt)) {
                    Log.info(TAG, "producing slot " + target);
                    new Pipeline(cfg, state, planner, directives, publisher, comfy).run(target.toInstant());
                }
            } catch (Exception e) {
                Log.err(TAG, "daemon loop error: " + e);
            }
            Thread.sleep(cfg.pollSeconds * 1000L);
        }
    }

    private static void serve(Config cfg, StateStore state, DirectiveStore directives) throws Exception {
        FeedbackServer server = new FeedbackServer(cfg, directives, () -> status(cfg, state, directives));
        server.start();
        Thread.currentThread().join();
    }

    private static Map<String, Object> status(Config cfg, StateStore state, DirectiveStore directives) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("brand", cfg.brandName);
        m.put("publish_mode", cfg.publishMode);
        m.put("timezone", cfg.timezone);
        m.put("slots", cfg.slotTimes);
        m.put("now", Instant.now().toString());
        m.put("next_slot", SlotClock.nextSlot(cfg, ZonedDateTime.now(SlotClock.zone(cfg))).toString());
        m.put("last_scheduled_slot", state.lastScheduledSlot());
        m.put("recent_posts", state.recentPosts(5));
        List<Object> active = new java.util.ArrayList<>();
        for (Directive d : directives.active()) active.add(d.toJson());
        m.put("active_directives", active);
        return m;
    }

    private static Instant slotFor(Map<String, String> opts, Config cfg) {
        if (opts.containsKey("at")) return Instant.parse(opts.get("at"));
        if (opts.containsKey("now")) return Instant.now().plusSeconds(120);
        return SlotClock.nextSlot(cfg, ZonedDateTime.now(SlotClock.zone(cfg))).toInstant();
    }

    private static Map<String, String> parseOpts(String[] args) {
        Map<String, String> m = new LinkedHashMap<>();
        for (int i = 1; i < args.length; i++) {
            if (!args[i].startsWith("--")) continue;
            String key = args[i].substring(2);
            if (i + 1 < args.length && !args[i + 1].startsWith("--")) { m.put(key, args[i + 1]); i++; }
            else m.put(key, "true");
        }
        return m;
    }
}
