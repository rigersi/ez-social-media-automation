package ez.factory.art;

import ez.factory.plan.PostPlan;

import java.io.File;
import java.io.IOException;

/** Produces the raw 1080x1920 artwork WITHOUT the logo (the brand stamp comes later). */
public interface ArtGenerator {
    File generate(PostPlan plan, File outFile) throws IOException;
}
