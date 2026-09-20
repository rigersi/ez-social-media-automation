package ez.factory.publish;

import ez.factory.config.Config;
import ez.factory.plan.PostPlan;
import ez.factory.util.Http;
import ez.factory.util.Json;
import ez.factory.util.Log;

import java.io.File;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Postiz bridge (6 connected channels: X, YouTube Shorts, TikTok, Instagram Reels,
 * Facebook, Threads). One upload, one scheduled post group, caption + hashtags ride as
 * metadata next to the SAME mp4 on every channel (brand spec §6).
 *
 * Auth: header "Authorization: {apiKey}" (Postiz public API).
 */
public final class PostizPublisher implements Publisher {

    private static final String TAG = "postiz";
    private final Config cfg;

    public PostizPublisher(Config cfg) { this.cfg = cfg; }

    private Map<String, String> headers() {
        Map<String, String> h = new LinkedHashMap<>();
        h.put("Authorization", cfg.postizApiKey);
        return h;
    }

    public List<Map<String, Object>> integrations() throws Exception {
        String res = Http.get(cfg.postizBaseUrl + "/integrations", headers(), 30);
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object o : Json.arr(Json.parse(res))) {
            Map<String, Object> m = Json.obj(o);
            String identifier = Json.str(m, "identifier", "").toLowerCase();
            if (cfg.postizChannels.isEmpty() || matches(identifier)) out.add(m);
        }
        return out;
    }

    private boolean matches(String identifier) {
        for (String want : cfg.postizChannels) {
            String w = want.toLowerCase();
            if (identifier.equals(w)) return true;
            if (w.equals("x") && (identifier.equals("twitter") || identifier.equals("x"))) return true;
            if (identifier.contains(w)) return true;
        }
        return false;
    }

    @Override
    public Map<String, Object> publish(PostPlan plan, File mp4, Instant slotTime) throws Exception {
        if (cfg.postizApiKey.isEmpty()) throw new IllegalStateException("postiz.apiKey is empty — set it in config.json");
        List<Map<String, Object>> ints = integrations();
        if (ints.isEmpty()) throw new IllegalStateException("No Postiz integrations matched config channels " + cfg.postizChannels);

        // 1. upload the static video once
        String up = Http.postFile(cfg.postizBaseUrl + "/upload", mp4, "file", headers(), 600);
        Map<String, Object> media = Json.parseObject(up);
        Map<String, Object> mediaRef = new LinkedHashMap<>();
        mediaRef.put("id", Json.str(media, "id", ""));
        mediaRef.put("path", Json.str(media, "path", ""));
        Log.info(TAG, "uploaded " + mp4.getName() + " -> " + mediaRef.get("path"));

        // 2. one post per channel, same group, same media, caption + hashtags as metadata
        String content = plan.caption + "\n\n" + String.join(" ", plan.hashtags);
        List<Object> posts = new ArrayList<>();
        for (Map<String, Object> in : ints) {
            String identifier = Json.str(in, "identifier", "").toLowerCase();
            Map<String, Object> valueItem = new LinkedHashMap<>();
            valueItem.put("content", content);
            List<Object> mediaList = new ArrayList<>();
            mediaList.add(mediaRef);
            valueItem.put("image", mediaList); // Postiz media array (images AND videos)

            Map<String, Object> post = new LinkedHashMap<>();
            Map<String, Object> integ = new LinkedHashMap<>();
            integ.put("id", Json.str(in, "id", ""));
            post.put("integration", integ);
            List<Object> value = new ArrayList<>();
            value.add(valueItem);
            post.put("value", value);
            post.put("settings", settingsFor(identifier, plan));
            posts.add(post);
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", "schedule");
        body.put("date", slotTime.toString());
        body.put("posts", posts);

        String res = Http.postJson(cfg.postizBaseUrl + "/posts", Json.stringify(body), headers(), 120);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("postiz_response", Json.parse(res));
        List<Object> ids = new ArrayList<>();
        for (Map<String, Object> in : ints) ids.add(Json.str(in, "id", "") + ":" + Json.str(in, "name", identifier(in)));
        out.put("channels", ids);
        out.put("scheduled_for", slotTime.toString());
        Log.info(TAG, "scheduled " + plan.id + " on " + ints.size() + " channels at " + slotTime);
        return out;
    }

    private String identifier(Map<String, Object> in) { return Json.str(in, "identifier", ""); }

    /** Per-platform DTOs (Postiz validates these; wrong keys surface as API errors). */
    private Map<String, Object> settingsFor(String identifier, PostPlan plan) {
        Map<String, Object> s = new LinkedHashMap<>();
        if (identifier.contains("youtube")) {
            s.put("title", trim(plan.headline.isEmpty() ? plan.hook : plan.headline, 100));
            s.put("type", "public");
            List<Object> tags = new ArrayList<>();
            for (String t : plan.ytTags) {
                Map<String, Object> tag = new LinkedHashMap<>();
                tag.put("value", t);
                tag.put("label", t);
                tags.add(tag);
            }
            s.put("tags", tags);
            if (!cfg.youtubePlaylistId.isEmpty()) s.put("playlistId", cfg.youtubePlaylistId);
        } else if (identifier.contains("tiktok")) {
            s.put("privacy_level", "PUBLIC_TO_EVERYONE");
            s.put("duet", Boolean.TRUE);
            s.put("stitch", Boolean.TRUE);
        } else if (identifier.contains("instagram")) {
            s.put("post_type", "reel");
        } else if (identifier.contains("facebook")) {
            s.put("post_type", "reel");
        }
        return s;
    }

    private static String trim(String s, int n) { return s.length() <= n ? s : s.substring(0, n - 1) + "\u2026"; }
}
