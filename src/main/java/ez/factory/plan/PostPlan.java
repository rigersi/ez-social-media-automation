package ez.factory.plan;

import ez.factory.util.Json;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Everything one post needs. On-image fields (burned into artwork) vs off-image fields
 * (caption / hashtags / yt tags delivered as metadata to the scheduler) are kept separate
 * by construction — see brand spec section 3.
 */
public final class PostPlan {

    public String id;
    public Archetype archetype;
    public String topicId = "";
    public String seriesName = "";
    public int seriesNumber = 0;

    // on-image
    public String hook = "";
    public String headline = "";
    public String accent = "";
    public List<String> callouts = new ArrayList<>();
    public List<List<String>> table = new ArrayList<>();   // comparison rows
    public String verdictA = "";                            // "best for X"
    public String verdictB = "";                            // "best for Y"
    public String quote = "";
    public String quoteSource = "";
    public boolean conceptVisual = false;
    public String seriesTag = "";                           // e.g. "SIDEMEN SETUP SERIES #8"
    public String cta = "";

    // off-image (metadata only)
    public String caption = "";
    public List<String> hashtags = new ArrayList<>();
    public List<String> ytTags = new ArrayList<>();

    // provenance / art
    public List<String> sources = new ArrayList<>();
    public String artPrompt = "";
    public String product = "";   // real product name from the topic card (designer-mode hero)
    public String visual = "";    // scene direction from the topic card
    public String assetPath = "";   // optional real product cutout (transparent PNG) composited on the hero stage
    public String createdAt = Instant.now().toString();

    public String seriesLabel() {
        return seriesName.isEmpty() ? "" : seriesName + " #" + seriesNumber;
    }

    public Map<String, Object> toJson() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("archetype", archetype == null ? "" : archetype.id);
        m.put("topic_id", topicId);
        m.put("series_name", seriesName);
        m.put("series_number", seriesNumber);
        m.put("series_tag", seriesTag);
        m.put("hook", hook);
        m.put("headline", headline);
        m.put("accent", accent);
        m.put("callouts", new ArrayList<Object>(callouts));
        List<Object> rows = new ArrayList<>();
        for (List<String> r : table) rows.add(new ArrayList<Object>(r));
        m.put("table", rows);
        m.put("verdict_a", verdictA);
        m.put("verdict_b", verdictB);
        m.put("quote", quote);
        m.put("quote_source", quoteSource);
        m.put("concept_visual", conceptVisual);
        m.put("cta", cta);
        m.put("caption", caption);
        m.put("hashtags", new ArrayList<Object>(hashtags));
        m.put("yt_tags", new ArrayList<Object>(ytTags));
        m.put("sources", new ArrayList<Object>(sources));
        m.put("art_prompt", artPrompt);
        m.put("product", product);
        m.put("visual", visual);
        m.put("asset_path", assetPath);
        m.put("created_at", createdAt);
        return m;
    }

    public static PostPlan fromJson(Map<String, Object> m) {
        PostPlan p = new PostPlan();
        p.id = Json.str(m, "id", "");
        p.archetype = Archetype.fromId(Json.str(m, "archetype", ""));
        p.topicId = Json.str(m, "topic_id", "");
        p.seriesName = Json.str(m, "series_name", "");
        p.seriesNumber = Json.int_(m, "series_number", 0);
        p.seriesTag = Json.str(m, "series_tag", "");
        p.hook = Json.str(m, "hook", "");
        p.headline = Json.str(m, "headline", "");
        p.accent = Json.str(m, "accent", "");
        p.callouts = Json.strList(m, "callouts");
        for (Object r : Json.list(m, "table")) {
            List<String> row = new ArrayList<>();
            for (Object c : Json.arr(r)) row.add(String.valueOf(c));
            p.table.add(row);
        }
        p.verdictA = Json.str(m, "verdict_a", "");
        p.verdictB = Json.str(m, "verdict_b", "");
        p.quote = Json.str(m, "quote", "");
        p.quoteSource = Json.str(m, "quote_source", "");
        p.conceptVisual = Json.bool(m, "concept_visual", false);
        p.cta = Json.str(m, "cta", "");
        p.caption = Json.str(m, "caption", "");
        p.hashtags = Json.strList(m, "hashtags");
        p.ytTags = Json.strList(m, "yt_tags");
        p.sources = Json.strList(m, "sources");
        p.artPrompt = Json.str(m, "art_prompt", "");
        p.product = Json.str(m, "product", "");
        p.visual = Json.str(m, "visual", "");
        p.assetPath = Json.str(m, "asset_path", "");
        p.createdAt = Json.str(m, "created_at", Instant.now().toString());
        return p;
    }
}