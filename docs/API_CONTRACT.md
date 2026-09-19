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
  "note": "Shown under the report as a small warning.",
  "questions": [
    "The city lists 1 building or fire safety case here that looks unresolved. Ask what has been done about it, when it will be fixed, and get the answer in writing.",
    "How do I report a repair, and how quickly are repairs usually done? Can I see the most recent inspection report?"
  ]
}
```

- `questions` (NEW) is a list of 3 to 6 plain-English "questions to ask before you sign", written from templates and chosen by what the city records mention (open cases first; a closing "how are repairs handled" question always last). Show it as a checklist card, e.g. "Questions to ask before you sign". It can be an empty list on old servers, so handle `undefined`. Add a line under it like "A starting point, not an accusation: the questions don't say anyone did anything wrong."
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

## GET /api/neighborhoods
Neighborhoods ranked by how many building and fire safety cases are **still unresolved and were issued in the last 3 years** (the same "recent" cut-off as the address rating). This is about **housing conditions from city inspection records, not crime**: the city's open crime data stopped updating in November 2023, so there is no live crime feed.

```json
{
  "asOf": "2026-09-19",
  "rankedBy": "per1000",
  "basis": "Building and fire safety cases that are still unresolved and were issued in the last 3 years",
  "rowsScanned": 53210,
  "truncated": false,
  "neighborhoods": [
    { "name": "Central Oakland", "openRecent": 12, "openOlder": 3, "addresses": 9,
      "population": 6100, "per1000": 2.0, "rank": 1, "rateNote": null }
  ],
  "note": "Shown small under the ranking."
}
```

- `rankedBy` is `"per1000"` (cases per 1,000 residents, from 2020 Census population) or `"count"` (raw number of cases, used only if we couldn't find a population for at least 90% of neighborhoods). **Show the ranking's unit from this field.**
- `population`, `per1000`, `rank` and `rateNote` can be `null`. In `per1000` mode, a neighborhood with no trustworthy rate has `per1000: null`, `rank: null` and a `rateNote` saying why (for example fewer than 1,000 residents, or the city's population table repeating one figure for two neighborhoods, which happens for Shadyside and Squirrel Hill South). List those at the bottom as "not ranked" and show the `rateNote`.
- The list is already sorted best-to-worst by rank. Show `note` under the ranking.
- Returns `503 {"status": "loading" | "error", "message": "..."}` while the city data is loading (about a minute after the server starts) or if the load failed. The page should show the message and retry every few seconds.
- The data is cached in the server and refreshed every 6 hours.

## GET /api/neighborhoods/near?lat=<number>&lon=<number>
The neighborhoods closest to a spot (e.g. the browser's location), closest first, with links to rental searches on other sites.

```json
{
  "asOf": "2026-09-19",
  "rankedBy": "per1000",
  "totalNeighborhoods": 88,
  "insideCity": true,
  "nearby": [
    { "name": "Central Oakland", "distanceKm": 0.07, "rank": 12, "openRecent": 3, "per1000": 0.5,
      "rentalLinks": [ { "label": "Pitt Off-Campus Housing Marketplace", "url": "https://...", "note": "Listings for University of Pittsburgh students" } ] }
  ],
  "note": "..."
}
```

- `nearby[0]` is the neighborhood the location is in (or nearest to). Up to 6 entries. `rank` and `per1000` can be `null`.
- `insideCity: false` means the location is more than 5 km from any city record: tell the user "You seem to be outside Pittsburgh".
- `rentalLinks[].note` can be `null`. These are just **search links to other websites**. We do not list, copy or verify rentals. Open them in a new tab (`target="_blank" rel="noopener noreferrer"`) and keep the note that says so.
- Errors: `400` (missing or bad lat/lon), `404` (no data), `503` (still loading, same as above).
- Use `navigator.geolocation` in the page to get lat/lon. It asks the user for permission and only works on `localhost` or `https`.

## GET /api/debug/*
Developer-only pages (`fields`, `rows`, `hotspots`, `distinct`, `population`). Not for the public page.

## Planned additions (add here when agreed, before coding them)
- `latitude` / `longitude` (numbers or null) in `/api/report`, for a map pin.
- Property facts (year built, commercial or residential) from county assessment data, if the columns check out.
- ~~`owner` block for owner matching~~: dropped. The county's open property data deliberately excludes owner names.
