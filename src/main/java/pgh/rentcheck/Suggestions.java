package pgh.rentcheck;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * "Did you mean...?" - when an address finds nothing, suggest real addresses that look close.
 *
 * WHY: "no records found" looks identical whether the address is genuinely clean, is outside the
 * city, or is simply misspelled. That is our worst failure, because the user can't tell which.
 *
 * NO LANGUAGE MODELS. This is classical string matching:
 *   1. Ask the city for rows whose address contains the street name we parsed out (no house number).
 *   2. Score every distinct address we get back against what the user typed.
 *   3. Keep the best few, if they are close enough.
 *
 * The score is Levenshtein edit distance (how many single-character edits turn one string into the
 * other) turned into a 0-1 similarity, with a bonus when the house number matches exactly, because
 * "1231 LAKEWOOD ST" and "1233 LAKEWOOD ST" are different buildings, not typos of each other.
 */
public final class Suggestions {
    private Suggestions() {}

    static final int MAX_SUGGESTIONS = 5;
    static final double MIN_SIMILARITY = 0.62;   // below this the "suggestion" is just noise
    static final int SEARCH_LIMIT = 200;
    static final int NUMBER_SEARCH_LIMIT = 400;   // "1231" alone matches more rows than a street name

    public record Suggestion(String address, int similarityPercent) {}

    /**
     * Picks the closest addresses from raw city rows. Public and static so it can be tested
     * without the network.
     */
    static List<Suggestion> best(JsonNode records, String typed) {
        String query = AddressNormalizer.normalize(typed);
        if (query.isBlank()) return List.of();

        // One entry per distinct address, keeping the best score we saw for it.
        Map<String, Double> scores = new LinkedHashMap<>();
        for (JsonNode r : records) {
            JsonNode node = r.path(Fields.ADDRESS);
            if (node.isMissingNode() || node.isNull()) continue;
            String full = node.asText();
            String candidate = AddressNormalizer.normalize(full);
            if (candidate.isBlank() || candidate.equals(query)) continue;   // exact match isn't a suggestion
            double score = similarity(query, candidate);
            Double existing = scores.get(candidate);
            if (existing == null || score > existing) scores.put(candidate, score);
        }

        List<Suggestion> out = new ArrayList<>();
        scores.entrySet().stream()
                .filter(e -> e.getValue() >= MIN_SIMILARITY)
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed()
                        .thenComparing(Map.Entry.comparingByKey()))
                .limit(MAX_SUGGESTIONS)
                .forEach(e -> out.add(new Suggestion(e.getKey(), (int) Math.round(e.getValue() * 100))));
        return out;
    }

    /**
     * 0 = nothing alike, 1 = identical. Edit distance over the longer string, then adjusted:
     * a matching house number is a strong signal, a different one is a strong warning.
     */
    static double similarity(String a, String b) {
        if (a.equals(b)) return 1.0;
        int longer = Math.max(a.length(), b.length());
        if (longer == 0) return 1.0;
        double base = 1.0 - (double) editDistance(a, b) / longer;

        String houseA = houseNumber(a), houseB = houseNumber(b);
        if (houseA != null && houseB != null) {
            if (houseA.equals(houseB)) {
                base += 0.15;                       // same number, so the street is probably the typo
            } else {
                base -= 0.25;                       // different building on the same street
            }
        }
        // Never 1.0: these are only ever spellings that did NOT match, so claiming a
        // "100% match" on the page would be a lie.
        return Math.max(0.0, Math.min(0.99, base));
    }

    private static String houseNumber(String normalized) {
        int space = normalized.indexOf(' ');
        if (space <= 0) return null;
        String first = normalized.substring(0, space);
        return first.matches("\\d+") ? first : null;
    }

    /**
     * Levenshtein distance: the fewest single-character insertions, deletions or substitutions
     * that turn a into b. Two rows instead of a full table, so memory stays small.
     */
    static int editDistance(String a, String b) {
        if (a.isEmpty()) return b.length();
        if (b.isEmpty()) return a.length();

        int[] previous = new int[b.length() + 1];
        int[] current = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) previous[j] = j;

        for (int i = 1; i <= a.length(); i++) {
            current[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                current[j] = Math.min(Math.min(
                        current[j - 1] + 1,          // insert
                        previous[j] + 1),            // delete
                        previous[j - 1] + cost);     // substitute (or free when equal)
            }
            int[] swap = previous; previous = current; current = swap;
        }
        return previous[b.length()];
    }

    /**
     * FIRST search term: the street name without the house number. This works when the street is
     * spelled right and the house number is wrong ("1299 LAKEWOOD ST" -> we find the whole street).
     *
     * It does NOT work when the street itself is misspelled: searching the city for "LAKEWUD"
     * matches nothing, because the city spells it LAKEWOOD. That is what {@link #houseNumberTerm}
     * is for.
     *
     * Returns null when there is nothing useful to search for.
     */
    static String streetSearchTerm(String typed) {
        String normalized = AddressNormalizer.normalize(typed);
        String[] tokens = normalized.split("\\s+");
        if (tokens.length < 2) return null;
        // Drop the house number, and drop a trailing ST/AVE/RD so a wrong suffix doesn't hide matches.
        int from = tokens[0].matches("\\d+") ? 1 : 0;
        int to = tokens.length;
        if (to - from >= 2 && STREET_SUFFIX.contains(tokens[to - 1])) to--;
        if (to <= from) return null;
        return String.join(" ", java.util.Arrays.copyOfRange(tokens, from, to));
    }

    /**
     * SECOND search term, used when searching by street name found nothing: the house number on its
     * own. People usually get the number right and the street wrong, so "1231" pulls back every
     * address in the city with that number, and we then compare spellings against those.
     * Returns null when the user typed no house number (nothing to fall back on).
     */
    static String houseNumberTerm(String typed) {
        String normalized = AddressNormalizer.normalize(typed);
        int space = normalized.indexOf(' ');
        if (space <= 0) return null;
        String first = normalized.substring(0, space);
        return first.matches("\\d{1,6}") ? first : null;
    }

    private static final java.util.Set<String> STREET_SUFFIX = java.util.Set.of(
            "ST", "AVE", "RD", "DR", "BLVD", "LN", "CT", "PL", "TER", "HWY", "PKWY", "WAY", "CIR", "ALY", "SQ", "PLZ", "TRL");
}
