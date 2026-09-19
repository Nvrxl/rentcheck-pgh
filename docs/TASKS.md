# Task split

Sat Sept 19 ~2 PM start -> **submit by 10 AM Sun (hard deadline 11 AM ET)**.
Michael and Connor can swap anything. The point is that you're rarely editing the same file.
Tick boxes as you go and commit this file so the other person (and their AI chat) sees progress.

## Together, first hour
- [ ] Both: install JDK 21 + Maven + an editor (IntelliJ Community is easiest). Check `java -version`, `mvn -v`.
- [ ] One person creates a GitHub repo, pushes this folder, adds the other as a collaborator.
- [ ] Both: clone, run `mvn compile exec:java`, open http://localhost:7070, click "See a sample report".
- [ ] Both: read `docs/API_CONTRACT.md` (5 minutes). This is what lets you work in parallel.

## Michael: backend and data (the "hard part" judges will ask about)
Goal: make `/api/report?address=...` return honest, accurate results.

1. [x] Real column names are now in `Fields.java` (verified Sat 9/19). [ ] Still to do: open `/api/debug/distinct?field=status` and `?field=investigation_outcome` to see the real values, then make `looksOpen()` and `isViolation()` exact instead of guesses.
2. [~] **Address matching.** (First draft done: `AddressNormalizer.java` + search on the address column only. Still needs: unit numbers, misspellings, "did you mean", parcel-id matching.) Right now we do a loose text search, so "4200 Forbes" can match neighbors. Normalize input ("Avenue"/"Ave", "Street"/"St", case, unit numbers) and match on the address field or parcel id instead. *This is your best explainable algorithm.*
3. [ ] **Scoring.** Replace the placeholder `riskLevel()` with a documented formula: weight by severity, decay by age (a 2015 violation matters less), open vs closed. Write the formula in `docs/HOW_WE_SCORE.md` in plain English (Connor shows it in the UI).
4. [~] **Categories.** (First draft done in `Categories.java`: real case types sorted into 6 groups. Review the rules.) Group violation codes into human categories (fire safety, plumbing, structural, pests, ...) instead of raw code strings.
5. [ ] Add lat/long to the report (if the data has it) for Connor's map. Update the contract first!
6. [ ] Stretch: **owner matching.** Pull county property assessments (WPRDC) to find the owner, then group owners whose names differ slightly ("ABC HOLDINGS LLC" vs "ABC HLDGS") with fuzzy matching. Show "this owner also has N other properties with M violations".
7. [ ] Stretch (MLH Tiger Data prize): load the data into Postgres for fast lookups and cache it.

## Connor: frontend, product and pitch
Goal: a page a renter understands in 5 seconds, plus a story judges remember.

1. [ ] Work against `/api/report/sample` so you're never blocked by the backend.
2. [ ] Polish `index.html`/`style.css`: empty state, loading state, error state, mobile layout.
3. [ ] Add a "How we score this" panel (text from Michael's `HOW_WE_SCORE.md`). Judges on the No Wrapper track want to understand the hard part.
4. [ ] Map with Leaflet + OpenStreetMap showing the building (needs lat/long from Michael).
5. [ ] Address autocomplete or "did you mean...?" if the search finds nothing.
6. [ ] **Devpost writeup**: problem, how it works, what's hard, honest limits, AI credit, what's next.
7. [ ] **Seed Round angle** (one slide/paragraph): who pays (universities, off-campus housing offices, tenant groups, property managers who want to look transparent)? Talk to one real Pitt student renter and put their quote in.
8. [ ] Demo script (2 minutes) + screenshots + a backup video of the demo in case wifi dies.

## Together, last stretch
- [ ] Deploy (DigitalOcean is an MLH prize) or at least a reliable local demo.
- [ ] **Code freeze at 8 AM Sunday.** Only bug fixes after that.
- [ ] Submit on Devpost by 10 AM. Tick every track you're entering. List AI tools used.
- [ ] Final `AI_USAGE.md` check.

## Suggested checkpoints
| Time | Target |
|---|---|
| Sat 4 PM | Both running locally; real data coming back for at least one address |
| Sat 9 PM | Matching + scoring v1; page polished on sample data |
| Sun 12 AM | Full flow works on real addresses. Start sleeping in shifts. |
| Sun 8 AM | Code freeze |
| Sun 10 AM | Submitted |
