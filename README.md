# RentCheck PGH

Type a Pittsburgh address, get a plain-English report on its code-violation history
before you sign a lease. Built at SteelHacks XIII (Sept 19-20, 2026).

## Team

| Name | Email |
|---|---|
| Michael Plofchan | mvp88@pitt.edu |
| Connor Readinger | Readinger.connor@gmail.com (registered with SteelHacks); Cir213@pitt.edu |

Both University of Pittsburgh students.

Data: City of Pittsburgh violations dataset via WPRDC (https://data.wprdc.org).
**No language models are used in the app itself** (see "Hackathon rules" below).

## Run it

You need JDK 21 and Maven (`java -version` and `mvn -v` should both work).

```bash
mvn compile exec:java
```

Then open http://localhost:7070

- Click "See a sample report" to check the page works with fake data.
- Change the port with `PORT=8080 mvn compile exec:java`.
- `mvn package` builds one runnable file, `target/rentcheck.jar`. That is what the `Dockerfile`
  runs when the app is hosted.

### What the app serves

| Page | What it does |
|---|---|
| `/` | search an address, get the report |
| `/neighborhoods.html` | neighborhoods ranked by unresolved building & fire safety cases |
| `/rentals.html` | nearest neighborhoods to your location, plus rental search links |
| `/api/report?address=` | the report as JSON (see `docs/API_CONTRACT.md`) |
| `/api/neighborhoods`, `/api/neighborhoods/near` | the ranking data |
| `/api/health` | is it alive, when did it start, what can it do |
| `/api/debug/*` | developer pages we used to check real column names before writing code |

The neighborhood ranking downloads city data in the background when the server starts, so those
pages answer "still loading" for about a minute after a fresh start.

## Where things are

| Path | What it is | Owner |
|---|---|---|
| `src/main/java/pgh/rentcheck/App.java` | web server + routes | Michael |
| `.../WprdcClient.java` | talks to the city and county data APIs | Michael |
| `.../ReportService.java` | the logic: matching, scoring, summaries | Michael |
| `.../NeighborhoodService.java` | neighborhood ranking and "near me" | Michael |
| `.../PropertyService.java` | county building facts (type, year built, owner type) | Michael |
| `.../Questions.java` | "questions to ask before you sign", from templates | Michael |
| `.../Categories.java`, `.../AddressNormalizer.java`, `.../Fields.java` | buckets, address matching, column names | Michael |
| `.../Models.java` | JSON shapes sent to the page (the contract) | **both** |
| `src/main/resources/public/*` | the web page (HTML/CSS/JS) | Connor |
| `Dockerfile` | how a hosting service builds and runs the app | Michael |
| `docs/TASKS.md` | who does what, in what order | both |
| `docs/HOW_WE_SCORE.md` | the rating rules in plain English | both |
| `docs/API_CONTRACT.md` | what the backend promises the frontend | both |
| `CLAUDE.md` | instructions for our AI assistants | both |
| `AI_USAGE.md` | log of AI help, Michael's | Michael |
| `AI_USAGE_CONNOR.md` | log of AI help, Connor's | Connor |

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
