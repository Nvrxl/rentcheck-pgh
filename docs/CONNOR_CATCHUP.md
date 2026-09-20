# Connor catch-up

Running notes for when Michael and Connor sync in person. Newest section at the top.
Michael keeps this file; Connor can read it after a pull.

---

## Sat Sep 19, evening sync

### 1. Submission requirements we are NOT meeting yet (most important)

From the SteelHacks XIII Devpost page. These are requirements, not suggestions.

- **README must list both team members' full names and email addresses.** Ours does not. Whoever
  edits it, do it once and tell the other person (README is a shared file).
- **A demo video or pitch slides must be submitted** (Google Drive, Canva, Figma or PowerPoint link).
  We have neither. This is the thing most likely to get left until 9 AM, so pick an owner tonight.
- Deadline is **11:00 AM EDT Sunday Sep 20**. Code freeze 8 AM, submit by 10 AM.

### 2. Connor's AI usage log is empty

`AI_USAGE_CONNOR.md` still has no rows. Michael's `AI_USAGE.md` has a full log. SteelHacks allows
AI help as long as it is credited, and judges may read these. A few honest rows beat a perfect one.

### 3. Backend fields - DONE, Connor already wired these up

`property` ("About this building"), `neighborhood` (rank line + link) and `questions` are all
rendered in `app.js` / `index.html` as of Connor's evening commits. Nothing outstanding here.
Details still in `docs/API_CONTRACT.md` if anything looks off.

### 4. Wording rules we agreed (they matter for judging, not just taste)

- The Neighborhoods tab is **housing conditions from city inspection records, not crime**. Title it
  "Unresolved building & fire safety cases per 1,000 residents". Never "worst" or "safest".
- Always show the `note` from the API under the ranking, and each `rateNote` for unranked entries.
- "No records found" is never presented as "safe".
- A violation is never presented as proof of a bad landlord.

### 5. Things we established are NOT possible (so nobody spends time on them)

- **Live crime ranking.** The city's open police blotter stopped updating 14 Nov 2023. Its
  replacement is a dashboard with no data download.
- **Owner names / "this landlord owns N buildings".** Allegheny County excludes owner names from
  the open property data by ordinance. We only get owner *type* (individual vs company).
- **Scraped rental listings.** Against those sites' terms. "Rentals near you" shows search links only.
- **311 complaints.** That dataset stopped updating Feb 2025.

### 6. Deploy status

- `Dockerfile` and a fat-jar build are in the repo, so the app can be deployed from GitHub.
- Not deployed yet. Michael is working on it.
- The MLH prize is **"Best Use of DigitalOcean"** (a retro wireless mouse per team member).
  Qualifying means running on DigitalOcean (App Platform fits our Dockerfile).

### 7. To decide at the sync

- Who owns the demo video, and when is it being recorded?
- Which address do we demo? We want one LOW, one HIGH, and the Forbes Ave example (a Pitt-owned
  building with a restaurant in it) because it shows why the county data matters.
- Do we use a real property as a "bad" example on screen? Default answer: no.
- Which tracks are we entering, and has anyone confirmed with organizers whether we can enter more
  than one?

### 8. NEW IDEA: getting around and everyday access (transit, POGOH, groceries)

Michael's idea: show what is near an address - bus stops, POGOH bike share, grocery stores - since
that is what actually decides whether a student can live somewhere without a car. Researched Sat
evening; here is what the data really looks like before anyone starts building.

**Blocker to solve first.** We have no coordinates for an address unless the city has a violation
record there (those rows carry latitude/longitude). The county property data has NO lat/long at all.
So "nearest bus stop" silently fails on clean addresses - exactly the ones a renter is happiest
about. Options: only show it when we have coordinates and say so, or add a geocoder (another
external service, more risk).

| Idea | Data | Verdict |
|---|---|---|
| Nearest bus stops + routes | PRT Transit Stops (WPRDC), updated quarterly, GeoJSON resource `d6e6ed6e-9220-4a0e-9796-e72d83ce8e7a` | **Best candidate.** GeoJSON parses with Jackson, which we already use. |
| Nearest POGOH bike station | POGOH Station Locations (WPRDC), current as of May 2026, has name, docks, lat/long | **Maybe.** Published as XLSX, which Java can't read without a new library. Check whether the CKAN datastore is queryable first. |
| Nearest grocery store | Allegheny County Supermarkets & Convenience Stores (WPRDC) | **Don't.** It is 2016 data. Telling a renter a shop is nearby when it closed years ago is the kind of wrong we have avoided all project. |
| Pitt shuttle / dining / labs via PittAPI | `github.com/pittcsc/PittAPI` | **Can't.** It is an unofficial Python library, not a REST API, and our app is Java. No time to add Python. |

