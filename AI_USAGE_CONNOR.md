# AI usage log: Connor

SteelHacks allows AI help with writing code as long as it is credited. Michael keeps his own log in
`AI_USAGE.md`; this file is Connor's, so we never edit the same file. Both are linked from the
Devpost submission.

The finished app itself uses **no language models** (No Wrapper track requirement).

## Tools used
- Claude (Anthropic, via the Claude desktop app): writing and testing frontend code (HTML/CSS/vanilla JS), accessibility checks, drafting the pitch doc, and explaining code so we can present it. Claude tested its changes in a headless browser against fake data shaped like docs/API_CONTRACT.md; Connor tested against the real server.

## Rules for ourselves
- Add a row when AI writes or substantially changes something.
- We read and understand everything we ship. If we can't explain it to a judge, we simplify it.

## Log

| When (ET) | Who | Tool | What we asked | What we used / changed |
|---|---|---|---|---|
| Sat Sep 19, ~3:15 PM | Connor | Claude | Explain the three frontend files and how the page gets its data; add loading/empty/error states, null-safe rendering, mobile layout, SAMPLE banner, ?address= links | `index.html`, `app.js`, `style.css`; plain-English risk labels; "No records found" styled so it never looks safe |
| Sat Sep 19, ~3:25 PM | Connor | Claude | Draft the Devpost writeup, 2-minute demo script and Seed Round paragraph | New `docs/PITCH.md` (TODO/CHECK marks for us to fill in and verify) |
| Sat Sep 19, ~3:35 PM | Connor | Claude | Violation timeline (issued/resolved dates, case ID, code section); "Also check" links (Google Maps, Google reviews, Reddit) | `app.js` (niceDate, timelineCell, renderAlsoCheck with encodeURIComponent), `index.html`, `style.css` |
| Sat Sep 19, ~3:40 PM | Connor | Claude | No-results box that says "does not mean the building is safe"; "How we score" placeholder; visual polish; clickable example addresses | `index.html`, `app.js`, `style.css` (Michael later replaced the scoring text with the final wording) |
| Sat Sep 19, ~3:50 PM | Connor | Claude | Faded Three Sisters-style bridge as a fixed background | New `bridge.svg` (original line drawing), `style.css` |
| Sat Sep 19, ~3:55 PM | Connor | Claude | Picture of the building | Google Maps satellite embed by address; chose this over copying listing photos (terms of use/copyright) |
| Sat Sep 19, ~4:00 PM | Connor | Claude | Accessibility audit (screen readers, low vision) | Search label, results heading + focus, live announcements, table roles, "opens in a new tab" text, AA-contrast colors, reduced-motion spinner |
| Sat Sep 19, ~4:05 PM | Connor | Claude | Demo-safety fixes | 25 s search timeout, ignore stale answers from double searches, sample link as a real button, tab icon |
| Sat Sep 19, ~4:45 PM | Connor | Claude | Questions card; Neighborhoods tab; Rentals near you tab | `api.js` (retry while the server loads), `neighborhoods.html/.js` (per 1,000 residents, not crime, not ranked list), `rentals.html/.js` (geolocation, rental search links only) |
| Sat Sep 19, ~4:55 PM | Connor | Claude | Print / save as PDF; neighborhood rank line on the report | `@media print` layout in `style.css`, print handler + renderNeighborhood in `app.js`, proposed `neighborhood` field in `docs/API_CONTRACT.md` |
| Sat Sep 19, ~5:05 PM | Connor | Claude | Sun, birds and animated cars on the bridge | New `bridge-animated.svg`, `sky.svg`; footer "Pause background animation" in `theme.js`; still bridge for reduced motion |
| Sat Sep 19, ~5:15 PM | Connor | Claude | "About this building" card from county data; reorder report | `renderProperty` (type not countyClass, context warning, owner type only, never owner names) |
| Sat Sep 19, ~6:15 PM | Connor | Claude | Mixed vehicles on the bridge; walk-to-campus directions; fix crash on shared ?address= links | Regenerated `bridge-animated.svg`; SCHOOLS list with verified addresses + Google Maps walking link; moved `latestRequest` above first use |
| Sat Sep 19, ~6:30 PM | Connor | Claude | Night scene in dark mode | New `sky-night.svg` (moon, no birds), `bridge-animated-night.svg` (headlights); day/night follows the Light/Dark button |
| Sat Sep 19, ~10:15 PM | Connor | Claude | Work through docs/CONNOR_CATCHUP.md: show the new report fields and the campus filter | Condemned alert, did-you-mean suggestions, building permits / permitsNote, "Around this address" (311), "Where this data comes from"; "Near my campus" filter on the Neighborhoods tab; filled in this log (times from our commit history, approximate) |
