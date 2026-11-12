package ez.factory.plan;

import ez.factory.config.Config;
import ez.factory.feedback.Directive;
import ez.factory.feedback.DirectiveStore;
import ez.factory.llm.LlmClient;
import ez.factory.state.StateStore;
import ez.factory.util.IO;
import ez.factory.util.Json;
import ez.factory.util.Log;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * CONTENT FACTORY — planning stage.
 *
 * Decides WHAT the next post is (archetype rotation, series continuity, topic card), then
 * writes the full post package: on-image copy, art prompt, off-image caption + hashtags +
 * YouTube tags. Uses the local free LLM when available; validates against the editorial
 * hard rules; retries once; falls back to the deterministic TemplatePlanner so a slot is
 * never missed. Active feedback directives (owner or QA module) steer every decision.
 */
public final class Planner {

    private static final String TAG = "planner";

    private final Config cfg;
    private final StateStore state;
    private final TopicPool topics;
    private final TrendPool trends;
    private final DirectiveStore directives;
    private final LlmClient llm; // may be null -> templates only
    private final Random rnd = new Random();
    private final List<Map<String, Object>> seriesDefs;

    public Planner(Config cfg, StateStore state, TopicPool topics, TrendPool trends,
                   DirectiveStore directives, LlmClient llm) throws IOException {
        this.cfg = cfg;
        this.state = state;
        this.topics = topics;
        this.trends = trends;
        this.directives = directives;
        this.llm = llm;
        this.seriesDefs = loadSeriesDefs(cfg.topicsDir.resolve("series.json"));
    }

    private List<Map<String, Object>> loadSeriesDefs(Path p) throws IOException {
        List<Map<String, Object>> out = new ArrayList<>();
        if (p.toFile().exists()) {
            for (Object o : Json.list(Json.parseObject(IO.read(p)), "series")) out.add(Json.obj(o));
        }
        if (out.isEmpty()) {
            Map<String, Object> d = new java.util.LinkedHashMap<>();
            d.put("name", "Doomsday Tech File");
            d.put("current", 2);
            out.add(d);
        }
        return out;
    }

    public PostPlan plan() throws IOException {
        trends.reload(cfg.hashtagPoolPath); // QA/owner can refresh trends any time
        List<Directive> active = directives.active();

        Archetype a = chooseArchetype(active);
        String seriesName = "";
        int seriesNumber = 0;
        if (a == Archetype.SERIES) {
            seriesName = pickSeries(active);
            seriesNumber = state.nextSeriesNumber(seriesName);
        }
        Map<String, Object> topic = pickTopic(a, active);
        List<String> extraTags = new ArrayList<>();
        for (Directive d : active) extraTags.addAll(d.hashtagAdd());
        List<String> hashtags = trends.pickFor(a, rnd, cfg.hashtagMin, cfg.hashtagMax, extraTags);
        List<String> ytTags = trends.ytTagsFor(topic == null ? new java.util.LinkedHashMap<>() : topic, a);

        PostPlan plan = null;
        if (llm != null && topic != null) {
            plan = llmPlan(a, topic, seriesName, seriesNumber, hashtags, ytTags, active);
        }
        if (plan == null) {
            Log.info(TAG, "using deterministic template planner" + (llm == null ? " (no LLM configured)" : " (LLM plan failed validation)"));
            plan = TemplatePlanner.build(a, topic == null ? emptyTopic(a) : topic, seriesName, seriesNumber, hashtags, ytTags);
        }

        plan.id = "p-" + UUID.randomUUID().toString().substring(0, 8);
        plan.hashtags = enforcePool(plan.hashtags, hashtags);
        if (plan.assetPath == null || plan.assetPath.isEmpty()) {
            plan.assetPath = topic == null ? "" : Json.str(topic, "asset", "");
        }
        if (plan.product == null || plan.product.isEmpty()) {
            plan.product = topic == null ? "the gadget" : Json.str(topic, "product", "the gadget");
        }
        if (plan.visual == null || plan.visual.isEmpty()) {
            plan.visual = topic == null ? "" : Json.str(topic, "visual", "");
        }
        if (plan.artPrompt == null || plan.artPrompt.isEmpty()) {
            plan.artPrompt = ArtPromptBuilder.build(plan, plan.product, plan.visual);
        }
        state.pushArchetype(a.id);
        if (topic != null) state.markTopicUsed(Json.str(topic, "id", ""));
        Log.info(TAG, "planned " + plan.id + " [" + a.id + "] " + plan.headline);
        return plan;
    }

