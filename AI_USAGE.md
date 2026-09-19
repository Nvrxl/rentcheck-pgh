# AI usage log

SteelHacks allows AI help with writing code as long as it is credited. This file is our record.
The finished app itself uses **no language models** (No Wrapper track requirement): report text is
built from templates and all logic is ordinary code.

## Tools used
- Claude (Anthropic): brainstorming, competition research, project scaffolding, code generation, debugging.

## Rules for ourselves
- Add a row when AI writes or substantially changes something.
- We read and understand everything we ship. If we can't explain it to a judge, we simplify it.

## Log

Times are approximate (we write them down after the fact); the Git history has the exact commit times.

| When (ET) | Who | Tool | What we asked | What we used / changed |
|---|---|---|---|---|
| Sat Sep 19, earlier in the afternoon (approx.) | Michael | Claude | Idea brainstorming; researched competition; picked "Look Before You Rent" for Pittsburgh | Chose the idea and tracks |
| Sat Sep 19, earlier in the afternoon (approx.) | Michael | Claude | Generate the starter project (Javalin backend, city data client, sample frontend, docs, task split) | Whole starter: `pom.xml`, `src/main/**`, `README.md`, `CLAUDE.md`, `docs/*`. Not yet compiled by Claude (no Maven access in its sandbox); we test and fix. |
| Sat Sep 19, earlier in the afternoon (approx.) | Michael | Claude | Fix the app to use the city dataset's real column names (pasted /api/debug/fields output); better address matching | `Fields.java`, new `AddressNormalizer.java` (tested in isolation), `WprdcClient.java` (address-only search, `/api/debug/distinct`), `ReportService.java` (only count real violations), `index.html` header |
| Sat Sep 19, earlier in the afternoon (approx.) | Michael | Claude | Pasted real investigation_outcome counts; asked for exact rule for what counts as a violation | `ReportService.java`: allow-list of violation outcomes, rule for rows with no outcome |
| Sat Sep 19, earlier in the afternoon (approx.) | Michael | Claude | Pasted real status and case_file_type counts; asked to use them | New `Categories.java` (keyword buckets, tested against the real case types), `ReportService.java`: exact open statuses, safety-focused risk rating, category buckets |
| Sat Sep 19, earlier in the afternoon (approx.) | Michael | Claude | Pasted raw city rows for 1231 Lakewood St; asked to fix over-counting | `ReportService.java`: group rows by case number so one case = one violation; `Models.java`: new fields caseId/resolvedDate/codeSection; tested against the real rows |
| Sat Sep 19, ~3:30 PM | Michael | Claude | Wanted test addresses that show MEDIUM/HIGH risk ratings | `WprdcClient.java` + `App.java`: `/api/debug/hotspots` helper. The first version used the city's SQL endpoint and got HTTP 403, so Claude rewrote it to use the plain search and count cases in Java |
| Sat Sep 19, ~3:55 PM | Michael | Claude | Tested real addresses; 3619 Forbes Ave (a restaurant with a paperwork case and stale 2020 court cases) was rated HIGH, which looked wrong | `ReportService.java`: points-based rating (recent open = 3, old open = 1, resolved recent = 1 capped at 4; HIGH at 8, MEDIUM at 3), unit-tested on 12 cases. New `docs/HOW_WE_SCORE.md` |
| Sat Sep 19, ~4:30 PM | Michael | Claude | Replace the draft "How we score" text; make the page dark by default with a Light/Dark button | `index.html` (score panel, button), `style.css` (dark default, light theme), new `theme.js` (tested in a headless browser) |
| Sat Sep 19, ~4:45 PM | Michael | Claude | Neighborhood ranking and "near you" rentals links (crime ranking not possible: the city's crime feed stopped in Nov 2023) | New `NeighborhoodService.java` (counting, ranking, nearest neighborhood, unit-tested on made-up data), `WprdcClient.java` (open-cases and population fetchers), `App.java` (`/api/neighborhoods`, `/api/neighborhoods/near`, `/api/debug/population`), `docs/API_CONTRACT.md` |
| | | | | |
