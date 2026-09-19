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
2. **Log AI help.** After a substantial contribution, remind the user to add a row to
   `AI_USAGE.md` (tool, what was asked, what was used). Offer a ready-to-paste row.
3. **Stay in your teammate's lane.** Backend files (`App`, `WprdcClient`, `ReportService`,
   `Fields`) belong to Michael. Everything in `src/main/resources/public/` belongs to Connor.
   `Models.java` and `docs/API_CONTRACT.md` are shared: if you change the JSON shape, say so
   loudly and tell the user to notify the other person and update the contract doc.
4. **No secrets in git.** No API keys in code. Use environment variables.
5. **Be honest in the product.** Never present a violation as proof of a bad landlord.
   "No records found" must never be presented as "safe". Sample data must stay labelled SAMPLE.
6. **Don't invent facts about the data.** The real column names and status values are not yet
   verified (see `Fields.java`). Check `/api/debug/fields` output before writing logic on them.
   If you're unsure whether something exists, say so.

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

## Read these first
`README.md`, `docs/TASKS.md` (who does what), `docs/API_CONTRACT.md`.
