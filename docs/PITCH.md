# Pitch pack: Devpost writeup, demo script, Seed Round angle

Owner: Connor. Draft written Sat Sep 19 evening. **Before submitting, re-check every line
against what actually works** (Devpost must be honest about built vs planned).
`[TODO]` = fill in. `[CHECK]` = true in the code today, but Michael may still change it.

---

## Devpost writeup

### Tagline (one line)
Type a Pittsburgh address, see its code-violation history in plain English before you sign a lease.

### Inspiration
Renters, and especially students signing their first lease near Pitt and CMU, usually see an
apartment once, for fifteen minutes, before committing to a year. The City of Pittsburgh
publishes every building-code investigation as open data, but it's hundreds of thousands of rows
of inspector jargon that nobody checks before signing. We wanted to turn that into something a
renter can read in five seconds.
[TODO: one real sentence/quote from a Pitt student renter goes here]

### What it does
- You type an address (e.g. "1231 Lakewood Street").
- RentCheck looks up that address in the city's code-violation records (WPRDC open data).
- It shows a plain-English report: how many records count as violations, how many look
  unresolved, the most recent date, the main categories, and the full list.
- It is careful about what the data can't say: "no records found" is shown as *no records*,
  never as "safe", and every report reminds you that a violation isn't proof of a bad landlord.

### How we built it
- **Backend:** Java 21 + Javalin. It calls the WPRDC CKAN API live (no API key) and turns raw
  records into a report. No database yet.
- **Frontend:** plain HTML/CSS/JavaScript: no framework, works on phones.
- **No language models anywhere in the app.** Every sentence in a report comes from a template,
  and every decision is ordinary code we can point to. (No Wrapper track.)

The interesting parts (the "hard part" we can explain line by line):
1. **Address matching** (`AddressNormalizer.java`). The city stores
   "1231 LAKEWOOD ST, Pittsburgh, PA 15220-" and people type "1231 Lakewood Street". We normalize
   both sides: upper-case, drop city/state/zip and unit numbers, standardize "Street"→"ST",
   "Avenue"→"AVE", "North"→"N" and so on. Then we match only at a word boundary, so "1231 LAKEWOOD"
   doesn't accidentally pull in "12310 LAKEWOOD".
2. **Deciding what counts as a violation** (`ReportService.java`). Most city records *aren't*
   violations: "No Violation Found", "Case Voided", "sent to another department". We counted
   the real outcome values across ~638,000 records and wrote an explicit allow-list
   (violation found, resolved, taken to court, lien, and so on). About a third of the records have no
   outcome at all; for those we count a record only if it names a specific violation.
3. **Categories** (`Categories.java`) [CHECK: wired into the report yet?]. The city uses ~150
   case types with near-duplicates ("Weeds/Debris", "Weeds and Debris"...). We sort them into 6
   buckets a renter understands: building & fire safety, vacant property, trash/weeds/junk,
   sidewalks/streets/utilities, zoning/permits, other.
4. **Risk rating** [CHECK: Michael is replacing the placeholder with a documented formula
   (severity, age, open vs closed). Describe the final version here, from `docs/HOW_WE_SCORE.md`.]

### Challenges we ran into
- The data didn't match our guesses: column names, status values and outcomes all had to be
  checked against the real dataset before we could write logic on them.
- Addresses are messy, and a loose text search matched neighbors' records. Being strict risks missing
  records, and being loose shows the wrong building. We chose strict and say so on the page.
- Being honest in the UI: it's easy to build something that makes landlords look bad or makes an
  empty result look like a clean bill of health. We designed the wording around that.
- [TODO: anything else that actually bit you tonight]

### Accomplishments we're proud of
- End-to-end on live city data, with no LLM, in 24 hours, as beginner Java programmers.
- Every number in a report can be traced to a rule we can explain.
- [TODO]

### What we learned
- [TODO: 2-3 honest bullets, e.g. working with a real open-data API, splitting work over an API contract]

### What's next
(Planned, **not built**. Keep this list honest.)
- Map of the building (Leaflet + OpenStreetMap).
- "Did you mean...?" when an address finds nothing; matching on parcel ID.
- Owner matching: link county property-assessment owners and group near-duplicate LLC names,
  so you can see "this owner has N other properties with M violations".
- Caching the dataset in Postgres for fast lookups.

### Honest limitations
- A violation record is not proof of a bad landlord or an unsafe building.
- No records found does not mean a building is fine: the address may not match, or problems
  may never have been reported.
- Pittsburgh's rental registry isn't public, so we can't tell which buildings are rentals.
- City data can lag behind real life.

### Use of AI
We used Claude (Anthropic) to help write code, docs and this writeup; every contribution is logged
in `AI_USAGE.md`. The app itself uses no language models.

### Built with
java, javalin, jackson, maven, html, css, javascript, ckan, wprdc, open-data
[TODO: add digitalocean if we deploy there]

---

## 2-minute demo script

Have these ready in browser tabs beforehand: the app, and a **backup video**.
[TODO: pick 2 real addresses tonight and test them: one with several violations, one with none.]

| Time | Say | Do |
|---|---|---|
| 0:00 | "You see an apartment for 15 minutes, then sign for a year. The city already knows if that building has a history of code violations, but it's buried in 600,000 rows of open data." | Title / app home page |
| 0:20 | "RentCheck turns that into a report a renter reads in five seconds." | Type address A, press Check |
| 0:35 | "Here's the summary, how many records look unresolved, what kinds of problems, and every record underneath." | Point at badge, stats, categories, table |
| 0:55 | "No AI in the app. Three pieces of plain logic do the work: address matching, deciding which of the city's outcomes are real violations, and grouping 150 case types into 6 categories." | Open "How we score this" (if built) or show the note line |
| 1:20 | "We're careful. Here's an address with nothing on file. We don't call it safe, we say *no records*, and why that can happen." | Search address B |
| 1:35 | "It works on a phone, because that's where renters are when they're touring." | Resize window / show phone |
| 1:45 | "Next: owner matching across properties, and a map. Universities' off-campus housing offices are our first customers." | Back to home |

Backup plan: if the city API is slow or down, click "See a sample report" (clearly labelled
SAMPLE) and play the backup video for the live part.

---

## Seed Round angle (one paragraph)

**Who pays:** university off-campus housing offices (Pitt, CMU, Duquesne, Chatham) that already
maintain housing lists and want to warn students about problem properties; tenant unions and
legal-aid groups that currently look this up by hand; and landlords with clean records who'd pay to
display a "RentCheck verified history" badge. It's cheap to run because the data is public and free,
and it expands city by city, since many US cities publish code-enforcement data in similar formats.
[TODO: one real renter quote, e.g. "I wish I'd known before I signed..." (ask someone tonight)]
