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
            String note               // extra warning shown in the UI (e.g. "SAMPLE DATA")
    ) {}

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
