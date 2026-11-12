package ez.factory.plan;

import ez.factory.util.Json;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Deterministic offline planner. Used when no LLM is reachable (or the LLM output failed
 * validation twice) so the publishing slot never dies. Builds a fully valid plan straight
 * from the topic card fields.
 */
public final class TemplatePlanner {

    public static PostPlan build(Archetype a, Map<String, Object> topic, String seriesName, int seriesNumber,
                                 List<String> hashtagPick, List<String> ytPick) {
        PostPlan p = new PostPlan();
        p.archetype = a;
        p.topicId = Json.str(topic, "id", "");
        String product = Json.str(topic, "product", "this gadget");
        String brand = Json.str(topic, "brand", "");
        List<String> facts = Json.strList(topic, "facts");
        if (facts.isEmpty()) { facts = new ArrayList<>(); facts.add("It exists. It works. It surprises people."); facts.add("Small price, big reaction."); facts.add("Verified by actual owners online."); }
        String angle = Json.str(topic, "angle", "the gadget nobody told you about");

        p.seriesName = seriesName;
        p.seriesNumber = seriesNumber;
        p.seriesTag = seriesName.isEmpty() ? "" : (seriesName + " #" + seriesNumber).toUpperCase();

        switch (a) {
            case COMPARISON: {
                String other = Json.str(topic, "versus", "the rival");
                p.headline = product.toUpperCase() + " VS " + other.toUpperCase() + "?";
                p.hook = "Which one should you buy?";
                p.accent = "HEAD TO HEAD";
                p.verdictA = "Best for: " + Json.str(topic, "best_for_a", "everyday carry");
                p.verdictB = "Best for: " + Json.str(topic, "best_for_b", "power users");
                p.table = new ArrayList<>();
                List<String> cols = new ArrayList<>(); cols.add(""); cols.add(product); cols.add(other);
                p.table.add(cols);
                String[] labels = {"Price", "Battery", "Killer feature", "Weak point"};
                for (int i = 0; i < labels.length && i * 2 + 1 < facts.size(); i++) {
                    List<String> r = new ArrayList<>(); r.add(labels[i]); r.add(facts.get(i * 2)); r.add(facts.get(i * 2 + 1));
                    p.table.add(r);
                }
                while (p.table.size() < 4) { List<String> r = new ArrayList<>(); r.add("Extra"); r.add(facts.get(0)); r.add(facts.get(1 % facts.size())); p.table.add(r); }
                p.caption = product + " or " + other + "? We put them head to head so you don't have to guess.\n\n"
                        + "The short version: " + p.verdictA.toLowerCase() + ", and " + p.verdictB.toLowerCase() + ".\n\n"
                        + "Full spec breakdown is on the card. Swipe, compare, decide — then tell us in the comments which side you're on.";
                break;
            }
            case CREATOR_VERIFIED: {
                String creator = Json.str(topic, "creator", "a creator you watch");
                p.headline = "THE GEAR " + creator.toUpperCase() + " ACTUALLY USES.";
                p.hook = "Not sponsored. Not gifted. Used.";
                p.accent = "VERIFIED SOURCE";
                p.quote = Json.str(topic, "quote", facts.get(0));
                p.quoteSource = creator;
                p.sources = Json.strList(topic, "sources");
                if (p.sources.isEmpty()) p.sources.add("https://youtube.com/@" + creator.toLowerCase().replace(" ", "") );
                p.caption = "Everyone sends gear to creators. Very few items survive a year of real use.\n\n"
                        + "This is what " + creator + " still reaches for: " + product + ".\n\"" + p.quote + "\"\n\n"
                        + "Sources linked in our bio card. If it's not verified, we don't post it. Who should we verify next?";
                break;
            }
            case CELEBRITY_TECH: {
                String celeb = Json.str(topic, "celebrity", "a Hollywood face");
                p.headline = celeb.toUpperCase() + " x " + (brand.isEmpty() ? product.toUpperCase() : brand.toUpperCase());
                p.hook = "What if their story ran on this gear?";
                p.accent = Json.str(topic, "tagline", "FICTION MEETS FUNCTION");
                p.conceptVisual = true;
                p.caption = "Concept visual: imagine " + celeb + "'s next role powered by " + product + ".\n\n"
                        + "Movie-poster energy, real spec sheet: " + String.join(" / ", facts.subList(0, Math.min(3, facts.size()))) + ".\n\n"
                        + "This pairing is a concept — the gear, however, is very real. Would you watch this film?";
                break;
            }
            case SERIES: {
                p.headline = (seriesName.isEmpty() ? "SETUP TOUR" : seriesName.toUpperCase()) + " #" + seriesNumber;
                p.hook = Json.str(topic, "hook", "Clean desk. Serious machine.");
                p.accent = "FULL BREAKDOWN";
                p.caption = seriesName + " #" + seriesNumber + " is live: " + product + " and the rest of the rack.\n\n"
                        + "What's inside: " + String.join(" / ", facts.subList(0, Math.min(4, facts.size()))) + ".\n\n"
                        + "Comment who we should tour next — the most-requested setup becomes episode #" + (seriesNumber + 1) + ".";
                break;
            }
            case THROW_AWAY: {
                String victim = Json.str(topic, "replace_target", "your old routine");
                p.headline = "THROW YOUR " + victim.toUpperCase() + " AWAY.";
                p.hook = product + " just made it obsolete.";
                p.accent = Json.str(topic, "tagline", "SAME RESULT. ZERO DRAMA.");
                p.caption = "Serious proposal: throw your " + victim + " away.\n\n"
                        + "Why? " + product + " does the job with " + facts.get(0).toLowerCase() + ", and then some: "
                        + String.join(", ", facts.subList(1, Math.min(3, facts.size()))).toLowerCase() + ".\n\n"
                        + "Absurd? Maybe. Effective? Also maybe. Tag someone who still pays for the old way.";
                break;
            }
            default: { // WEIRD_GADGET
                p.headline = Json.str(topic, "hook", "POV: YOU FOUND " + product.toUpperCase());
                p.hook = "POV: you found the gadget nobody knew existed.";
                p.accent = Json.str(topic, "tagline", "YES, REALLY.");
                p.caption = "POV: you just found " + product + " — " + angle + ".\n\n"
                        + "The spec sheet reads like a joke but it ships: " + String.join(" / ", facts.subList(0, Math.min(4, facts.size()))) + ".\n\n"
                        + "Where would YOU use this? Best answer gets pinned.";
                break;
            }
        }

                if (p.quoteSource.isEmpty()) {
            String creator = Json.str(topic, "creator", "");
            if (!creator.isEmpty()) {
                p.quoteSource = creator;
                if (p.quote.isEmpty()) p.quote = Json.str(topic, "quote", "");
                if (p.sources.isEmpty()) p.sources = Json.strList(topic, "sources");
            }
        }
        while (p.callouts.size() < Math.min(6, facts.size())) p.callouts.add(facts.get(p.callouts.size()));
        while (p.callouts.size() < 3) p.callouts.add("Owner-verified in the wild");
        p.cta = Json.str(topic, "cta", "FOLLOW EZ GOOD FINDS — GEAR THAT EARNS ITS SPACE");
        p.assetPath = Json.str(topic, "asset", "");
        p.hashtags = new ArrayList<>(hashtagPick);
        p.ytTags = new ArrayList<>(ytPick);
        p.product = product;
        p.visual = Json.str(topic, "visual", "");
        p.artPrompt = ArtPromptBuilder.build(p, product, p.visual);
        return p;
    }
}