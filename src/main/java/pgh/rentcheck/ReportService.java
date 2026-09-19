package pgh.rentcheck;

import com.fasterxml.jackson.databind.JsonNode;
import pgh.rentcheck.Models.CategoryCount;
import pgh.rentcheck.Models.Report;
import pgh.rentcheck.Models.ViolationItem;

import java.time.LocalDate;
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
 * See docs/TASKS.md for the to-do list (scoring, better matching, owner matching).
 *
 * KEY FACT ABOUT THE DATA: the city stores SEVERAL ROWS PER CASE. Example: case
 * CF-ES-2026-008155 (a tire in a yard) is 3 rows: the first inspection ("Violation Found"),
 * a re-inspection ("Violation Resolved", finding "CORRECTED"), and a detail row with the
 * legal code section. Counting rows would say "3 violations"; the honest answer is 1.
 * So we group rows by casefile_number and count CASES.
 */
public class ReportService {
    private static final int MAX_RECORDS = 200;

    private final WprdcClient wprdc;

    public ReportService(WprdcClient wprdc) {
        this.wprdc = wprdc;
    }

    public Report buildReport(String address) throws Exception {
        String searchTerms = AddressNormalizer.searchTerms(address);   // e.g. "1829 MCNARY"
        JsonNode records = wprdc.searchByAddress(searchTerms, MAX_RECORDS).path("result").path("records");

        // Step 1: keep only rows for this exact address, grouped by case number.
        Map<String, List<JsonNode>> byCase = new LinkedHashMap<>();
        int rowsForAddress = 0;
        for (JsonNode r : records) {
            // The search can return look-alikes (e.g. "1231 LAKEWOOD" vs "12310 LAKEWOOD"): double check.
            if (!AddressNormalizer.matches(text(r, Fields.ADDRESS), address)) continue;
            rowsForAddress++;
            String caseId = text(r, Fields.CASEFILE);
            if (isBlank(caseId)) caseId = "row-" + r.path("_id").asText();
            byCase.computeIfAbsent(caseId, k -> new ArrayList<>()).add(r);
        }

        // Step 2: one violation per case (or nothing, if the case isn't a real violation).
        List<ViolationItem> items = new ArrayList<>();
        for (Map.Entry<String, List<JsonNode>> e : byCase.entrySet()) {
            ViolationItem item = toViolation(e.getKey(), e.getValue());
            if (item != null) items.add(item);
        }
        // Newest first. (ISO dates sort correctly as text; blanks go last.)
        items.sort(Comparator.comparing(ViolationItem::date, Comparator.nullsLast(Comparator.<String>reverseOrder())));

        int open = 0;          // unresolved, any kind
        int safety = 0;        // building & fire safety violations, any status
        int safetyOpen = 0;    // ...that are still unresolved
        for (ViolationItem v : items) {
            boolean isOpen = looksOpen(v.status());
            boolean isSafety = Categories.SAFETY.equals(Categories.bucket(v.code()));
            if (isOpen) open++;
            if (isSafety) safety++;
            if (isSafety && isOpen) safetyOpen++;
        }

        String mostRecent = items.isEmpty() ? null : items.get(0).date();
        String risk = riskLevel(items, LocalDate.now());

        int cases = byCase.size();
        int notCounted = cases - items.size();
        String note = "Matched " + rowsForAddress + " city row" + (rowsForAddress == 1 ? "" : "s")
                + " for this address, grouped into " + cases + " case" + (cases == 1 ? "" : "s")
                + "; " + items.size() + (items.size() == 1 ? " counts" : " count") + " as a violation."
                + (notCounted > 0 ? " The other " + notCounted + " are not counted (no violation found, "
                + "voided, sent to another department, or no recorded outcome)." : "")
                + " A violation is not proof of a bad landlord.";
        if (records.size() >= MAX_RECORDS) {
            note += " Showing only the newest " + MAX_RECORDS + " records.";
        }

        return new Report(address, items.size(), open, mostRecent, risk,
                summarize(items.size(), open, safety, safetyOpen, mostRecent),
                categorize(items), items, note, Questions.forRecords(items));
    }

