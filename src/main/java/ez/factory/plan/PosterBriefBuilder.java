package ez.factory.plan;

import java.util.ArrayList;
import java.util.List;

/**
 * Designer-mode creative brief (v9 "flash-taught"): turns a PostPlan into the full
 * art-direction text that a frontier image model (Gemini "Nano Banana") renders as a FINISHED
 * 9:16 poster, text included. v9 returns to the cheap flash painter and teaches it the lessons
 * of every judged paint: the user's favourite poster (pedestal hero, glass quote card with a
 * named human, centred headline under an unbothered logo) fused with the campaign north star
 * (cinematic person, collab lockup, themed motif). House style from the brand's real feed:
 * dark layered environment, one electric accent, ALL-CAPS condensed headline, calm logo
 * corner. Java stamps the official logo afterwards.
 */
public final class PosterBriefBuilder {

    public static String build(PostPlan p) {
        StringBuilder b = new StringBuilder();
        b.append("Design a FINISHED 9:16 vertical social-media poster (1080x1920) for tech-gadget brand ")
         .append("EZ Good Finds. This is the final artwork, not a template: a premium brand-campaign ")
         .append("poster with cinematic human presence, never an empty product catalogue.\n\n");

        b.append("HOUSE STYLE: a near-black / deep-navy cinematic ENVIRONMENT, never flat empty black — layered with ")
         .append("a deep gradient glow in the accent colour, subtle tech-grid or circuit texture, floating bokeh dust ")
         .append("particles, soft volumetric light and a glossy floor reflection under the hero; ONE electric accent ")
         .append("colour (electric blue, cyan, magenta or green — whichever fits the product) used for the headline ")
         .append("keyword, glows, tile edges and icons; giant bold condensed white sans-serif headline set in ALL-CAPS, ")
         .append("horizontally CENTRED, with one keyword in the accent colour; a thin centred accent highlight bar ")
         .append("directly under the headline; high-detail premium commercial advertising grade, crisp and saturated.\n\n");

        b.append("HERO PRODUCT: render the REAL, purchasable product \"").append(safe(p.product))
         .append("\" as photorealistic studio product photography: recognisable real-world industrial design, accurate ")
         .append("proportions, realistic materials, reflections and physical weight, glowing accent rim light. ")
         .append("PRODUCT DOMINANCE: the product is the main attraction — the largest, brightest, sharpest object in ")
         .append("the frame, at least one third of the poster height; any person is secondary: smaller scale, softer ")
         .append("light, placed behind or beside the product, interacting with it but never overshadowing it. ")
         .append("Render the exact commercial product as sold in stores, with its real branding, markings, ports and ")
         .append("materials visible; never substitute a generic look-alike. ")
         .append("Scene direction: ").append(safe(p.visual)).append("\n\n");

        if (!p.table.isEmpty()) {
            b.append("LAYOUT: head-to-head comparison. MANDATORY: both real product heroes, large and photorealistic, ")
             .append("angled toward each other in the middle band of the poster (each about a third of the poster ")
             .append("height), with a charged accent-coloured energy clash between them — a comparison poster that does ")
             .append("not visibly draw BOTH products is REJECTED. Below the heroes: a compact dark glass spec panel with ")
             .append("one column per product (product names as column headers; the first column holds feature names and ")
             .append("has NO header word — never write 'Label'); render EVERY column of EVERY row below, never collapse ")
             .append("or drop columns:\n");
            for (List<String> row : p.table) b.append(" - ").append(safe(String.join(" | ", row))).append('\n');
            List<String> verdicts = new ArrayList<>();
            if (!p.verdictA.isEmpty()) verdicts.add(p.verdictA);
            if (!p.verdictB.isEmpty()) verdicts.add(p.verdictB);
            if (!verdicts.isEmpty()) {
                b.append("Render each verdict as a solid accent-coloured rounded chip with bold dark text, chips side by ")
                 .append("side under the panel, reading exactly: ");
                for (int i = 0; i < verdicts.size(); i++) {
                    if (i > 0) b.append(" and ");
                    b.append("\"").append(safe(verdicts.get(i))).append("\"");
                }
                b.append("\n");
            }
        } else if (!p.quote.isEmpty()) {
            b.append("LAYOUT: cinematic creator-verified campaign poster, in this exact composition: ");
            b.append("(1) PERSON: an upper-body cinematic portrait of ").append(safe(p.quoteSource))
             .append(" in wardrobe and lighting matching the story world, placed left-of-centre beside the product; if ")
             .append("an exact photoreal likeness is not possible, keep wardrobe, pose and world exact and let dramatic ")
             .append("lighting shadow the face; ");
            b.append("(2) PRODUCT HERO centre-stage on a round glossy pedestal stage with an accent-coloured ring light ")
             .append("at its base, the product large and glowing, with a thin dark name band under it reading exactly \"")
             .append(safe(p.product)).append("\"; ");
            b.append("(3) COLLAB LOCKUP in the top-right corner: stacked bold display type reading \"")
             .append(safe(p.quoteSource)).append("\" then \"X\" then \"").append(safe(p.product)).append("\"; ");
            b.append("(4) QUOTE CARD at mid-right: a dark glass card with one oversized accent-coloured quotation mark, ")
             .append("the quote in clean white type reading exactly \"").append(safe(p.quote))
             .append("\", and a small attribution line under it reading exactly \"").append(safe(p.quoteSource)).append("\"; ");
            b.append("(5) four dark glass callout tiles with thin accent edges floating around the hero, each with one ")
             .append("photorealistic text-free detail visual plus one exact label from the TILE LABELS list; ");
            if (!p.cta.isEmpty()) {
                b.append("(6) CTA: an angled solid accent-coloured chip in the bottom-left reading exactly \"")
                 .append(safe(p.cta)).append("\". ");
            }
            b.append("BACKGROUND: one oversized themed motif from the story world (flag texture, team emblem, energy ")
             .append("field, skyline) blended softly into the dark environment behind the person.\n");
        } else {
            b.append("LAYOUT: cinematic product story: ");
            if (!p.quoteSource.isEmpty()) {
                b.append("a cinematic upper-body portrait of ").append(safe(p.quoteSource))
                 .append(" beside the product (if an exact photoreal likeness is not possible, keep wardrobe, pose and ")
                 .append("world exact and let dramatic lighting shadow the face), plus a COLLAB LOCKUP in the top-right ")
                 .append("corner: stacked bold display type reading \"").append(safe(p.quoteSource))
                 .append("\" then \"X\" then \"").append(safe(p.product)).append("\"; ");
            } else {
                b.append("a person using the product in a real-life moment that matches the scene direction, secondary ")
                 .append("in scale to the product, ");
            }
            b.append("the product glowing in their hands or on their body, with a thin dark NAME BAND under it reading ")
             .append("exactly \"").append(safe(p.product)).append("\"; 4-6 dark glass callout tiles floating around them; ");
            if (!p.cta.isEmpty()) {
                b.append("CTA as an angled solid accent-coloured chip in the bottom-left reading exactly \"")
                 .append(safe(p.cta)).append("\". ");
            }
            b.append('\n');
        }

        b.append("\nTILE RULES: each tile shows ONLY one exact label from the list below plus one clean icon or tiny ")
         .append("photorealistic detail visual. NEVER write the word 'tile', never number the tiles, no extra words on ")
         .append("any tile. Icons and mini visuals must be completely text-free: no tiny words, no screen text, no ")
         .append("numbers — icons and photographic detail only.\n");

        b.append("\nTEXT TO RENDER, VERBATIM, EXACT SPELLING, IN THIS HIERARCHY:\n");
        b.append("HEADLINE: \"").append(safe(p.headline)).append("\"\n");
        if (!p.accent.isEmpty()) b.append("ACCENT BAR: \"").append(safe(p.accent)).append("\"\n");
        if (!p.callouts.isEmpty()) {
            b.append("TILE LABELS:\n");
            for (String c : p.callouts) b.append(" - \"").append(safe(c)).append("\"\n");
        }
        if (!p.seriesTag.isEmpty()) b.append("SERIES TAG (small, near headline): \"").append(safe(p.seriesTag)).append("\"\n");
        if (p.conceptVisual) b.append("CONCEPT TAG (small chip): \"CONCEPT VISUAL\"\n");
        if (!p.cta.isEmpty()) b.append("CTA CHIP: \"").append(safe(p.cta)).append("\"\n");
        b.append("No other text anywhere in the image.\n\n");

        b.append("HARD RULES: the top-left square (left third of the width, top sixth of the height) is RESERVED for a logo ")
         .append("stamped later by software: draw NOTHING there — no logo, no badge, no panel, no box, no rectangle, no ")
         .append("brighter or darker patch, no circuit traces, no light beams: the environment must flow through that ")
         .append("square completely unchanged, as if nothing will ever be placed there; no headline, no text, no chips, ")
         .append("no product, no face inside it either; the headline block must be horizontally CENTRED and start BELOW that ")
         .append("square — the top edge of its first line at or below one fifth (20%) of the poster height — never ")
         .append("inside or overlapping the square, even if the composition feels tight; keep the right edge ")
         .append("strip and the very bottom strip visually calm (platform buttons overlay them); the composition must ")
         .append("fill the ENTIRE frame edge to edge — no large empty or flat regions anywhere, environment glow, ")
         .append("particles and reflections continue behind every element; no watermark, no signature, no border; do not ")
         .append("invent extra words; render every quoted string exactly as written; never draw social-media logos, app ")
         .append("icons or interface glyphs anywhere.");
        return b.toString();
    }

    private static String safe(String s) {
        return s == null ? "" : s.replace('"', '\'');
    }
}