# API contract (backend -> frontend)

If you change anything here, change `Models.java` too, and **tell the other person**.

## GET /api/report?address=<text>

Response 200:

```json
{
  "query": "4200 Forbes Ave",
  "totalViolations": 3,
  "openViolations": 1,
  "mostRecent": "2025-06-02",
  "riskLevel": "MEDIUM",
  "summary": "We found 3 violation records, 1 of which looks unresolved. The most recent one is dated 2025-06-02.",
  "categories": [ { "name": "Fire safety", "count": 2 } ],
  "violations": [
    {
      "date": "2026-02-18",
      "code": "Refuse or Recycling Violations",
      "description": "TIRE LEFT IN FRONT YARD.",
      "status": "Closed",
      "caseId": "CF-ES-2026-008155",
      "resolvedDate": "2026-03-19",
      "codeSection": "CITY CODE 619.06(A)"
    }
  ],
  "note": "Shown under the report as a small warning."
}
```

- `riskLevel` is one of `LOW`, `MEDIUM`, `HIGH`, `UNKNOWN`. `UNKNOWN` means no records found (NOT "safe").
- Any field except `query` and the counts may be `null` or empty. The page must handle that.
- `violations` is newest first. **Each entry is one city CASE**, not one row: the city stores several rows per case (inspection, re-inspection, detail), and the backend merges them. `totalViolations` counts cases.
- `date` is when the violation was issued; `resolvedDate` is when it was marked resolved (or `null`). `codeSection` is the legal code (or `null`).
- In each violation, `code` currently holds the case type (e.g. "Refuse or Recycling Violations"), because the city's code-section column is often empty. The page labels this column "Type".

Errors: `400 {"error": "..."}` (missing address), `502 {"error": "..."}` (city data unreachable or a bug).

## GET /api/report/sample
Same shape, fake data labelled SAMPLE. Frontend can always use this.

## GET /api/debug/fields
Raw pass-through of one row from the city dataset. For developers only.

## Planned additions (add here when agreed, before coding them)
- `latitude` / `longitude` (numbers or null) for the map.
- `owner` block for owner matching.
