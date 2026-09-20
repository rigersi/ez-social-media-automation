package ez.factory.plan;

import ez.factory.util.IO;
import ez.factory.util.Json;
import ez.factory.util.Log;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Hashtag source of truth (trends/hashtag-pool.json): a base niche pool, per-archetype pools
 * and a "trending" list. The owner OR the QA module can edit/refresh this file at any time —
 * it is re-read on every plan, so trend updates apply from the next post without a restart.
 * Captions/hashtags are NEVER burned into artwork; they ride as metadata (brand spec §3).
 */
public final class TrendPool {

    private List<String> base = new ArrayList<>();
    private List<String> trending = new ArrayList<>();
    private Map<String, Object> perArchetype = new java.util.LinkedHashMap<>();

    public TrendPool(Path file) {
        reload(file);
    }

    public final void reload(Path file) {
        try {
            if (file.toFile().exists()) {
                Map<String, Object> m = Json.parseObject(IO.read(file));
                base = norm(Json.strList(m, "base"));
                trending = norm(Json.strList(m, "trending"));
                perArchetype = Json.sub(m, "per_archetype");
            }
        } catch (Exception e) {
            Log.warn("trends", "cannot read hashtag pool: " + e.getMessage());
        }
        if (base.isEmpty()) {
            base = norm(java.util.Arrays.asList(
                    "#tech", "#gadgets", "#techtok", "#gadgetlover", "#innovation", "#futuretech",
                    "#smartgadgets", "#techfinds", "#ezgoodfinds", "#viralgadgets", "#cooltech", "#techreels"));
        }
    }

    /** trending first, then archetype-specific, then base fill — within min..max bounds. */
    public List<String> pickFor(Archetype a, Random rnd, int min, int max, List<String> extra) {
        Set<String> out = new LinkedHashSet<>();
        shuffleInto(out, extra, rnd);
        shuffleInto(out, trending, rnd);
        shuffleInto(out, Json.strList(perArchetype, a == null ? "" : a.id), rnd);
        shuffleInto(out, base, rnd);
        List<String> list = new ArrayList<>(out);
        int target = Math.min(max, Math.max(min, Math.min(list.size(), min + 4)));
        return new ArrayList<>(list.subList(0, Math.min(target, list.size())));
    }

    public List<String> ytTagsFor(Map<String, Object> topic, Archetype a) {
        Set<String> tags = new LinkedHashSet<>();
        tags.addAll(Json.strList(topic, "tags"));
        String product = Json.str(topic, "product", "");
        for (String w : product.split("[^A-Za-z0-9]+")) if (w.length() > 2) tags.add(w.toLowerCase());
        tags.add("ez good finds");
        tags.add("tech gadgets");
        tags.add("cool gadgets");
        tags.add(a == null ? "tech" : a.id.replace('_', ' '));
        tags.add("gadgets you didn't know existed");
        tags.add("tech shorts");
        return new ArrayList<>(tags);
    }

    private static void shuffleInto(Set<String> out, List<String> src, Random rnd) {
        List<String> copy = new ArrayList<>(src);
        java.util.Collections.shuffle(copy, rnd);
        out.addAll(copy);
    }

    private static List<String> norm(List<String> in) {
        List<String> out = new ArrayList<>();
        for (String s : in) {
            String t = s.trim();
            if (t.isEmpty()) continue;
            if (!t.startsWith("#")) t = "#" + t;
            out.add(t);
        }
        return out;
    }
}
