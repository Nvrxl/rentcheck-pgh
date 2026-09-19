package pgh.rentcheck;

/**
 * The city has ~150 different violation "case types", with near-duplicates
 * ("Weeds/Debris", "Weeds and Debris", "Weeds or Debris"). We sort them into a few
 * groups a renter understands. Building & fire safety is what matters most to someone
 * who would LIVE in the unit; a weedy lot or a broken public sidewalk matters much less.
 *
 * No AI: plain keyword rules, checked in order. First match wins.
 * TODO: review the rules with the full list at /api/debug/distinct?field=case_file_type
 */
public final class Categories {
    private Categories() {}

    public static final String SAFETY  = "Building & fire safety";
    public static final String VACANT  = "Vacant property";
    public static final String TRASH   = "Trash, weeds & junk";
    public static final String STREETS = "Sidewalks, streets & utilities";
    public static final String ZONING  = "Zoning, permits & licensing";
    public static final String OTHER   = "Other";

    public static String bucket(String caseType) {
        if (caseType == null || caseType.isBlank()) return OTHER;
        String t = caseType.toLowerCase();

        if (t.contains("vacant")) return VACANT;

        if (t.contains("land operations")) return ZONING;
        if (has(t, "building maintenance", "fire", "electrical", "hvac", "unpermitted",
                "without a permit", "without permits", "construction site", "retaining wall",
                "dangerous tree", "sewer lateral")) return SAFETY;

        if (has(t, "weeds", "refuse", "recycl", "dump", "couch", "junk", "set out",
                "plastic bag", "graffiti")) return TRASH;

        if (has(t, "sidewalk", "curb", "utility", "road", "street", "wires", "pole",
                "blocked", "obstruction", "tree", "stormwater", "fence")) return STREETS;

        if (has(t, "zoning", "license", "historic")) return ZONING;

        return OTHER;
    }

    private static boolean has(String text, String... words) {
        for (String w : words) if (text.contains(w)) return true;
        return false;
    }
}
