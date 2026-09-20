package ez.factory.publish;

import ez.factory.plan.PostPlan;

import java.io.File;
import java.time.Instant;
import java.util.Map;

public interface Publisher {
    /** Ship (or stage) one post: the static video + caption + hashtags + per-platform settings. */
    Map<String, Object> publish(PostPlan plan, File mp4, Instant slotTime) throws Exception;
}
