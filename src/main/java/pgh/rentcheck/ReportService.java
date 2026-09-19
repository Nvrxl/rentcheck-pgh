package pgh.rentcheck;

import com.fasterxml.jackson.databind.JsonNode;
import pgh.rentcheck.Models.CategoryCount;
import pgh.rentcheck.Models.Report;
import pgh.rentcheck.Models.ViolationItem;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Turns raw city records into a renter-friendly Report.
 * NO language models here -- everything is plain logic (this is what the
 * "No Wrapper" track wants). The summary text is built from templates.
 *
 * This is the "brain" of the project and where most of the interesting work is.
 * See docs/TASKS.md for the to-do list (address matching, better scoring, owner matching).
 */
public class ReportService {
    private static final int MAX_RECORDS = 200;

    private final WprdcClient wprdc;

    public ReportService(WprdcClient wprdc) {
        this.wprdc = wprdc;
    }

    public Report buildReport(String address) throws Exception {
        String normalized = AddressNormalizer.normalize(address);
        JsonNode records = wprdc.searchByAddress(normalized, MAX_RECORDS).path("result").path("records");

        List<ViolationItem> items = new ArrayList<>();
        int rowsForAddress = 0;   // investigations on file for this address (violation or not)
        for (JsonNode r : records) {
            // The search can return look-alikes (e.g. "1231 LAKEWOOD" vs "12310 LAKEWOOD"): double check.
            if (!AddressNormalizer.matches(text(r, Fields.ADDRESS), address)) continue;
            rowsForAddress++;
            // Many rows are NOT violations (nothing found, voided, sent to another department...).
            boolean hasDetail = !isBlank(text(r, Fields.DESCRIPTION)) || !isBlank(text(r, Fields.CODE));
            if (!isViolation(text(r, Fields.OUTCOME), hasDetail)) continue;

            items.add(new ViolationItem(
                    day(text(r, Fields.DATE)),
                    text(r, Fields.CASE_TYPE),   // shown in the "Code" column: e.g. "Refuse or Recycling Violations"
                    firstNonBlank(text(r, Fields.DESCRIPTION), text(r, Fields.FINDINGS)),
                    text(r, Fields.STATUS)));
        }
        // Newest first. (ISO dates sort correctly as text; blanks go last.)
        items.sort(Comparator.comparing(ViolationItem::date, Comparator.nullsLast(Comparator.<String>reverseOrder())));

        int open = 0;
        for (ViolationItem v : items) {
            if (looksOpen(v.status())) open++;
        }

        String mostRecent = items.isEmpty() ? null : items.get(0).date();
        String risk = riskLevel(items.size(), open);

        int notCounted = rowsForAddress - items.size();
        String note = "Matched " + rowsForAddress + " city record" + (rowsForAddress == 1 ? "" : "s")
                + " for this address; " + items.size() + " count as violations."
                + (notCounted > 0 ? " The other " + notCounted + " are not counted (no violation found, "
                + "voided, sent to another department, or no recorded outcome)." : "")
                + " A violation is not proof of a bad landlord.";
        if (records.size() >= MAX_RECORDS) {
            note += " Showing only the newest " + MAX_RECORDS + " records.";
        }

        return new Report(address, items.size(), open, mostRecent, risk,
                summarize(items.size(), open, mostRecent),
                categorize(items), items, note);
    }

    /**
     * Outcomes that mean "a violation was found", based on the REAL values in the city data
     * (counted Sat Sep 19 2026 via /api/debug/distinct?field=investigation_outcome).
     * Everything else is NOT counted: "No Violation Found", "Case Voided", "Do Not Display",
     * "Sending back to 311 to Assign to Another Department", "Assigning to Another OneStopPGH Department".
     */
    private static final Set<String> VIOLATION_OUTCOMES = Set.of(
            "violation found",
            "violation resolved",          // it WAS a violation; it was fixed
            "taking case to court",
            "filing court paperwork",
            "dpw to correct issue",        // city crew fixing it
            "follow-up investigation scheduled",
            "case appealed to pli",
            "create lien");

