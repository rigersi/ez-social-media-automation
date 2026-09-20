package ez.factory.plan;

/** The six editorial archetypes. Rotation is enforced; a plain product ad is not an archetype. */
public enum Archetype {
    CELEBRITY_TECH("celebrity_tech",
            "A famous face concept-paired with a gadget, cinematic movie-poster energy. "
          + "Fictional pairings MUST carry a small CONCEPT VISUAL tag. Lockup: NAME x BRAND + tagline."),
    WEIRD_GADGET("weird_gadget",
            "\"POV: you found...\", \"Pocket projector? YES, REALLY.\" — devices people didn't know existed."),
    COMPARISON("comparison",
            "Two products head-to-head with a comparison table and a clear 'best for X / best for Y' verdict."),
    SERIES("series",
            "Numbered episodic run (setup tours, tech files...). Carries its number and a 'recommend the next' hook."),
    THROW_AWAY("throw_away",
            "\"Throw away your X\" — a gadget as an absurd replacement; the funnier/irreverent the replaced thing, the better."),
    CREATOR_VERIFIED("creator_verified",
            "\"The headphones MKBHD / Linus actually uses\" — quote bubble, verified-source badge, real evidence.");

    public final String id;
    public final String description;

    Archetype(String id, String description) {
        this.id = id;
        this.description = description;
    }

    public static Archetype fromId(String id) {
        for (Archetype a : values()) if (a.id.equalsIgnoreCase(id)) return a;
        return null;
    }

    public static String allIds() {
        StringBuilder b = new StringBuilder();
        for (Archetype a : values()) b.append(a.id).append(' ');
        return b.toString().trim();
    }
}
