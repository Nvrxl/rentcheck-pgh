package pgh.rentcheck;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Building permits: evidence of work actually being DONE, legally, with the city's knowledge.
 *
 * Why this matters more than it sounds. Violations tell a renter what is wrong with a building.
 * Permits tell them whether anyone is fixing it properly. A building with five violations and five
 * permits is being looked after. A building with five violations and nothing permitted in six years
 * is not. Nobody shows renters that comparison, and it is the closest public data comes to
 * answering "will my landlord actually repair things".
 *
 * Source: City of Pittsburgh PLI Permits, updated daily. Column names verified against the live
 * dataset on Sat Sep 19 2026 via /api/debug/peek?package=pli-permits.
 *
 * TWO LIMITS WE MUST STATE, or the numbers mislead:
 *  1. The data starts in JUNE 2019. No permits before then does NOT mean no work was done.
 *  2. Plumbing permits are NOT here: the Allegheny County Health Department issues those.
 * Also: the same permit appears on several rows (the city splits them by street closure), so we
 * count distinct permit ids, exactly like we count violation cases and not rows.
 */
public final class Permits {
    private Permits() {}

    /** The earliest date this dataset covers. Stated on the page so absence is read correctly. */
    static final String DATA_STARTS = "2019-06-03";
    static final int RECENT_YEARS = 5;

    public record PermitItem(String date, String type, String workType, String description,
                             Integer projectValue, String status) {}

    public record PermitHistory(
            int total,                 // distinct permits on record
            int recent,                // ...issued in the last 5 years
            Long totalProjectValue,    // sum of stated project values, or null if none were given
            String mostRecent,         // date of the newest permit (yyyy-MM-dd), or null
            List<PermitItem> items,    // newest first, capped
            String summary,            // plain English for the page
            String note                // the two limits above
    ) {}

    static final int MAX_ITEMS = 8;

    /**
     * Turns raw permit rows into a history. Returns null when there is nothing on record, so the
     * page can say "no permits since 2019" itself rather than showing an empty card.
     */
    static PermitHistory from(JsonNode records, int violationCount, LocalDate today) {
        // One entry per permit id: the city repeats a permit across rows.
        Map<String, JsonNode> byId = new LinkedHashMap<>();
        int unnamed = 0;
        for (JsonNode r : records) {
            String id = text(r, "permit_id");
            if (id == null) id = "row-" + (unnamed++);
            JsonNode existing = byId.get(id);
            if (existing == null) byId.put(id, r);
        }
        if (byId.isEmpty()) return null;

        String cutoff = today.minusYears(RECENT_YEARS).toString();
        List<PermitItem> items = new ArrayList<>();
        long valueSum = 0;
        boolean anyValue = false;
        int recent = 0;
        String mostRecent = null;

        for (JsonNode r : byId.values()) {
            String date = day(text(r, "issue_date"));
            Integer value = integer(r, "total_project_value");
            if (value != null && value > 0) {
                valueSum += value;
                anyValue = true;
            }
            if (date != null) {
                if (date.compareTo(cutoff) >= 0) recent++;
                if (mostRecent == null || date.compareTo(mostRecent) > 0) mostRecent = date;
            }
            items.add(new PermitItem(date, text(r, "permit_type"), text(r, "work_type"),
                    text(r, "work_description"), value, text(r, "status")));
        }

        items.sort((a, b) -> {
            if (a.date() == null) return 1;
            if (b.date() == null) return -1;
            return b.date().compareTo(a.date());
        });
        List<PermitItem> shown = items.size() > MAX_ITEMS ? new ArrayList<>(items.subList(0, MAX_ITEMS)) : items;

        return new PermitHistory(byId.size(), recent, anyValue ? valueSum : null, mostRecent, shown,
                summarize(byId.size(), recent, anyValue ? valueSum : null, mostRecent, violationCount),
                "City building permits only, and only since June 2019: nothing here does not mean nothing "
                        + "was done. Plumbing permits are issued by the county health department and are not "
                        + "included. A permit means work was declared to the city, not that it was done well.");
    }

    /**
     * The sentence a renter reads. It deliberately does NOT declare a landlord good or bad: it puts
     * the two numbers side by side and lets the reader draw the conclusion.
     */
    static String summarize(int total, int recent, Long value, String mostRecent, int violationCount) {
        StringBuilder sb = new StringBuilder();
        sb.append("The city has issued ").append(total).append(total == 1 ? " building permit" : " building permits")
                .append(" here since June 2019");
        if (recent > 0 && recent != total) sb.append(", ").append(recent).append(" in the last five years");
        sb.append(".");
        if (value != null && value > 0) {
            sb.append(" The declared value of that work is about $").append(String.format("%,d", value)).append(".");
        }
        if (mostRecent != null) sb.append(" The most recent was ").append(mostRecent).append(".");
        if (violationCount > 0) {
            sb.append(" For comparison, the city recorded ").append(violationCount)
                    .append(violationCount == 1 ? " violation" : " violations").append(" at this address.");
        }
        return sb.toString();
    }

    /** Shown when an address has violations but no permits at all. Careful wording on purpose. */
    static String noPermitsNote(int violationCount) {
        if (violationCount <= 0) {
            return "No city building permits on record here since June 2019. For a home with no violations "
                    + "either, that usually just means nothing major needed doing.";
        }
        return "No city building permits on record here since June 2019, while the city did record "
                + violationCount + (violationCount == 1 ? " violation" : " violations")
                + ". Permitted work is not the only kind of repair - small fixes do not need a permit, and "
                + "plumbing permits are issued by the county, not the city - but it is worth asking what "
                + "work has been done and whether it was permitted.";
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
            return (int) Math.round(Double.parseDouble(n.asText().replace(",", "").trim()));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
