package ez.factory;

import ez.factory.art.ComfyUiGenerator;
import ez.factory.art.FallbackRenderer;
import ez.factory.art.HeroMask;
import ez.factory.art.ArtGenerator;
import ez.factory.brand.BrandStamp;
import ez.factory.config.Config;
import ez.factory.feedback.DirectiveStore;
import ez.factory.plan.Planner;
import ez.factory.plan.PostPlan;
import ez.factory.publish.Publisher;
import ez.factory.qa.EditorialQa;
import ez.factory.state.StateStore;
import ez.factory.util.IO;
import ez.factory.util.Json;
import ez.factory.util.Log;
import ez.factory.video.VideoWrap;

import java.io.File;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One production run: PLAN -> RENDER (Qwen-Image-Edit over deterministic base) -> BRAND STAMP
 * -> STATIC VIDEO -> EDITORIAL QA -> PUBLISH (Postiz or dry-run outbox) -> RECORD.
 * Failures quarantine into out/quarantine/ and the slot is retried on the next run;
 * a slot is never filled with rule-breaking material.
 */
public final class Pipeline {

    private static final String TAG = "pipeline";

    private final Config cfg;
    private final StateStore state;
    private final Planner planner;
    private final DirectiveStore directives;
    private final Publisher publisher;
    private final FallbackRenderer renderer;
    private final ArtGenerator painter; // null when disabled (Gemini, ComfyUI or none)

    public Pipeline(Config cfg, StateStore state, Planner planner, DirectiveStore directives,
                    Publisher publisher, ArtGenerator painter) {
        this.cfg = cfg;
        this.state = state;
        this.planner = planner;
        this.directives = directives;
        this.publisher = publisher;
        this.renderer = new FallbackRenderer(cfg);
        this.painter = painter;
    }

    public Map<String, Object> run(Instant slotTime) {
        Map<String, Object> record = new LinkedHashMap<>();
        record.put("slot", slotTime.toString());
        File artDir = cfg.outDir.resolve("art").toFile();
        File vidDir = cfg.outDir.resolve("video").toFile();
        artDir.mkdirs();
        vidDir.mkdirs();

        PostPlan goodPlan = null;
        File goodArt = null;
        File goodMp4 = null;

        for (int attempt = 1; attempt <= 2 && goodPlan == null; attempt++) {
            try {
                PostPlan plan = planner.plan();
                File base = new File(artDir, plan.id + "-layout.png");
                renderer.generate(plan, base, null);
                File mask = new File(artDir, plan.id + "-mask.png");
                HeroMask.make(cfg, mask);

                File art = base;
                if (painter != null) {
                    try {
                        art = painter.generate(plan, base, mask);
                    } catch (Exception e) {
                        Log.warn(TAG, "paint pass failed (" + e.getMessage() + ") — using deterministic base layout");
                        art = base;
                    }
                }
                File finalArt = new File(artDir, plan.id + ".png");
                if (!art.getAbsolutePath().equals(finalArt.getAbsolutePath())) {
                    java.nio.file.Files.copy(art.toPath(), finalArt.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
                BrandStamp.stamp(cfg, finalArt);
                art = finalArt;

                File mp4 = null;
                if (cfg.videoEnabled) {
                    mp4 = new File(vidDir, plan.id + ".mp4");
                    VideoWrap.wrap(cfg, art, mp4);
                }

                List<String> problems = EditorialQa.check(cfg, plan, art, mp4);
                if (problems.isEmpty()) {
                    goodPlan = plan;
                    goodArt = art;
                    goodMp4 = mp4;
                } else {
                    quarantine(plan.id, attempt, art, mp4, problems);
                }
            } catch (Exception e) {
                Log.err(TAG, "attempt " + attempt + " crashed: " + e);
            }
        }

        if (goodPlan == null) {
            record.put("status", "quarantined");
            record.put("reason", "both attempts failed editorial QA");
            Log.err(TAG, "slot " + slotTime + " NOT filled — everything quarantined (see out/quarantine)");
            return record;
        }

        try {
            IO.write(cfg.outDir.resolve("plans").resolve(goodPlan.id + ".json"), Json.pretty(goodPlan.toJson()));
            Map<String, Object> pubRes = publisher.publish(goodPlan, goodMp4, slotTime);
            record.put("status", cfg.livePublish() ? "scheduled" : "staged(dry-run)");
            record.put("publish", pubRes);
        } catch (Exception e) {
            record.put("status", "publish-failed");
            record.put("error", String.valueOf(e.getMessage()));
            Log.err(TAG, "publish failed: " + e);
            return record;
        }

        record.put("plan_id", goodPlan.id);
        record.put("archetype", goodPlan.archetype.id);
        record.put("headline", goodPlan.headline);
        record.put("artwork", goodArt.getAbsolutePath());
        if (goodMp4 != null) record.put("video", goodMp4.getAbsolutePath());

        Map<String, Object> log = new LinkedHashMap<>();
        log.put("id", goodPlan.id);
        log.put("slot", slotTime.toString());
        log.put("archetype", goodPlan.archetype.id);
        log.put("headline", goodPlan.headline);
        log.put("series", goodPlan.seriesLabel());
        log.put("caption", goodPlan.caption);
        log.put("hashtags", goodPlan.hashtags);
        log.put("video", goodMp4 == null ? "" : goodMp4.getAbsolutePath());
        log.put("status", record.get("status"));
        state.recordPost(log);
        state.setLastScheduledSlot(slotTime.toString());
        try {
            directives.tick();
            state.save();
        } catch (Exception e) {
            Log.warn(TAG, "state save failed: " + e.getMessage());
        }
        Log.info(TAG, "slot " + slotTime + " filled with " + goodPlan.id + " [" + goodPlan.archetype.id + "]");
        return record;
    }

    private void quarantine(String id, int attempt, File art, File mp4, List<String> problems) {
        try {
            File q = cfg.outDir.resolve("quarantine").resolve(id + "-a" + attempt).toFile();
            q.mkdirs();
            if (art != null && art.exists()) IO.move(art.toPath(), q.toPath().resolve(art.getName()));
            if (mp4 != null && mp4.exists()) IO.move(mp4.toPath(), q.toPath().resolve(mp4.getName()));
            IO.write(q.toPath().resolve("problems.json"), Json.pretty(problems));
            Log.warn(TAG, id + " attempt " + attempt + " quarantined: " + String.join("; ", problems));
        } catch (Exception e) {
            Log.err(TAG, "quarantine failed: " + e);
        }
    }
}
