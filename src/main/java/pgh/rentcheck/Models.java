package pgh.rentcheck;

import java.util.List;

/**
 * The JSON shapes the backend sends to the frontend.
 * THIS IS THE CONTRACT between the two of you. If you change a field name here,
 * tell the other person (and update docs/API_CONTRACT.md) or the page will break.
 */
public final class Models {
    private Models() {}

    public record Report(
            String query,             // what the user typed
            int totalViolations,      // how many violation records we found
            int openViolations,       // how many look unresolved
            String mostRecent,        // date of newest violation, or null
            String riskLevel,         // "LOW" | "MEDIUM" | "HIGH" | "UNKNOWN"
            String summary,           // plain-English sentence(s) for renters
            List<CategoryCount> categories,
            List<ViolationItem> violations,
            String note               // extra warning shown in the UI (e.g. "SAMPLE DATA")
    ) {}

    public record CategoryCount(String name, int count) {}

    public record ViolationItem(String date, String code, String description, String status) {}
}
