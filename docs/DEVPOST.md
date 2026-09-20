# Devpost submission text

Draft, ready to paste and edit. Written Sat Sep 19 2026 and accurate to what is actually built -
if a feature is cut before the freeze, cut its line here too. **Nothing in this file should claim
something the app does not do.**

---

## Tagline

Look up any Pittsburgh address and find out what the city already knows about it - before you sign.

---

## Inspection / The problem

Renting in Pittsburgh means trusting a stranger's description of a building you will live in for a
year. The information that would actually tell you something - code violations, condemnations,
whether anyone has pulled a permit to fix the place - is public. It is also scattered across five
different city and county datasets, in formats nobody reads for fun, and some of them quietly
stopped updating years ago without saying so.

We are two Pitt students. Between us we have signed leases on places we knew nothing about. So we
built the thing we wanted: type an address, get a plain-English answer.

## What it does

**Type a Pittsburgh address.** RentCheck pulls together five live public sources and gives you:

- **A rating** - LOW, SOME or HIGH concern - driven only by building and fire safety cases, with the
  points arithmetic shown on the page so you can disagree with it.
- **The full case history**, merged properly: the city stores several rows per case, so a single
  violation can look like three. We group by case number and count cases.
- **About this building** - type, year built, size, from county assessment records. This is what
  tells you that an address with three fire-safety cases is a restaurant, not an apartment.
- **A condemnation warning** if the city has declared the property unfit to live in.
- **What has been fixed** - building permits since 2019 with their declared values, shown right next
  to the violation count, because "five violations and five permits" and "five violations and no
  permits" are very different landlords.
- **Around this address** - what residents have reported to 311 in that neighbourhood.
- **Questions to ask before you sign**, chosen from what the records actually mention.
- **"Did you mean...?"** when an address finds nothing, so a typo never masquerades as a clean record.
- **A neighbourhood ranking** by unresolved safety cases per 1,000 residents, filterable by how far
  you are willing to be from your campus and how you get around.
- **An "as of" date on every single source.**

## How we built it

Java 21, Javalin, Jackson. A vanilla HTML/CSS/JS front end. No frameworks, no database, no API keys.

The interesting work is not the plumbing, it is deciding what the data actually means:

- **Address matching.** People type "1231 Lakewood Street", the city stores "1231 LAKEWOOD ST,
  Pittsburgh, PA 15220-". We normalise both, then verify the match on a word boundary so "123 Lake"
  never matches "1231 Lakewood".
- **"Did you mean" uses Levenshtein edit distance** with a house-number weighting, because 1231 and
  1233 on the same street are different buildings, not typos of each other. When the street name
  itself is misspelled, searching for it finds nothing - so we fall back to searching the house
  number alone and comparing spellings against every address in the city with that number.
- **Scoring** is a points system, not a count. An unresolved safety case from this year is worth
  three points; one from 2019 is worth one, because cases sit in court for years; a fixed case is
  worth one, capped, so history alone can never reach "high concern".
- **Joining five datasets** on the county parcel id, which is the only identifier they share.

## Challenges we ran into

**Most of this city's open data is quietly broken, and finding that out took the whole night.**
The police blotter stopped in 2023. The old 311 archive stopped in February 2025. The fire incidents
stopped in August 2025. The county's supermarket list is from 2016. The crime "dataset" that replaced
the blotter turned out to be an Excel pivot table - 42 rows with headers reading "Row Labels" and
"Grand Total". We checked each one and wrote down why we rejected it.

We also got things wrong and caught them. Our unit tests passed while "did you mean" was completely
broken, because we tested it against invented rows instead of what the city actually returns; one
real search found the bug in seconds. The condemned-property data documents two categories but uses
a single combined label for both, so our first version would have told every renter that the city
could not find the owner. We only noticed because we tested against real rows.

## What we learned

That the hard part of a data project is not fetching the data, it is refusing to overstate it. Every
number on our page had a version where it said something we could not actually support.

## What's next

Median days-to-close per neighbourhood. Reopened-after-closed detection. A map of bus stops, bike
share and food shops around a property. Renter-submitted structured tips - fixed answers only, never
free text, and only shown once several people agree.

---

## Track notes

**No Wrapper.** There is no language model anywhere in the finished app. Every sentence a user reads
is a template filled by plain arithmetic. The rating, the categories, the questions, the address
matching and the suggestions are all classical algorithms we can explain line by line. We used AI to
help write the code and credited every session in `AI_USAGE.md` and `AI_USAGE_CONNOR.md`.

**Xtract - signal in too much information.** Our input is roughly 640,000 city violation rows,
585,000 county parcels, 65,000 permits and a live 311 feed. Our output is one sentence and a rating.
The extraction is the product: merging multi-row cases into single events, separating trash from fire
safety, and joining five datasets on a parcel id.

**Cold Start.** Both of us are first-time hackers and beginner Java programmers. We started at 2 PM
Saturday with an empty folder.

**Seed Round.** The buyer is not the renter. Universities and off-campus housing offices already
field "is this place OK?" questions with no data behind the answer; Pitt alone advises thousands of
off-campus students. Tenant organisations and legal aid need the same lookup. A city-by-city rollout
works wherever there is open code-enforcement data, which is most large US cities.

## Honest limitations (keep these on the submission)

- A violation is not proof of a bad landlord. Some are paperwork; some were fixed the same week.
- "No records found" does not mean safe. The address may not have matched, problems may go
  unreported, or the property may be outside the City of Pittsburgh.
- We only cover the City of Pittsburgh, not the surrounding boroughs.
- We do not know who owns anything. The county excludes owner names from its open data by ordinance,
  and the one dataset that does contain them we deliberately do not display.
- Distances are straight-line. In Pittsburgh that is not walking distance.
- Our scoring thresholds are our own judgement, published on the page so you can argue with them.
