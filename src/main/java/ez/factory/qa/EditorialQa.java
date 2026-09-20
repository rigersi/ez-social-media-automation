package ez.factory.qa;

import ez.factory.config.Config;
import ez.factory.plan.PostPlan;
import ez.factory.plan.Validator;
import ez.factory.video.VideoWrap;
import ez.factory.util.Log;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Pre-publish gate. Checks the plan against the editorial hard rules AND the artifacts
 * against the technical spec (1080x1920, silent 8-12s H.264 mp4). Anything that fails is
 * quarantined into out/quarantine/ instead of shipping — the slot is retried on next run.
 *
 * This is the in-factory half of QA. The standalone Quality-Assurance module (phase 2)
 * plugs in through the feedback API and the post log; see docs/QA_AND_AFFILIATE.md.
 */
public final class EditorialQa {

    private static final String TAG = "qa";

    public static List<String> check(Config cfg, PostPlan plan, File stampedPng, File mp4) {
        List<String> problems = new ArrayList<>(Validator.check(plan, cfg.hashtagMin, cfg.hashtagMax));
        try {
            BufferedImage img = ImageIO.read(stampedPng);
            if (img == null) problems.add("stamped artwork unreadable");
            else if (img.getWidth() != cfg.canvasW || img.getHeight() != cfg.canvasH)
                problems.add("artwork is " + img.getWidth() + "x" + img.getHeight() + ", expected " + cfg.canvasW + "x" + cfg.canvasH);
        } catch (IOException e) {
            problems.add("cannot read artwork: " + e.getMessage());
        }
        if (cfg.videoEnabled) {
            if (mp4 == null || !mp4.exists() || mp4.length() < 50_000) problems.add("mp4 missing or suspiciously small");
            else problems.addAll(VideoWrap.probe(cfg, mp4));
        }
        if (!problems.isEmpty()) Log.warn(TAG, plan.id + " failed QA: " + String.join("; ", problems));
        else Log.info(TAG, plan.id + " passed QA");
        return problems;
    }
}
