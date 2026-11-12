package ez.factory.plan;

/**
 * Builds the Qwen-Image-Edit instruction that upgrades the deterministic base layout into
 * the rich poster look.
 *
 * Hard-won rules (v3): keep it SHORT and imperative; anchor on preservation FIRST (the edit
 * model rewrites anything it is not pinned on); never mention the brand name (the model
 * paints it as a hallucinated wordmark); always carry the tokens the editorial validator
 * requires ("top-left", "logo", "safe zone").
 */
public final class ArtPromptBuilder {

    public static String build(PostPlan p, String product, String visual) {
        StringBuilder b = new StringBuilder();
        b.append("Keep every existing text block, card, table and chip exactly where it is: ");
        b.append("crisp, legible, word-for-word unchanged. ");
        b.append("Change only the look: rich cinematic dark product-poster lighting, deep blacks, ");
        b.append("one dominant accent colour, glossy materials, subtle film grain. ");
        b.append("Paint into the empty glowing middle stage: ");
        b.append(visual == null || visual.isEmpty() ? ("a cinematic hero shot of " + product) : visual);
        b.append(", colour-matched to the accent, never covering any text. ");
        b.append("Keep the top-left logo zone completely empty and keep the right-side and bottom ");
        b.append("safe zones calm and dark. Add no new text anywhere.");
        return b.toString();
    }
}