    /**
     * Outcomes that mean "a violation was found", based on the REAL values in the city data
     * (counted Sat Sep 19 2026 via /api/debug/distinct?field=investigation_outcome).
     * Everything else does not by itself make a case a violation: "No Violation Found",
     * "Case Voided", "Do Not Display", "Sending back to 311 to Assign to Another Department",
     * "Assigning to Another OneStopPGH Department".
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
     * Combines all rows of ONE case into one violation, or returns null if it is not a violation.
     *
     * A case counts as a violation if ANY of its rows either has a violation outcome
     * (see list above) or names a specific violation (description / legal code section: the
     * "detail row", which older cases have instead of an outcome).
     * A case does NOT count if it was voided/hidden ("Case Voided", "Do Not Display") or
     * its status is "Cancelled".
     *
     * TODO (Michael): confirm this rule on a few more addresses with /api/debug/rows and
     * explain it on the "How we score" page.
     */
    private static ViolationItem toViolation(String caseId, List<JsonNode> rows) {
        String status = null, caseType = null, description = null, codeSection = null;
        String foundFinding = null, anyFinding = null, issued = null, resolved = null;
        boolean voided = false, counts = false;

        for (JsonNode r : rows) {
            String outcome = lower(text(r, Fields.OUTCOME));
            String rowStatus = text(r, Fields.STATUS);
            if ("cancelled".equalsIgnoreCase(rowStatus)
                    || "case voided".equals(outcome) || "do not display".equals(outcome)) {
                voided = true;
            }
            if (status == null) status = rowStatus;
            if (caseType == null) caseType = text(r, Fields.CASE_TYPE);

            String desc = text(r, Fields.DESCRIPTION);
            String code = text(r, Fields.CODE);
            if (description == null && !isBlank(desc)) description = desc;
            if (codeSection == null && !isBlank(code)) codeSection = code;
            if (!isBlank(desc) || !isBlank(code)) counts = true;                 // the "detail row"
            if (outcome != null && VIOLATION_OUTCOMES.contains(outcome)) counts = true;

            String finding = text(r, Fields.FINDINGS);
            if (!isBlank(finding)) {
                if (anyFinding == null) anyFinding = finding;
                if ("violation found".equals(outcome) && foundFinding == null) foundFinding = finding;
            }

            String date = day(text(r, Fields.DATE));
            if (date != null) {
                if (issued == null || date.compareTo(issued) < 0) issued = date;   // earliest = when it was issued
                if ("violation resolved".equals(outcome) && (resolved == null || date.compareTo(resolved) > 0)) {
                    resolved = date;
                }
            }
        }

        if (voided || !counts) return null;
        String what = description != null ? description : (foundFinding != null ? foundFinding : anyFinding);
        return new ViolationItem(issued, caseType, what, status, caseId, resolved, codeSection);
    }

