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
  "property": {
    "parcelId": "0028B00142000000",
    "type": "University or college property",
    "countyClass": "GOVERNMENT",
    "residential": false,
    "use": "OWNED BY COLLEGE/UNIV/ACADEMY",
    "yearBuilt": null, "stories": null, "bedrooms": null, "fullBaths": null, "livingAreaSqFt": null,
    "ownerType": "a company",
    "ownerOccupied": null,
    "conditionRating": null,
    "parcelsAtAddress": 1,
    "matchedBy": "address",
    "context": "The county lists this as university or college property for tax purposes, not as housing...",
    "note": "From Allegheny County assessment records..."
  },
  "location": { "latitude": 40.4406, "longitude": -79.9959, "source": "city violation records",
                "note": "Position comes from city violation records, not from the address itself..." },
  "requests311": {
    "neighborhood": "Central Oakland", "total": 300, "recent": 214, "open": 37, "asOf": "2026-09-19",
    "topTypes": [ { "type": "Potholes", "count": 41 } ],
    "items": [ { "date": "2026-09-18", "type": "Street Light", "status": "Open" } ],
    "summary": "Residents made 300 311 requests in Central Oakland...",
    "note": "311 requests are things RESIDENTS reported to the city in Central Oakland, not problems with this particular building..."
  },
  "dataAsOf": [ { "source": "City building permits", "covers": "June 2019 to now", "asOf": "2025-04-02",
                  "note": "Plumbing permits are issued by the county and are not included." } ],
  "condemned": null,
  "permits": {
    "total": 3, "recent": 2, "totalProjectValue": 41500, "mostRecent": "2025-04-02",
    "items": [ { "date": "2025-04-02", "type": "Building", "workType": "Existing (alteration/addition)",
                 "description": "REPLACE ROOF", "projectValue": 14000, "status": "Completed" } ],
    "summary": "The city has issued 3 building permits here since June 2019...",
    "note": "City building permits only, and only since June 2019..."
  },
  "permitsNote": null,
  "suggestions": [ { "address": "1231 LAKEWOOD ST", "similarityPercent": 87 } ],
  "neighborhood": { "name": "Central Oakland", "rank": 12, "per1000": 0.5, "openRecent": 3, "rankedBy": "per1000" },
  "questions": [
    "The city lists 1 building or fire safety case here that looks unresolved. Ask what has been done about it, when it will be fixed, and get the answer in writing.",
    "How do I report a repair, and how quickly are repairs usually done? Can I see the most recent inspection report?"
  ]
}
```

- `questions` (NEW) is a list of 3 to 6 plain-English "questions to ask before you sign", written from templates and chosen by what the city records mention (open cases first; a closing "how are repairs handled" question always last). Show it as a checklist card, e.g. "Questions to ask before you sign". It can be an empty list on old servers, so handle `undefined`. Add a line under it like "A starting point, not an accusation: the questions don't say anyone did anything wrong."
- `property` (NEW) is what **Allegheny County** records about the building, or `null` when nothing matched (a different dataset from the city violations, so it often misses; the page must handle `null`). Show it as an "About this building" card.
  - `type` is a plain-English label made for the page; `countyClass` is the county's own word. Show `type`, not `countyClass`: the county files a university building under "GOVERNMENT", which would read as nonsense to a renter.
  - **If `context` is not null, show it.** It means the address is not ordinary housing (commercial, university, etc.), which explains why violation counts there can look alarming. This is the fix for addresses like 3619 Forbes Ave (a Pitt-owned building with a restaurant in it).
  - `yearBuilt`, `stories`, `bedrooms`, `fullBaths`, `livingAreaSqFt` and `conditionRating` are filled in **only for residential parcels**, so expect nulls elsewhere.
  - `parcelsAtAddress` > 1 means the building is assessed unit by unit (condos/apartments); room counts are omitted then, because they would describe one unit.
  - `ownerType` is "an individual" or "a company". **There are no owner names** anywhere: the county excludes them by ordinance. Never imply we know who the landlord is.
  - `ownerOccupied` is `true` or `null`, never `false`. `true` means someone claimed the homestead tax reduction, which only applies to a home the owner lives in. `null` means unknown, NOT "it is a rental".
  - Always show `note` in small text under the card.
- `neighborhood` (NEW, this is Connor's proposed field, built exactly as proposed) says where the address sits on the Neighborhoods ranking, or `null`. `name` is the city's own neighborhood for the matched records (the most common one, since a corner address can be tagged differently on different cases). `rank`, `per1000`, `openRecent` and `rankedBy` are copied from `/api/neighborhoods`, and any of them can be `null` — while the ranking is still loading, or for a neighborhood that can't be ranked, you get the name and nulls. The report never waits for the ranking to load.
- `suggestions` (NEW) is "did you mean?": up to 5 real addresses from the city's data that look close to what was typed, best first. **It is only ever non-empty when `totalViolations` is 0**, so show it in the empty state, above or instead of "no records found". Each entry is a normalized address you can put straight back into the search box (`?address=...`). `similarityPercent` never reaches 100, because an exact match would not be a suggestion. If it is empty, keep the existing "no records" wording.
- **`condemned`** (NEW) is almost always `null`. When it is not, the city has declared the property **unfit for occupancy** - show `warning` prominently, above everything else, in a red/alert style. It is the most serious thing the report can say. Also show `note` (a condemnation can be lifted and the list can lag). `ownerNotFound` is only true when the city specifically could not find the owner; the live data usually uses one combined label, so it is normally false. **The source dataset contains owner names and we deliberately do not expose them** - do not go looking for them.
- **`permits`** (NEW) is the city's building-permit history for the property, or `null` when there are none. This is the counterweight to the violations list: violations are what went wrong, permits are what was fixed properly. Show `summary`, the `items` table, and `note`. `totalProjectValue` can be `null`.
- **`permitsNote`** (NEW) is set *instead of* `permits` when there are none, and is already worded carefully (small repairs need no permit, plumbing permits are issued by the county). Show it as-is; do not write your own version, and do not present "no permits" as proof of neglect.
- **`requests311`** (NEW) is what residents reported to 311 in this address's **neighbourhood**, or `null`. Show it as "Around this address" and keep the framing: it describes the street, not the building. Always show `note`. `open` can be `null` (we only report it when the dataset actually has a status column). Source is the CURRENT 311 system, March 2025 onwards, published four times a day.
- **`dataAsOf`** (NEW) lists every dataset behind the report with what it covers and the newest record we saw, so the page can show honest "as of" dates. `asOf` can be `null`. Put this at the foot of the report. Half the public datasets in this city have quietly stopped updating, so being explicit here is a feature, not boilerplate.
- **`location`** (NEW) is the building's coordinates for the map, or `null`. It comes from whichever dataset we hold that has a position for this address (violations first, then permits), and `source` says which. **We never invent a position: no data, no pin** - so an address with no records anywhere has `location: null` and the map must simply not appear. Coordinates outside greater Pittsburgh, and the 0/0 the city uses for "unknown", are rejected. Show `note`: a position from a case record can refer to the block rather than the building.
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

## GET /api/schools
The universities we know about and the travel modes, so the page can build the "near my campus" filter.

```json
{
  "schools": [ { "id": "pitt", "name": "University of Pittsburgh", "shortName": "Pitt",
                 "latitude": 40.4446, "longitude": -79.9533,
                 "housingLinks": [ { "label": "Pitt Off-Campus Housing Marketplace", "url": "https://...", "note": "..." } ] } ],
  "travelModes": [ { "id": "walk", "label": "Walking", "defaultMiles": 1.0, "note": "About a 20 minute walk on flat ground..." } ],
  "note": "Campus coordinates are a single point... all distances are straight-line..."
}
```

- Currently Pitt, CMU and Duquesne. Each `housingLinks` entry is that university's OWN housing office.
- Use `travelModes` to fill the mode picker; `defaultMiles` is the starting radius for that mode, which the student can change.
- Show `note` near the filter. These are straight-line distances, not walking distances, and Pittsburgh's hills and rivers make that a real difference.

## GET /api/neighborhoods?school=<id>&mode=<id>  (or &maxMiles=<number>)
Same endpoint as above, filtered to neighborhoods near a campus. Without `school` the response is exactly as before, so nothing breaks.

With a school, each entry gains **`distanceMiles`** and **`housingLinks`** (that school's office first), the list is sorted **closest first instead of by rank**, and the response gains:

```json
{
  "filter": { "school": "Pitt", "schoolId": "pitt", "mode": "walk", "modeLabel": "Walking",
              "modeNote": "About a 20 minute walk...", "maxMiles": 1.0, "matched": 4, "ofTotal": 87 },
  "distanceNote": "Straight-line distance from the middle of Pitt, not walking distance...",
  "neighborhoods": [ { "name": "Central Oakland", "distanceMiles": 0.06, "rank": 12, "per1000": 8.2, "...": "..." } ]
}
```

- `mode` sets the radius from `travelModes`; `maxMiles` overrides it (0 to 50). Neither one given = every neighborhood, still sorted by distance.
- **Neighborhoods with no coordinates are left out entirely** when a school is chosen, because we can't honestly say how far away they are. Say so if `matched` is well below `ofTotal`.
- `rank` is still the citywide rank, so a filtered list will have gaps in the ranks. That is correct, not a bug.
- Always show `distanceNote`. Errors: `400` for an unknown school or a bad `maxMiles`.

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
