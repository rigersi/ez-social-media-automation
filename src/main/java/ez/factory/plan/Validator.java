package ez.factory.plan;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Editorial QA gate (brand spec hard rules). A plan that fails here is sent back to the
 * planner once, then quarantined — a plain product ad or a rule-breaking post never ships.
 */
public final class Validator {

    private static final Pattern TAG = Pattern.compile("^#[A-Za-z0-9_]{2,60}$");
    private static final Pattern THROW_RE = Pattern.compile("(?i).*(throw|stop|quit|cancel|delete|uninstall).*");
    private static final Pattern WEIRD_RE = Pattern.compile("(?i).*(\\?|POV|YES, REALLY|did(n'?t| not) know).*");

    public static List<String> check(PostPlan p, int hashtagMin, int hashtagMax) {
        List<String> problems = new ArrayList<>();
        if (p.archetype == null) problems.add("missing archetype");
        if (blank(p.headline, 60)) problems.add("on-image headline missing or >60 chars");
        if (blank(p.hook, 90)) problems.add("hook missing or >90 chars");
        if (p.accent.isEmpty() || p.accent.length() > 48) problems.add("accent phrase missing or >48 chars");
        if (p.callouts.size() < 3 || p.callouts.size() > 8) problems.add("need 3..8 factual callouts, got " + p.callouts.size());
        for (String c : p.callouts) if (c.length() > 70) problems.add("callout too long (>70): " + c);
        if (blank(p.cta, 80)) problems.add("CTA bar text missing or >80 chars");
        if (p.caption.length() < 120 || p.caption.length() > 2200)
            problems.add("caption must be 120..2200 chars (story/joke required), got " + p.caption.length());
        if (p.hashtags.size() < hashtagMin || p.hashtags.size() > hashtagMax)
            problems.add("hashtag count " + p.hashtags.size() + " outside " + hashtagMin + ".." + hashtagMax);
        for (String h : p.hashtags) if (!TAG.matcher(h).matches()) problems.add("bad hashtag: " + h);
        if (p.ytTags.size() < 5 || p.ytTags.size() > 30) problems.add("yt tags 5..30 required, got " + p.ytTags.size());
        if (!p.artPrompt.toLowerCase().contains("top-left") || !p.artPrompt.toLowerCase().contains("logo"))
            problems.add("art_prompt must reserve the top-left logo zone");
        if (!p.artPrompt.toLowerCase().contains("safe zone"))
            problems.add("art_prompt must keep TikTok/Shorts safe zones calm");

        if (p.archetype != null) switch (p.archetype) {
            case COMPARISON:
                if (p.table.size() < 4) problems.add("comparison needs a table with >=4 rows");
                for (List<String> r : p.table) if (r.size() < 3) problems.add("comparison table rows need 3 columns");
                if (p.verdictA.isEmpty() || p.verdictB.isEmpty()) problems.add("comparison needs both 'best for' verdicts");
                break;
            case CREATOR_VERIFIED:
                if (p.quote.isEmpty()) problems.add("creator-verified needs a quote");
                if (p.quoteSource.isEmpty()) problems.add("creator-verified needs a quote source");
                if (p.sources.isEmpty()) problems.add("creator-verified needs >=1 verification source URL");
                break;
            case CELEBRITY_TECH:
                if (!p.conceptVisual) problems.add("fictional celebrity pairing must carry the CONCEPT VISUAL tag");
                break;
            case SERIES:
                if (p.seriesName.isEmpty() || p.seriesNumber < 1) problems.add("series post needs name + number");
                if (!p.caption.toLowerCase().contains("next")) problems.add("series post needs a 'recommend the next' hook in caption");
                break;
            case THROW_AWAY:
                if (!THROW_RE.matcher(p.headline + " " + p.hook).matches())
                    problems.add("throw-away post headline/hook must reference throwing/stopping something");
                break;
            case WEIRD_GADGET:
                if (!WEIRD_RE.matcher(p.headline + " " + p.hook).matches())
                    problems.add("weird-gadget headline/hook needs POV/?/YES-REALLY energy");
                break;
        }
        return problems;
    }

    private static boolean blank(String s, int max) {
        return s == null || s.trim().isEmpty() || s.trim().length() > max;
    }
}
