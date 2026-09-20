package ez.factory.plan;

/**
 * Builds the Qwen-Image-Edit instruction that upgrades the deterministic base layout into
 * the rich poster look. Always carries the brand hard-rules: empty top-left logo zone and
 * calm TikTok/Shorts safe zones (right button rail + bottom caption strip).
 */
public final class ArtPromptBuilder {

    public static String build(PostPlan p, String product) {
        StringBuilder b = new StringBuilder();
        b.append("Turn this layout into a rich cinematic 9:16 tech poster for the brand 'EZ Good Finds'. ");
        b.append("Keep every text block exactly where it is and keep all wording verbatim. ");
        b.append("Style: dark editorial poster, deep blacks, one dominant accent colour, glossy product lighting, ");
        b.append("subtle film grain, high contrast, premium movie-poster energy. Subject: ").append(product).append(". ");
        switch (p.archetype == null ? Archetype.WEIRD_GADGET : p.archetype) {
            case CELEBRITY_TECH:
                b.append("Cinematic portrait lighting on the person, gadget glowing as the co-star. ");
                if (p.conceptVisual) b.append("Keep the small CONCEPT VISUAL tag visible. ");
                break;
            case COMPARISON:
                b.append("Split the frame into two duelling colour zones, keep the comparison table crisp and readable. ");
                break;
            case CREATOR_VERIFIED:
                b.append("Documentary look, quote bubble with clean typography, verified-source badge shining subtly. ");
                break;
            case SERIES:
                b.append("Wide desk/battlestation vista with depth, episode number large and collectible-looking. ");
                break;
            case THROW_AWAY:
                b.append("Comic exaggeration: the replaced object dramatically discarded in the background shadows. ");
                break;
            default:
                b.append("Macro hero shot of the device, mysterious discovery mood, hand-held scale cues. ");
        }
        b.append("HARD RULES: leave the top-left logo zone completely empty (pure background, no text, no objects) ");
        b.append("because the official logo is stamped later by the renderer. ");
        b.append("Keep the TikTok/Shorts safe zones calm: the right-side button rail and the bottom caption strip ");
        b.append("must stay dark and uncluttered. No watermark, no extra logo, no border frame.");
        return b.toString();
    }
}
