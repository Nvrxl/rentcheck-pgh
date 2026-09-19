# RentCheck PGH

Type a Pittsburgh address, get a plain-English report on its code-violation history
before you sign a lease. Built at SteelHacks XIII (Sept 19-20, 2026) by Michael and Connor.

Data: City of Pittsburgh violations dataset via WPRDC (https://data.wprdc.org).
**No language models are used in the app itself** (see "Hackathon rules" below).

## Run it

You need JDK 21 and Maven (`java -version` and `mvn -v` should both work).

```bash
mvn compile exec:java
```

Then open http://localhost:7070

- Click "See a sample report" to check the page works with fake data.
- http://localhost:7070/api/debug/fields shows the real column names in the city data.
  (The very first job is making `Fields.java` match them.)
- Change the port with `PORT=8080 mvn compile exec:java`.

## Where things are

| Path | What it is | Owner |
|---|---|---|
| `src/main/java/pgh/rentcheck/App.java` | web server + routes | Michael |
| `.../WprdcClient.java` | talks to the city data API | Michael |
| `.../ReportService.java` | the logic: matching, scoring, summaries | Michael |
| `.../Fields.java` | city column names | Michael |
| `.../Models.java` | JSON shapes sent to the page (the contract) | **both** |
| `src/main/resources/public/*` | the web page (HTML/CSS/JS) | Connor |
| `docs/TASKS.md` | who does what, in what order | both |
| `docs/API_CONTRACT.md` | what the backend promises the frontend | both |
| `CLAUDE.md` | instructions for our AI assistants | both |
| `AI_USAGE.md` | log of AI help (we must credit it) | both |

## Hackathon rules we are following

- Team of 1-4 college students. Submission due **11:00 AM ET, Sunday Sept 20** on Devpost.
- **No Wrapper track:** no language models in the finished project (classical ML is OK).
  Using AI to help *write* the code is allowed, and we credit it in `AI_USAGE.md` and on Devpost.
  So: no calls to OpenAI/Gemini/Anthropic/etc. from the app, and no LLM libraries.
- Tracks we are aiming at: No Wrapper, Xtract (signal in too much information), Cold Start
  (beginner hack), Seed Round (could be a company). MLH extras that don't need an LLM:
  Tiger Data, DigitalOcean.
- Everything on Devpost must be honest about what is built vs. planned.

Still to confirm with organizers (ask at the help desk / Discord):
1. Can one project enter more than one theme track?
2. Rules on pre-existing code/templates (this starter was generated during the event, and the
   log is in `AI_USAGE.md`).
3. Does text-to-speech (ElevenLabs) count as a "language model" for No Wrapper? (We're avoiding it.)
4. Demo/judging time slot.

## Honest limitations (put these on Devpost too)

- A violation record is not proof of a bad landlord or an unsafe building.
- No records found does not mean a building is fine (address may not match, issues go unreported).
- The city's rental registry is voluntary and not public, so we don't use it.
