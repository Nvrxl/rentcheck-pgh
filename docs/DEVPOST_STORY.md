## Inspiration

Most students see an apartment for fifteen minutes, then sign a lease for a year. The things that would actually tell you about a building, like code violations, condemnations and whether anyone ever pulled a permit to fix it, are already public in Pittsburgh. But they're spread across separate city and county datasets, written in inspector shorthand, and several have quietly stopped updating without saying so. Nobody checks them before signing. We wanted a renter to be able to type an address and get a plain-English answer in seconds.

## What it does

Type a Pittsburgh address and **RentCheck PGH** gives you:

- a **rating** (Low, Some or High concern) driven only by building and fire safety cases
- the full **case history**, with issued and resolved dates
- **About this building** from county assessment records (for example, "this is university property, not housing")
- **building permits**, meaning what was actually fixed with a permit
- a **condemnation warning** if the city has declared the place unfit to live in
- **questions to ask before you sign**, chosen from what the records mention, plus a print/PDF version to bring to a viewing
- **"Did you mean…?"** when nothing matches, so a typo never looks like a clean record
- an **"as of" date on every data source**

Two more tabs rank neighborhoods by unresolved safety cases per 1,000 residents (filterable by distance from your campus), and find the neighborhoods near you with links to rental searches.

## How we built it

**Java 21 + Javalin + Jackson** on the back end, **vanilla HTML/CSS/JavaScript** on the front end. No frameworks, no database, no API keys, and **no language model anywhere in the app**. Every sentence is a template filled in by plain arithmetic. We split the work over a written API contract (`docs/API_CONTRACT.md`): Michael built the data engine and Connor built the pages, so we rarely touched the same file.

**Merging the data.** The city stores several rows per violation case (inspection, re-inspection, details), so we group rows by case number and count *cases*, not rows. Only cases where a violation was actually found count.

**The rating is a points system, not a count.** For building and fire safety cases, where "recent" means issued in the last 3 years:

$$
S = 3\,n_{\text{open, recent}} \;+\; 1\,n_{\text{open, older}} \;+\; \min\!\left(n_{\text{fixed, recent}},\ 4\right)
$$

$$
\text{rating} =
\begin{cases}
\text{Low} & S \le 2 \\
\text{Some concern} & 3 \le S \le 7 \\
\text{High concern} & S \ge 8
\end{cases}
$$

Old unresolved cases count less because cases can sit "In Court" for years. Fixed cases are capped so that history alone can never reach High. No records at all gives "No records found", never "safe".

**"Did you mean?"** uses Levenshtein edit distance, adjusted for house numbers, because 1231 and 1233 on the same street are different buildings, not typos:

$$
\text{sim}(a,b) = \min\!\left(0.99,\ \max\!\left(0,\ 1 - \frac{\operatorname{lev}(a,b)}{\max(|a|,|b|)} + \beta\right)\right),
\qquad
\beta =
\begin{cases}
+0.15 & \text{same house number} \\
-0.25 & \text{different house number} \\
0 & \text{otherwise}
\end{cases}
$$

Suggestions below 0.62 are dropped, and a score never reaches 100%, because a suggestion is by definition not an exact match.

**Neighborhood ranking** uses 2020 Census population, and only for neighborhoods with at least 1,000 residents, since tiny ones produce wild rates:

$$
r = \frac{1000 \times \text{unresolved safety cases issued in the last 3 years}}{\text{population}}
$$

**Distance to campus** is straight-line (haversine), and the page says so:

$$
d = 2R \arcsin\sqrt{\sin^2\frac{\Delta\varphi}{2} + \cos\varphi_1 \cos\varphi_2 \sin^2\frac{\Delta\lambda}{2}}, \qquad R = 6371\ \text{km}
$$

On the front end, every field in the contract can be `null`, so each section hides itself when there's nothing honest to show. All data goes into the page as text, never as HTML. We also did an accessibility pass: labels, screen-reader announcements, keyboard focus, WCAG AA contrast in light and dark mode, and a pause button plus reduced-motion support for the animated bridge background.

## Challenges we ran into

- **A lot of the city's open data is quietly out of date.** The police blotter stopped in 2023, the old 311 archive in February 2025, and fire incidents in August 2025. The county's supermarket list is from 2016. The dataset that replaced the crime blotter turned out to be an Excel pivot table. We checked each one, wrote down why we rejected it, and put an "as of" date on everything we kept. That's why there's no live crime ranking: there's no live crime data to rank.
- **Addresses are messy.** "1231 Lakewood Street" vs "1231 LAKEWOOD ST, Pittsburgh, PA 15220-". We normalize both sides and match on word boundaries so "123 Lake" never matches "1231 Lakewood".
- **Tests passed while features were broken.** Our unit tests for "did you mean" passed against invented rows but failed on the city's real data. One real search found the bug. The condemned-property data uses one combined label for two categories, which would have wrongly told every renter the city couldn't find the owner. We now test against real rows.
- **Being honest is harder than being impressive.** Every number had a version that overstated it: a violation isn't proof of a bad landlord, "no permits" isn't proof of neglect, 311 complaints describe the street and not the building, and a straight-line mile in Pittsburgh can be a 25-minute walk up a hill. The county deliberately excludes owner names, so we never imply we know who the landlord is.
- **We're beginner Java programmers** building against a 24-hour clock, with two people working in parallel.

## What we learned

The hard part of a data project isn't fetching the data, it's refusing to overstate it. We learned to check real column names and values before writing logic on top of them, to treat "no data" as its own answer instead of a zero, and to put a date on every number. We also learned that a written API contract is what let two beginners build a backend and a frontend at the same time without constantly breaking each other's work. And accessibility isn't an extra: a label, a focus move and a contrast fix take minutes and make the site usable for many more people.