    private static String lower(String s) {
        return s == null ? null : s.trim().toLowerCase();
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    /** "2026-02-18T00:00:00" -> "2026-02-18" */
    private static String day(String date) {
        if (date == null) return null;
        return date.length() >= 10 ? date.substring(0, 10) : date;
    }

    // ---------------------------------------------------------------------
    // Status, score, wording. First drafts: tune and document them (docs/HOW_WE_SCORE.md).
    // ---------------------------------------------------------------------

    /**
     * Statuses that mean "still not resolved" (real values, counted Sat Sep 19 2026):
     * Closed 584k | In Court 40k | In Violation 7.7k | Clean & Lien 5.2k | Ready to Close 664 |
     * Under Investigation 152 | Cancelled 144 | Appealed 25.
     * "Ready to Close" is treated as resolved; "Under Investigation" is not a confirmed violation yet.
     */
    private static final Set<String> OPEN_STATUSES = Set.of("in violation", "in court", "clean & lien", "appealed");

    static boolean looksOpen(String status) {
        return status != null && OPEN_STATUSES.contains(status.trim().toLowerCase());
    }

    // Points scoring. Full explanation for humans: docs/HOW_WE_SCORE.md (keep the two in sync!).
    static final int POINTS_OPEN_RECENT = 3;    // unresolved building/fire safety case from the last 3 years
    static final int POINTS_OPEN_OLD = 1;       // unresolved but older: city cases can sit "In Court" for years
    static final int POINTS_RESOLVED_RECENT = 1; // fixed building/fire safety case from the last 3 years
    static final int MAX_RESOLVED_POINTS = 4;   // history alone can never push a rating past MEDIUM
    static final int HIGH_AT = 8;
    static final int MEDIUM_AT = 3;
    static final int RECENT_YEARS = 3;

    /**
     * The rating. Key idea: only BUILDING & FIRE SAFETY cases earn points, because about half of
     * all city records are weeds and trash, which say little about whether an apartment is safe.
     * Unresolved and recent cases weigh more than fixed or old ones.
     * Pure function of the list and today's date, so the same records always give the same rating.
     */
    static String riskLevel(List<ViolationItem> items, LocalDate today) {
        if (items.isEmpty()) return "UNKNOWN"; // no records != safe!
        int score = score(items, today);
        if (score >= HIGH_AT) return "HIGH";
        if (score >= MEDIUM_AT) return "MEDIUM";
        return "LOW";
    }

    static int score(List<ViolationItem> items, LocalDate today) {
        String cutoff = today.minusYears(RECENT_YEARS).toString();   // ISO dates compare correctly as text
        int points = 0;
        int resolvedPoints = 0;
        for (ViolationItem v : items) {
            if (!Categories.SAFETY.equals(Categories.bucket(v.code()))) continue;
            boolean recent = v.date() == null || v.date().compareTo(cutoff) >= 0;  // unknown date: assume recent
            if (looksOpen(v.status())) {
                points += recent ? POINTS_OPEN_RECENT : POINTS_OPEN_OLD;
            } else if (recent) {
                resolvedPoints += POINTS_RESOLVED_RECENT;
            }
        }
        return points + Math.min(resolvedPoints, MAX_RESOLVED_POINTS);
    }

    /** Template-based plain English. Keep it factual and careful. */
    static String summarize(int total, int open, int safety, int safetyOpen, String mostRecent) {
        if (total == 0) {
            return "We found no violation records for this address. That does not guarantee the "
                    + "building is problem-free: the address may not have matched, issues may not "
                    + "have been reported, or the address may be outside the City of Pittsburgh "
                    + "(our data only covers the city).";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("We found ").append(total).append(total == 1 ? " violation" : " violations");
        sb.append(", ").append(open).append(open == 1 ? " of which is" : " of which are").append(" still unresolved. ");
        if (safety == 0) {
            sb.append("None are building or fire safety issues.");
        } else {
            sb.append(safety).append(safety == 1 ? " is a building or fire safety issue" : " are building or fire safety issues");
            sb.append(" (").append(safetyOpen).append(" unresolved).");
        }
        if (mostRecent != null) sb.append(" The most recent was issued ").append(mostRecent).append(".");
        return sb.toString();
    }

    /** Groups violations into renter-friendly categories (see Categories.java). */
    static List<CategoryCount> categorize(List<ViolationItem> items) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (ViolationItem v : items) {
            counts.merge(Categories.bucket(v.code()), 1, Integer::sum);
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
                new ViolationItem("2025-06-02", "Fire Safety System Issue", "SAMPLE: smoke detector missing",
                        "In Violation", "SAMPLE-1", null, null),
                new ViolationItem("2024-11-15", "Building Maintenance", "SAMPLE: broken stair railing",
                        "Closed", "SAMPLE-2", "2024-12-20", null),
                new ViolationItem("2023-03-09", "Weeds/Debris", "SAMPLE: overgrown yard",
                        "Closed", "SAMPLE-3", "2023-04-01", null));
        return new Report("123 Example St (SAMPLE)", 3, 1, "2025-06-02", "MEDIUM",
                summarize(3, 1, 2, 1, "2025-06-02"),
                categorize(items), items,
                "SAMPLE DATA - not a real address. Used only for building the interface.",
                Questions.forRecords(items));
    }
}
