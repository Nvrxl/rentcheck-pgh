package pgh.rentcheck;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Is this property CONDEMNED - declared unfit for occupancy by the city?
 *
 * This is the single most important thing we can tell a renter. Everything else in the report is
 * "here is the history, judge for yourself". This one is "the city says nobody should be living here".
 *
 * Source: City of Pittsburgh "Condemned and Dead-End Properties", updated daily. A "Dead-End
 * Property" is a condemned property where PLI could not find the owner after repeated attempts.
 * Column names verified against the live dataset on Sat Sep 19 2026 via
 * /api/debug/peek?package=condemned-properties.
 *
 * WE DO NOT SHOW THE OWNER NAME. The dataset contains one, including private individuals. A renter
 * does not need a name to decide anything, and republishing someone's name next to the word
 * "condemned" is not our call to make.
 */
public final class Condemned {
    private Condemned() {}

    /** Only ever built when a matching record was found. */
    public record CondemnedRecord(
            String propertyType,     // "Condemned Property" / "Dead-End Property" / combined label
            String since,            // when the city created the record (yyyy-MM-dd), or null
            String inspectionStatus, // "Active" means an ongoing investigation
            String latestResult,     // "Pass" / "Fail" / null
            Integer latestScore,     // 0 = passed; higher = worse
            boolean ownerNotFound,   // the "dead end" case
            String warning,          // the sentence to put in front of the renter
            String note              // where this came from, and its limits
    ) {}

    /**
     * Reads the city's rows. Returns null when there is nothing to report, which is the normal case
     * for the overwhelming majority of addresses (about 3,500 records in the whole city).
     *
     * Records marked "Inactive" are ignored: the city omits them precisely because they are no
     * longer a live investigation, and flagging one would scare a renter over settled history.
     */
    static CondemnedRecord from(JsonNode records) {
        JsonNode best = null;
        for (JsonNode r : records) {
            String status = text(r, "inspection_status");
            if (status != null && !status.equalsIgnoreCase("Active")) continue;
            // If several records match, keep the newest one.
            if (best == null || after(text(r, "create_date"), text(best, "create_date"))) best = r;
        }
        if (best == null) return null;

        String type = text(best, "property_type");
        boolean deadEnd = isDeadEnd(type);
        String since = day(text(best, "create_date"));

        StringBuilder warning = new StringBuilder();
        warning.append("The City of Pittsburgh lists this property as condemned, which means PLI has "
                + "determined it is not fit to live in");
        if (since != null) warning.append(", on record since ").append(since);
        warning.append(". Do not sign a lease here without checking directly with PLI. ");
        if (deadEnd) {
            warning.append("It is also flagged as a \"dead end\" property, meaning the city could not "
                    + "reach the owner. ");
        }
        warning.append("If someone is offering to rent this to you, that is a serious warning sign.");

        return new CondemnedRecord(type, since, text(best, "inspection_status"),
                text(best, "latest_inspection_result"), integer(best, "latest_inspection_score"),
                deadEnd, warning.toString(),
                "From the city's condemned property list, updated daily. A condemnation can be lifted "
                        + "after repairs, and this list can lag, so check with PLI before acting on it.");
    }

    /**
     * Is this specifically a "dead end" property, i.e. condemned AND the city could not find the owner?
     *
     * The documentation says this column holds either "Condemned Property" or "Dead-End Property",
     * but the live data uses one COMBINED label, "Condemned/Dead End Property", for both. So when we
     * see the combined label we do not know which it is, and we must not claim the owner is missing.
     * We only say "dead end" when the label says dead end and does NOT also say condemned.
     */
    static boolean isDeadEnd(String propertyType) {
        if (propertyType == null) return false;
        String t = propertyType.toLowerCase();
        if (!t.contains("dead")) return false;
        return !t.contains("condemned");
    }

    /** true when a is a later date than b (plain text compare works on yyyy-MM-dd). */
    private static boolean after(String a, String b) {
        if (a == null) return false;
        if (b == null) return true;
        return a.compareTo(b) > 0;
    }

    private static String day(String date) {
        if (date == null) return null;
        return date.length() >= 10 ? date.substring(0, 10) : date;
    }

    private static String text(JsonNode r, String field) {
        JsonNode n = r.path(field);
        if (n.isMissingNode() || n.isNull()) return null;
        String s = n.asText().trim();
        return s.isEmpty() ? null : s;
    }

    private static Integer integer(JsonNode r, String field) {
        JsonNode n = r.path(field);
        if (n.isMissingNode() || n.isNull()) return null;
        try {
            return (int) Math.round(Double.parseDouble(n.asText().trim()));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
