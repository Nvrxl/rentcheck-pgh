# What is left, and who does it

Written Sat Sep 19 ~11 PM. **Code freeze 8 AM Sunday. Submit by 10 AM. Hard deadline 11:00 AM EDT.**

Rule for the rest of the night: anything in "Must" beats anything in "Should", which beats anything
in "Stretch". A feature that is half-built at 7 AM is worse than not starting it.

---

## MUST - without these there is no submission

| # | What | Who | Notes |
|---|---|---|---|
| 1 | **Push everything currently uncommitted** | Michael | 11 files sitting on his Mac. Connor cannot see any of tonight's work until this happens. |
| 2 | **Demo video or pitch slides** | **Connor** | A Devpost requirement, not a nice-to-have. Script and shot list ready in `docs/DEMO_VIDEO.md`. Record it EARLY - a rough one that exists beats a polished one that does not. |
| 3 | **Create and submit the Devpost entry** | **Connor** drafts, either submits | Text ready in `docs/DEVPOST.md`. Needs: GitHub link, video/slides link, tick every track. |
| 4 | **Connor's AI usage log** | **Connor** | `AI_USAGE_CONNOR.md` is still empty. A few honest rows. Judges may read it. |
| 5 | **`mvn package` must succeed** | Michael | Never been run. If the jar build is broken we find out now, not during a deploy at 3 AM. |

Already done: README has both names and emails (a Devpost requirement).

## SHOULD - these are what the scores actually turn on

| # | What | Who | Notes |
|---|---|---|---|
| 6 | **Wire the new report fields into the page** | **Connor** | `condemned`, `permits`, `permitsNote`, `requests311`, `dataAsOf`, `location`, `suggestions`. All shapes in `docs/API_CONTRACT.md`; wording rules in `docs/CONNOR_CATCHUP.md`. The condemned warning is the most important thing the app can say - it belongs at the top in an alert style. |
| 7 | **Deploy to DigitalOcean** | Michael | Required for the "Best Use of DigitalOcean" prize, and a live link helps every other track. `Dockerfile` is ready. Blocked only on adding a payment method; the $5 signup credit covers a weekend. |
| 8 | **Verify the 311 column detection** | Michael | Open `/api/debug/311` and check `detectedColumns` against `allColumns`. The code discovers column names at run time, so this is the one thing standing between "probably right" and "checked". |
| 9 | **Test 5-6 real addresses end to end** | either | Watch the clock on each report: it now makes five or six calls to the city. If a report takes more than ~4 seconds, say so - a slow demo is a bad demo. |
| 10 | **Ask an organiser three questions** | either | Can one project enter multiple tracks? Is judging expo-style or presentations, and when? What does the DigitalOcean prize require? All three change what we prepare. |

## STRETCH - only if everything above is done

| # | What | Who |
|---|---|---|
| 11 | Map pins for bus stops / POGOH / food shops. Engine is built and tested (`NearbyPlaces.java`); blocked on three `/api/debug/peek` checks | Michael, then Connor for the pins |
| 12 | Compare two addresses side by side - the strongest single demo moment we do not have | **Connor** |
| 13 | Median days-to-close, and reopened-after-closed | Michael |

---

## The short version for Connor

Pull first. Then, in this order:

1. Read `docs/CONNOR_CATCHUP.md` - it has everything that changed tonight and, importantly, the four
   things we proved are NOT possible so nobody re-opens them.
2. **Record the demo video** using `docs/DEMO_VIDEO.md`. This is the single highest-value thing
   anyone can do right now.
3. Draft the Devpost entry from `docs/DEVPOST.md`.
4. Add a few rows to `AI_USAGE_CONNOR.md`.
5. Then the new report fields on the page, `condemned` first.

Everything Connor needs is already written down. He is not blocked on Michael for any of it.
