package pgh.rentcheck;

import java.util.Map;

/**
 * Makes addresses comparable. The city stores "1231 LAKEWOOD ST, Pittsburgh, PA 15220-"
 * but a renter types "1231 Lakewood Street". We turn BOTH into the same short form:
 * "1231 LAKEWOOD ST".
 *
 * No AI here: just upper-casing, chopping off city/state/zip and unit numbers, and
 * swapping long words for standard abbreviations. (First draft: see docs/TASKS.md.)
 */
public final class AddressNormalizer {
    private AddressNormalizer() {}

    private static final Map<String, String> ABBREVIATIONS = Map.ofEntries(
            Map.entry("STREET", "ST"), Map.entry("AVENUE", "AVE"), Map.entry("ROAD", "RD"),
            Map.entry("DRIVE", "DR"), Map.entry("BOULEVARD", "BLVD"), Map.entry("LANE", "LN"),
            Map.entry("COURT", "CT"), Map.entry("PLACE", "PL"), Map.entry("TERRACE", "TER"),
            Map.entry("HIGHWAY", "HWY"), Map.entry("PARKWAY", "PKWY"),
            Map.entry("NORTH", "N"), Map.entry("SOUTH", "S"), Map.entry("EAST", "E"), Map.entry("WEST", "W"));

    /** "1231 Lakewood Street, Pittsburgh, PA 15220" -> "1231 LAKEWOOD ST" */
    public static String normalize(String raw) {
        if (raw == null) return "";
        String s = raw.toUpperCase().trim();

        int comma = s.indexOf(',');                       // drop ", Pittsburgh, PA 15220-"
        if (comma >= 0) s = s.substring(0, comma);

        s = s.replaceAll("\\s*#\\s*\\w+\\s*$", "");       // drop trailing "#2"
        s = s.replaceAll("\\s+(APT|UNIT|STE|SUITE)\\b.*$", ""); // drop "APT 3", "UNIT B"...
        s = s.replace(".", " ");

        StringBuilder out = new StringBuilder();
        for (String token : s.trim().split("\\s+")) {
            if (token.isEmpty()) continue;
            if (out.length() > 0) out.append(' ');
            out.append(ABBREVIATIONS.getOrDefault(token, token));
        }
        return out.toString();
    }

    /**
     * Does a record's address belong to the address the user typed?
     * Exact match, or the user's text is the start of the record's address at a word
     * boundary ("1231 LAKEWOOD" matches "1231 LAKEWOOD ST" but "123 LAKE" does not
     * match "123 LAKEWOOD ST").
     */
    public static boolean matches(String recordAddress, String queryAddress) {
        String r = normalize(recordAddress);
        String q = normalize(queryAddress);
        if (q.isEmpty()) return false;
        return r.equals(q) || r.startsWith(q + " ");
    }
}
