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

| When (ET) | Who | Tool | What we asked | What we used / changed |
|---|---|---|---|---|
| Sat Sep 19, ~2 PM | Michael | Claude | Idea brainstorming; researched competition; picked "Look Before You Rent" for Pittsburgh | Chose the idea and tracks |
| Sat Sep 19, ~2 PM | Michael | Claude | Generate the starter project (Javalin backend, city data client, sample frontend, docs, task split) | Whole starter: `pom.xml`, `src/main/**`, `README.md`, `CLAUDE.md`, `docs/*`. Not yet compiled by Claude (no Maven access in its sandbox); we test and fix. |
| Sat Sep 19, ~4 PM | Michael | Claude | Fix the app to use the city dataset's real column names (pasted /api/debug/fields output); better address matching | `Fields.java`, new `AddressNormalizer.java` (tested in isolation), `WprdcClient.java` (address-only search, `/api/debug/distinct`), `ReportService.java` (only count real violations), `index.html` header |
| Sat Sep 19, ~4:30 PM | Michael | Claude | Pasted real investigation_outcome counts; asked for exact rule for what counts as a violation | `ReportService.java`: allow-list of violation outcomes, rule for rows with no outcome |
| Sat Sep 19, ~5 PM | Michael | Claude | Pasted real status and case_file_type counts; asked to use them | New `Categories.java` (keyword buckets, tested against the real case types), `ReportService.java`: exact open statuses, safety-focused risk rating, category buckets |
| Sat Sep 19, ~6:30 PM | Michael | Claude | Pasted raw city rows for 1231 Lakewood St; asked to fix over-counting | `ReportService.java`: group rows by case number so one case = one violation; `Models.java`: new fields caseId/resolvedDate/codeSection; tested against the real rows |
| | | | | |