    /**
     * Rule: a record counts as a violation if its outcome is in the list above.
     * About a third of rows (208k of 638k) have NO outcome. For those we count the row only if it
     * names a specific violation (description or code section), which older tickets do.
     * TODO (Michael): check what the no-outcome rows really are (look at their status and
     * case_file_type) and tighten this rule. Explain the rule on the "How we score" page.
     */
    static boolean isViolation(String outcome, boolean hasViolationDetail) {
        if (outcome == null || outcome.isBlank()) return hasViolationDetail;
        return VIOLATION_OUTCOMES.contains(outcome.trim().toLowerCase());
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) return a;
        return (b == null || b.isBlank()) ? null : b;
    }

    /** "2026-02-18T00:00:00" -> "2026-02-18" */
    private static String day(String date) {
        if (date == null) return null;
        return date.length() >= 10 ? date.substring(0, 10) : date;
    }

    // ---------------------------------------------------------------------
    // TODO (Michael): everything below is a first-draft placeholder.
    // ---------------------------------------------------------------------

    /** TODO: check the real status values (see /api/debug/fields) and fix this. */
    static boolean looksOpen(String status) {
        if (status == null || status.isBlank()) return false;
        String s = status.toLowerCase();
        return !(s.contains("close") || s.contains("complet") || s.contains("resolv")
                || s.contains("complied") || s.contains("abate"));
    }

    /** TODO: replace with a real score (weight severity, recency, open vs closed). */
    static String riskLevel(int total, int open) {
        if (total == 0) return "UNKNOWN"; // no records != safe!
        if (open >= 5 || total >= 25) return "HIGH";
        if (open >= 1 || total >= 8) return "MEDIUM";
        return "LOW";
    }

    /** Template-based plain English. Keep it factual and careful. */
    static String summarize(int total, int open, String mostRecent) {
        if (total == 0) {
            return "We found no violation records for this search. That does not guarantee the "
                    + "building is problem-free: the address may not have matched, or issues may "
                    + "not have been reported.";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("We found ").append(total).append(total == 1 ? " violation record" : " violation records");
        sb.append(", ").append(open).append(open == 1 ? " of which looks" : " of which look").append(" unresolved");
        if (mostRecent != null) sb.append(". The most recent one is dated ").append(mostRecent);
        sb.append(".");
        return sb.toString();
    }

    /** TODO: group by a smarter category (fire safety, plumbing, structural...) using the code section. */
    static List<CategoryCount> categorize(List<ViolationItem> items) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (ViolationItem v : items) {
            String key = (v.code() == null || v.code().isBlank()) ? "Other" : v.code();
            counts.merge(key, 1, Integer::sum);
        }
        List<CategoryCount> out = new ArrayList<>();
        counts.forEach((k, v) -> out.add(new CategoryCount(k, v)));
        out.sort(Comparator.comparingInt(CategoryCount::count).reversed());
        return out.size() > 8 ? out.subList(0, 8) : out;
    }

    private static String text(JsonNode record, String field) {
        JsonNode n = record.get(field);
        return (n == null || n.isNull()) ? null : n.asText();
    }

    /** Fake data so the frontend can be built before the real data works. Clearly labelled. */
    public static Report sample() {
        List<ViolationItem> items = List.of(
                new ViolationItem("2025-06-02", "Sample code A", "SAMPLE: smoke detector missing", "Open"),
                new ViolationItem("2024-11-15", "Sample code B", "SAMPLE: broken stair railing", "Closed"),
                new ViolationItem("2023-03-09", "Sample code A", "SAMPLE: smoke detector not working", "Closed"));
        return new Report("123 Example St (SAMPLE)", 3, 1, "2025-06-02", "MEDIUM",
                summarize(3, 1, "2025-06-02"),
                categorize(items), items,
                "SAMPLE DATA - not a real address. Used only for building the interface.");
    }
}