**If we build it:** frame it as "Getting around" with a data-as-of date, distances in miles, and no
score. It must not feed the risk rating - that rating is about building safety, and mixing in
"far from a bus" would muddy the one thing we can defend to judges.

### 9. NEW IDEA: "how far from campus", filtered by how you get around

Michael's follow-up: let a student pick their school and how they travel (walk / bike / bus / car),
and filter places by how far they are from campus.

**This one fits our existing data better than the transit idea.** The Neighborhoods tab already
works out where each neighborhood is (we take the coordinates off the city's case records), so a
"within X miles of campus" filter needs almost nothing new. The travel mode really just picks a
sensible default radius: walking about 1 mile, biking about 3, bus or car about 6. Student can
override it.

**Where to get campus coordinates without making numbers up.** Don't hardcode lat/long from memory.
Cleanest trick: run a campus building's street address through our own lookup - the city's records
carry latitude and longitude - and use that. Failing that, take the coordinates off a published
source and write down where they came from.

**The honest caveat, which is also a good demo line.** We can only measure straight-line distance.
This is Pittsburgh: hills, rivers and bridges mean a half-mile straight line can be a 25-minute
walk, or impossible without crossing the Mon. Real walking and biking times need a routing service
(an API key, another outside dependency, probably not worth it tonight). So label it clearly -
"straight-line distance, not walking time" - rather than implying we know the walk.

**Scope check.** Mode + radius filter on the Neighborhoods tab is small. Per-address distance to
campus hits the same coordinate blocker as section 8: no coordinates for an address unless the city
has a violation record there.

### 12. CORRECTION: 311 data is NOT dead (shared-file change, Connor please note)

Our `CLAUDE.md` said the city's 311 data stopped in Feb 2025 and not to use it. That was wrong and
it has been corrected in `CLAUDE.md` - a shared file, hence this note.

Only the OLD archive stopped. The city moved to a new 311 system on 4 Feb 2025 and publishes
**Pittsburgh 311 Data** (`pittsburgh-311-data`, resource `5202679a-d243-402e-b82a-63189995a942`),
covering March 2025 to today, updated **four times a day**. Verified Sat Sep 19 2026.

The catch, and it is a real one: the city withholds the exact location of certain complaint types
for privacy. Those rows can never go on a map or into a "near this address" list, and we do not
guess where they were.

### 13. Two new report fields, live now (pull first)

- **`condemned`** - almost always null. When it is not, the city has declared the property unfit to
  live in. Show `warning` at the top of the report in an alert style; it is the most serious thing
  we can say. The source dataset contains owner names and we deliberately do not expose them.
- **`permits`** / **`permitsNote`** - building permits since June 2019, with project values,
  shown against the violation count. Violations are what went wrong; permits are what was fixed
  properly. When there are no permits, `permitsNote` is already worded carefully (small repairs need
  no permit, plumbing permits come from the county) - show it as written, do not rephrase it into
  "this landlord does nothing".

Shapes in `docs/API_CONTRACT.md`.

### 14. Crime data: checked again, still not usable (closed)

Michael pushed back on the earlier "crime data is dead" note, correctly - it was too strong. There IS
a successor dataset, **Monthly Criminal Activity** (WPRDC), incident-level NIBRS, Jan 2024 to Apr 2026.

We queried it. The CKAN datastore is active, but the ingested content is an Excel **pivot table**, not
incidents: 42 rows, headers reading "Row Labels", "Column Labels", "Grand Total", plus two unnamed
columns. The real data is presumably on another sheet of the workbook that was never ingested, so we
would have to download and parse XLSX (a new Java library) to get at ~5-month-old, block-level data.
Not worth it before the freeze. **Closed unless someone finds a different source.**

Worth keeping for the pitch though: of the public datasets we checked tonight - police blotter, 311
archive, fire incidents, county grocery list, crime dashboard - three had quietly stopped updating,
one became a dashboard, and one publishes a pivot table where a dataset should be. The reason renters
cannot easily check a building is not that the information does not exist; it is that it is scattered
across sources that decay silently. RentCheck stitches five live ones together and puts a date on each.