    // ---------- decisions ----------

    private Archetype chooseArchetype(List<Directive> active) {
        for (Directive d : active) {
            Archetype forced = Archetype.fromId(d.archetypeHint());
            if (forced != null) return forced;
        }
        List<String> recent = state.recentArchetypes();
        List<Archetype> candidates = new ArrayList<>();
        for (Archetype a : Archetype.values()) {
            boolean last = !recent.isEmpty() && recent.get(recent.size() - 1).equals(a.id);
            int count = 0;
            for (String r : recent) if (r.equals(a.id)) count++;
            if (!last && count < 3) candidates.add(a);
        }
        if (candidates.isEmpty()) candidates = new ArrayList<>(java.util.Arrays.asList(Archetype.values()));
        return candidates.get(rnd.nextInt(candidates.size()));
    }

    private String pickSeries(List<Directive> active) {
        for (Directive d : active) if (!d.seriesHint().isEmpty()) return d.seriesHint();
        String best = null;
        int bestN = Integer.MAX_VALUE;
        for (Map<String, Object> s : seriesDefs) {
            String name = Json.str(s, "name", "");
            int n = state.peekSeriesNumber(name);
            if (n < bestN) { bestN = n; best = name; }
        }
        return best == null ? "Doomsday Tech File" : best;
    }

    private Map<String, Object> pickTopic(Archetype a, List<Directive> active) {
        List<String> banned = new ArrayList<>();
        for (Directive d : active) banned.addAll(d.bannedTopics());
        Map<String, Object> t = topics.pick(a, state, rnd);
        int guard = 0;
        while (t != null && banned.contains(Json.str(t, "id", "")) && guard++ < 10) {
            t = topics.pick(a, state, rnd);
        }
        return t;
    }

    private List<String> enforcePool(List<String> chosen, List<String> pool) {
        List<String> out = new ArrayList<>();
        for (String h : chosen) if (pool.contains(h) && !out.contains(h)) out.add(h);
        for (String h : pool) {
            if (out.size() >= cfg.hashtagMin) break;
            if (!out.contains(h)) out.add(h);
        }
        if (out.size() > cfg.hashtagMax) out = out.subList(0, cfg.hashtagMax);
        return new ArrayList<>(out);
    }

    private Map<String, Object> emptyTopic(Archetype a) {
        Map<String, Object> t = new java.util.LinkedHashMap<>();
        t.put("id", "generic");
        t.put("product", "this gadget");
        t.put("archetype", a.id);
        return t;
    }

    // ---------- LLM path ----------

    private PostPlan llmPlan(Archetype a, Map<String, Object> topic, String seriesName, int seriesNumber,
                             List<String> hashtags, List<String> ytTags, List<Directive> active) {
        String system = systemPrompt();
        String user = userPrompt(a, topic, seriesName, seriesNumber, hashtags, ytTags, active);
        for (int attempt = 1; attempt <= 2; attempt++) {
            try {
                String raw = llm.chat(system, attempt == 1 ? user : lastError + "\n\n" + user);
                PostPlan p = parseLlm(raw, a, seriesName, seriesNumber);
                List<String> problems = Validator.check(p, cfg.hashtagMin, cfg.hashtagMax);
                if (problems.isEmpty()) return p;
                lastError = "Your previous JSON failed editorial QA: " + String.join("; ", problems)
                        + ". Fix every problem and return the corrected JSON only.";
                Log.warn(TAG, "attempt " + attempt + " failed QA: " + String.join("; ", problems));
            } catch (Exception e) {
                lastError = "Previous output was unusable (" + e.getMessage() + "). Return strict JSON only.";
                Log.warn(TAG, "LLM attempt " + attempt + " error: " + e.getMessage());
            }
        }
        return null;
    }

    private String lastError = "";

