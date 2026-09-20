package ez.factory.feedback;

import ez.factory.util.Json;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One feedback instruction. Sources: "human" (owner via text/screenshot) or "qa" (the future
 * Quality-Assurance module posting through the same API). Directives steer the planner for
 * `runsLeft` runs, then retire automatically.
 *
 * Optional structured hints (QA-friendly): archetype, series, ban_topics[], hashtag_add[].
 */
public final class Directive {

    public String id;
    public String source = "human";
    public String text = "";
    public String screenshotPath = "";
    public String visionNote = "";
    public int runsLeft = 10;
    public String createdAt = Instant.now().toString();
    public boolean active = true;
    public Map<String, Object> hints = new LinkedHashMap<>();

    public Map<String, Object> toJson() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("source", source);
        m.put("text", text);
        m.put("screenshot", screenshotPath);
        m.put("vision_note", visionNote);
        m.put("runs_left", runsLeft);
        m.put("created_at", createdAt);
        m.put("active", active);
        m.put("hints", new LinkedHashMap<>(hints));
        return m;
    }

    public static Directive fromJson(Map<String, Object> m) {
        Directive d = new Directive();
        d.id = Json.str(m, "id", "");
        d.source = Json.str(m, "source", "human");
        d.text = Json.str(m, "text", "");
        d.screenshotPath = Json.str(m, "screenshot", "");
        d.visionNote = Json.str(m, "vision_note", "");
        d.runsLeft = Json.int_(m, "runs_left", 10);
        d.createdAt = Json.str(m, "created_at", Instant.now().toString());
        d.active = Json.bool(m, "active", true);
        d.hints = Json.sub(m, "hints");
        return d;
    }

    public String archetypeHint() { return Json.str(hints, "archetype", ""); }
    public String seriesHint() { return Json.str(hints, "series", ""); }
    public List<String> bannedTopics() { return Json.strList(hints, "ban_topics"); }
    public List<String> hashtagAdd() { return Json.strList(hints, "hashtag_add"); }
}
