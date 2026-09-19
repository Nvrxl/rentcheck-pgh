# Instructions for AI assistants working on RentCheck PGH

Paste this whole file into your Claude Project instructions (claude.ai), or keep it in the repo
root if you use Claude Code. It is the shared memory for both teammates' chats.

## Who you're helping
Two beginner Java programmers at a 24-hour hackathon (SteelHacks XIII, deadline 11:00 AM ET
Sunday Sept 20, 2026): **Michael** (backend/data) and **Connor** (frontend/product/pitch).
Their roles can swap. If you don't know which one you're talking to, ask.
They are "vibe coding": they want working code fast, but they must be able to explain
what the code does to judges. So: explain each change in 2-3 plain sentences, keep the
code simple, and never leave them with code they can't run.

## The project
A web app: user types a Pittsburgh address -> we look up the city's code-violation records
(WPRDC open data, CKAN API, no key needed) -> we show a plain-English risk report.
Stack: Java 21, Maven, Javalin (web), Jackson (JSON), vanilla HTML/CSS/JS frontend.
Run: `mvn compile exec:java` -> http://localhost:7070

## HARD RULES (do not break these)
1. **No language models in the app.** The No Wrapper track forbids any LLM in the finished
   project. Never add calls to OpenAI/Gemini/Anthropic/Nemotron etc., never add LLM libraries,
   and generate report text from templates. Classical algorithms and classical ML are fine.
   (Using YOU to write the code is fine.)
2. **Log AI help in YOUR OWN file.** After a substantial contribution, remind the user to add a
   row to their own log: Michael -> `AI_USAGE.md`, Connor -> `AI_USAGE_CONNOR.md` (tool, what was
   asked, what was used). Separate files on purpose, so the two of them never edit the same file.
   Offer a ready-to-paste row.
3. **Stay in your teammate's lane.** See "How we avoid stepping on each other" below.
   Only edit files the user you're helping owns. `Models.java` and `docs/API_CONTRACT.md` are
   shared: if you change the JSON shape, say so loudly and tell the user to notify the other
   person and update the contract doc.
4. **No secrets in git.** No API keys in code. Use environment variables.
5. **Be honest in the product.** Never present a violation as proof of a bad landlord.
   "No records found" must never be presented as "safe". Sample data must stay labelled SAMPLE.
6. **Don't invent facts about the data.** Column names in `Fields.java` are verified; other
   assumptions are not. Check `/api/debug/fields`, `/api/debug/rows?address=...` and
   `/api/debug/distinct?field=...` before writing logic on them. If you're unsure whether
   something exists, say so.

## Working style
- Small changes, one file or feature at a time, each runnable. Prefer standard library and
  the existing dependencies; ask before adding a new one.
- Keep classes short and named plainly. Comments should explain *why*.
- After changing code, tell the user exactly how to test it (a URL to open or a command).
- Git: commit small and often; `git pull --rebase` before `git push`. Since the two of them
  work in different folders, they should rarely conflict.
- Priorities in order: (1) a demo that works end to end, (2) the explainable "hard part"
  (address matching, scoring, owner matching), (3) polish. Cut scope before breaking the demo.
  "Small and working beats big and broken."
- The deadline is real. If something is taking more than ~45 minutes, suggest a simpler path.

## How we avoid stepping on each other
**Who owns what** (only edit your own files):

| Owner | Files |
|---|---|
| Michael | `src/main/java/**` (except `Models.java`), `pom.xml`, `AI_USAGE.md` |
| Connor | `src/main/resources/public/**` (`index.html`, `style.css`, `app.js`, any images), `AI_USAGE_CONNOR.md`, the Devpost writeup and pitch |
| Shared (rarely edit, tell the other person first) | `Models.java`, `docs/API_CONTRACT.md`, `docs/TASKS.md`, `README.md`, `CLAUDE.md` |

**Habits**
- Before starting work: GitHub Desktop -> Fetch origin -> Pull origin. After each finished chunk:
  Commit -> Push origin. Push rejected? Pull first, then push again.
- Need a change in the other person's file? Don't make it. Message them, or add a line to `docs/TASKS.md`.
- The JSON contract: ADDING a field is safe (the page ignores unknown fields). Renaming or removing
  one needs agreement first, because it breaks the other person's code.
- Don't reformat, rename or move files you don't own. Never run "Reformat/Optimize imports on the
  whole project" in IntelliJ.
- Never commit `target/`, `.idea/` or secrets (already in `.gitignore`).
- If GitHub Desktop reports a merge CONFLICT, stop. Don't click through blindly: ask the other person.
- Connor can build the whole page against `/api/report/sample` (fake data, same shape as real data),
  so he is never blocked waiting for the backend.

## Facts about the data (verified against the real city data, Sat Sep 19 2026)
- The city stores SEVERAL ROWS PER CASE (an inspection row, a re-inspection row, a detail row). The
  backend merges them: each item in `violations` is ONE CASE, and `totalViolations` counts cases.
- Each violation has `date` (issued), `resolvedDate` (or null), `caseId`, `codeSection` (or null),
  and `code` (which currently holds the case type, e.g. "Refuse or Recycling Violations").
- Roughly half of all city records are weeds/trash/junk; only about a quarter are building & fire
  safety. So the risk rating is driven by building & fire safety problems only (`Categories.java`).
- Statuses that mean unresolved: In Violation, In Court, Clean & Lien, Appealed.
- Neighborhood ranking (`NeighborhoodService.java`) ranks unresolved building/fire safety cases from the last
  3 years per 1,000 residents (2020 Census table from WPRDC). It is about housing conditions, NOT crime: the city's
  open police-blotter data stopped updating in Nov 2023 and its replacement is a dashboard with no data download,
  so never build or promise a "live crime ranking". Case counts include commercial and mixed-use buildings.
- The county's open property-assessment data excludes owner names (county ordinance), so "owner matching" is not possible.
- The city's 311 open data stopped updating in Feb 2025. Don't use it.
- We don't scrape or list rentals. "Rentals near you" only shows search links to other sites (`rentalLinks`).
- The data only covers the City of Pittsburgh. "No records" can mean a clean address, a typo, or an
  address outside the city: never present it as "safe".

## Read these first
`README.md`, `docs/TASKS.md` (who does what), `docs/API_CONTRACT.md`.
