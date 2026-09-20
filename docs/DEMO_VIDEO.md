# Demo video: script and shot list

Target **2 minutes**, hard stop at 3. Screen recording with a voice-over is fine - no faces needed.
Record it EARLY. A rough video that exists beats a polished one that does not.

## Before you hit record

- Start the server and **wait two minutes** so the neighbourhood data has finished loading, or the
  Neighborhoods tab will show "still loading" on camera.
- Open every address once beforehand so nothing is being fetched cold during the take.
- Close notifications. Hide bookmarks. Full-screen the browser.
- Decide dark or light and stay there.
- On a Mac: Cmd+Shift+5 records the screen. QuickTime also works.

## Addresses to use

| Purpose | Address | What it shows |
|---|---|---|
| The clean one | 1231 Lakewood St | LOW concern, one closed trash case |
| The typo | 1231 Lakewud St | "Did you mean" catching a misspelling |
| The one that needs context | 3619 Forbes Ave | Pitt-owned building with a restaurant in it |
| The serious one | 508 Shadyhill Rd | HIGH concern, several unresolved safety cases |

**Do not** call any real property or landlord bad on camera. Let the records speak and keep the
narration factual - this is the same rule the app follows.

---

## Script

**0:00-0:15 - The problem.** *(Show the plain search box.)*

> "Every year thousands of Pitt students sign leases on buildings they know nothing about. The city
> already knows a lot about those buildings - code violations, condemnations, whether anyone has
> pulled a permit to fix the place. It is public. It is just spread across five datasets that nobody
> reads. So we built this."

**0:15-0:45 - It works.** *(Type 508 Shadyhill Rd. Let it load. Scroll slowly.)*

> "Type an address. RentCheck pulls the city's code-violation records and gives you a plain-English
> answer. High concern - seven violations, three unresolved, all building or fire safety."
>
> "The city stores several rows per case, so one violation can look like three. We group them by
> case number, so this is seven real cases, not twenty-one rows."

**0:45-1:15 - The part that makes it honest.** *(Search 3619 Forbes Ave. Point at "About this
building".)*

> "Here is why raw violation counts mislead. This address looked alarming - unresolved fire-safety
> cases. But pull in county property records and it is a university-owned building with a restaurant
> in it. Those cases belong to a commercial kitchen, not somebody's apartment. Our report says so."
>
> *(Scroll to permits.)*
>
> "And violations only tell you what went wrong. Permits tell you what got fixed. We show them side
> by side, because five violations with five permits and five violations with none are very
> different landlords."

**1:15-1:35 - The typo.** *(Type 1231 Lakewud St deliberately.)*

> "If we find nothing, that is dangerous - a typo looks exactly like a clean building. So we compare
> what you typed against every address in the city with that house number, using edit distance, and
> offer the closest matches. No AI involved; it is a string algorithm from the 1960s."

**1:35-1:50 - Neighbourhoods.** *(Click the Neighborhoods tab.)*

> "You can also go the other way - unresolved safety cases per thousand residents, filtered to what
> is within walking distance of your campus. This measures housing conditions from inspection
> records. It is not crime data, and we say so on the page."

**1:50-2:00 - Close.** *(Scroll to the "as of" dates.)*

> "Every number carries the date of the source it came from, because half the city datasets we
> looked at had quietly stopped updating. No language models anywhere in the app - every sentence is
> a template filled by arithmetic we can explain. That is RentCheck PGH."

---

## If you have a spare thirty seconds

The strongest thing you can say, and it is true: *"We checked seven public datasets tonight. Three
had stopped updating, one had become a dashboard, and one published a spreadsheet pivot table where
a dataset should be. The reason renters cannot check a building is not that the information does not
exist - it is that it rots quietly. So we put a date on everything."*

## Backup plan

Record a second take on a phone pointed at the screen, and export a PDF report using the "Print or
save as PDF" button. If the wifi dies during judging, you still have something to show.