    private String systemPrompt() {
        return "You are the editorial engine of '" + cfg.brandName + "', a viral tech-gadget channel.\n"
             + "EVERY post must have: a hook headline, a story/joke/argument or series continuity. A clean product shot on a background is REJECTED.\n"
             + "Archetypes (rotate, never a plain ad):\n"
             + " - celebrity_tech: " + Archetype.CELEBRITY_TECH.description + "\n"
             + " - weird_gadget: " + Archetype.WEIRD_GADGET.description + "\n"
             + " - comparison: " + Archetype.COMPARISON.description + "\n"
             + " - series: " + Archetype.SERIES.description + "\n"
             + " - throw_away: " + Archetype.THROW_AWAY.description + "\n"
             + " - creator_verified: " + Archetype.CREATOR_VERIFIED.description + "\n"
             + "On-image text (burned into artwork): hook, headline, accent phrase, callouts, table, series tag, CONCEPT VISUAL tag, CTA bar.\n"
             + "Off-image text (metadata only, never drawn): caption, hashtags, yt_tags.\n"
             + "Style: punchy, funny on purpose, confident. No spammy ALL-CAPS walls, no emoji spam (max 2 emoji in caption).\n"
             + "Return ONLY one strict JSON object with keys: archetype, hook, headline, accent, callouts[3..8 strings <=60 chars], "
             + "table (only for comparison: rows of [label, productA, productB]), verdict_a, verdict_b, quote, quote_source, sources[], "
             + "concept_visual (true for fictional celebrity pairings), series_tag, cta, caption (120..2200 chars, multi-paragraph, ends with a question or CTA), "
             + "hashtags (ONLY from the provided list), yt_tags (5..30 plain phrases), "
             + "art_prompt (MAX 60 words, imperative, in this order: 1) 'Keep every existing text block, card and table exactly where it is, crisp and word-for-word unchanged.' "
             + "2) one sentence naming the hero visual to paint into the empty middle stage. "
             + "3) 'Keep the top-left logo zone empty and the right-side and bottom safe zones calm.' "
             + "NEVER mention the brand name inside art_prompt — the image model paints it as a hallucinated wordmark).";
    }

    private String userPrompt(Archetype a, Map<String, Object> topic, String seriesName, int seriesNumber,
                              List<String> hashtags, List<String> ytTags, List<Directive> active) {
        StringBuilder b = new StringBuilder();
        b.append("Write the next post.\nARCHETYPE: ").append(a.id).append(" — ").append(a.description).append('\n');
        if (a == Archetype.SERIES) b.append("SERIES: ").append(seriesName).append(" #").append(seriesNumber)
                .append(" (carry the number on-image and ask viewers to recommend the next episode in the caption).\n");
        b.append("TOPIC CARD (facts are verified — use them, do not invent specs):\n").append(Json.pretty(topic)).append('\n');
        b.append("ALLOWED HASHTAGS (choose ").append(cfg.hashtagMin).append("-").append(cfg.hashtagMax).append("): ")
                .append(String.join(" ", hashtags)).append('\n');
        b.append("YT TAG POOL (choose 8-20): ").append(String.join(" | ", ytTags)).append('\n');
        if (!active.isEmpty()) {
            b.append("ACTIVE FEEDBACK DIRECTIVES (obey these):\n");
            for (Directive d : active) {
                b.append(" - [").append(d.source).append("] ").append(d.text == null ? "" : d.text).append('\n');
                if (!d.visionNote.isEmpty()) b.append("   screenshot note: ").append(d.visionNote).append('\n');
            }
        }
        List<Object> recent = state.recentPosts(3);
        if (!recent.isEmpty()) {
            b.append("RECENT POSTS (do not repeat hooks/headlines):\n");
            for (Object o : recent) b.append(" - ").append(Json.str(Json.obj(o), "headline", "")).append('\n');
        }
        b.append("Return the JSON object now.");
        return b.toString();
    }

    private PostPlan parseLlm(String raw, Archetype a, String seriesName, int seriesNumber) {
        String cleaned = raw.trim();
        int brace = cleaned.indexOf('{');
        int lastBrace = cleaned.lastIndexOf('}');
        if (brace >= 0 && lastBrace > brace) cleaned = cleaned.substring(brace, lastBrace + 1);
        Map<String, Object> m = Json.parseObject(cleaned);
        PostPlan p = PostPlan.fromJson(m);
        if (p.archetype == null) p.archetype = a;
        if (p.archetype == Archetype.SERIES) {
            p.seriesName = seriesName;
            p.seriesNumber = seriesNumber;
            if (p.seriesTag.isEmpty()) p.seriesTag = (seriesName + " #" + seriesNumber).toUpperCase();
        }
        return p;
    }
}