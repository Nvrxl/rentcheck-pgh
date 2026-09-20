package pgh.rentcheck;

import java.util.List;

/**
 * The JSON shapes the backend sends to the frontend.
 * THIS IS THE CONTRACT between the two of you. If you change a field name here,
 * tell the other person (and update docs/API_CONTRACT.md) or the page will break.
 * Adding NEW fields is safe (the page just ignores ones it doesn't know yet).
 */
public final class Models {
    private Models() {}

    public record Report(
            String query,             // what the user typed
            int totalViolations,      // how many violation CASES we found (not rows)
            int openViolations,       // how many look unresolved
            String mostRecent,        // date the newest violation was issued, or null
            String riskLevel,         // "LOW" | "MEDIUM" | "HIGH" | "UNKNOWN"
            String summary,           // plain-English sentence(s) for renters
            List<CategoryCount> categories,
            List<ViolationItem> violations,
            String note,              // extra warning shown in the UI (e.g. "SAMPLE DATA")
            List<String> questions,   // "questions to ask before you sign", chosen from templates
            PropertyService.PropertyFacts property,  // what the COUNTY knows about the building, or null
            ReportNeighborhood neighborhood,  // where this address sits on the neighborhood ranking, or null
            List<Suggestions.Suggestion> suggestions, // "did you mean?" - only when nothing was found
            Condemned.CondemnedRecord condemned,      // city says unfit to live in, or null (almost always null)
            Permits.PermitHistory permits,            // work permitted here since June 2019, or null
            String permitsNote,                       // shown when there are no permits at all
            Requests311.Summary requests311,          // 311 requests in this neighbourhood, or null
            List<SourceAsOf> dataAsOf,                // where every number came from, and how fresh it is
            Location location                         // where the building is, for the map, or null
    ) {}

    /**
     * Coordinates for the searched address. Null when no dataset we hold has a location for it.
     * `source` says which dataset it came from, because we never invent a position.
     */
    public record Location(double latitude, double longitude, String source, String note) {}

    /** One data source behind the report, so the page can show "as of" dates honestly. */
    public record SourceAsOf(String source, String covers, String asOf, String note) {}

    /**
     * Links a report to the Neighborhoods ranking ("In Central Oakland: rank 12").
     * rank/per1000 are null for neighborhoods we can't rank (see NeighborhoodService).
     */
    public record ReportNeighborhood(String name, Integer rank, Double per1000,
                                     Integer openRecent, String rankedBy) {}

    public record CategoryCount(String name, int count) {}

    /** One violation = one city case, even though the city stores several rows per case. */
    public record ViolationItem(
            String date,          // date the violation was issued (earliest inspection date)
            String code,          // the case type, e.g. "Refuse or Recycling Violations" (page shows it as "Type")
            String description,   // e.g. "TIRE LEFT IN FRONT YARD."
            String status,        // city status, e.g. "Closed", "In Violation", "In Court"
            String caseId,        // e.g. "CF-ES-2026-008155"
            String resolvedDate,  // date it was marked resolved, or null
            String codeSection    // legal code, e.g. "CITY CODE 619.06(A)", or null
    ) {}
}
