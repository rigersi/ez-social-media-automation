package ez.factory.publish;

import ez.factory.config.Config;
import ez.factory.plan.PostPlan;
import ez.factory.util.IO;
import ez.factory.util.Json;
import ez.factory.util.Log;

import java.io.File;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Default publish mode. Writes exactly what WOULD go to Postiz into out/publish/<id>.json
 * (payload + media paths + slot) so the owner can eyeball a week of output before flipping
 * publish.mode to "live". Nothing touches the real channels.
 */
public final class DryRunPublisher implements Publisher {

    private static final String TAG = "dry-run";
    private final Config cfg;

    public DryRunPublisher(Config cfg) { this.cfg = cfg; }

    @Override
    public Map<String, Object> publish(PostPlan plan, File mp4, Instant slotTime) throws Exception {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("plan", plan.toJson());
        payload.put("media", mp4.getAbsolutePath());
        payload.put("scheduled_for", slotTime.toString());
        payload.put("caption_with_hashtags", plan.caption + "\n\n" + String.join(" ", plan.hashtags));
        payload.put("would_post_to", cfg.postizChannels.isEmpty()
                ? java.util.Collections.singletonList("all connected Postiz channels")
                : cfg.postizChannels);
        File out = cfg.outbox.resolve(plan.id + ".json").toFile();
        IO.write(out.toPath(), Json.pretty(payload));
        Log.info(TAG, "staged " + plan.id + " -> " + out.getPath());
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("mode", "dry-run");
        res.put("payload_file", out.getPath());
        res.put("scheduled_for", slotTime.toString());
        return res;
    }
}
