# How we score an address

RentCheck's rating is a small, fixed set of rules written in code
(`ReportService.java`, method `riskLevel` / `score`). **No AI or language model is involved.**
The same city records always give the same rating. (If you change a rule in the code,
change it here too. Connor uses this page for the "How we score this" panel on the site.)

## What counts

1. We look up the address in the City of Pittsburgh's code-violation records (WPRDC open data).
2. The city stores several rows per case (inspection, re-inspection, details), so we group them
   and count **cases**, not rows.
3. A case counts as a violation if the city found one. Cases that were voided, cancelled, marked
   "no violation found", or sent to another department are left out.
4. Each case is sorted into a category. Only **building and fire safety** cases earn points.
   About half of all city records are trash, weeds and junk outside a property, and they say
   little about whether an apartment is safe to live in. They are still listed in the report.

## Points

Each building or fire safety case earns points:

| Case | Points |
|---|---|
| Unresolved, issued in the last 3 years | 3 |
| Unresolved, but issued more than 3 years ago | 1 |
| Resolved, issued in the last 3 years | 1 (all together capped at 4) |
| Resolved and older than 3 years | 0 |

"Unresolved" means the city status is In Violation, In Court, Clean & Lien, or Appealed.

Why older unresolved cases count less: cases can sit at "In Court" for years, and we can't tell
from the data whether an old one is still a real problem. Why resolved cases are capped: a
property with a history of fixed problems can reach MEDIUM but never HIGH from history alone.

## Rating

| Total points | Rating |
|---|---|
| No records found | UNKNOWN (never treated as "safe") |
| 0 to 2 | LOW |
| 3 to 7 | MEDIUM |
| 8 or more | HIGH |

Examples: one recent unresolved fire-safety case is 3 points (MEDIUM). Three recent unresolved
cases is 9 points (HIGH). An address with only a fixed trash violation is 0 points (LOW).

## Limits, and what we do NOT claim

- A violation is **not** proof of a bad landlord. Some are paperwork (for example "testing and
  inspection records needed"), some were fixed quickly, and big buildings collect more cases.
- "No records found" does **not** mean safe. The address may not have matched, problems may not
  have been reported, or it may be outside the City of Pittsburgh (our data covers only the city).
- We can't see owners, unreported problems, or anything that happened after the city's latest update.
- The thresholds (3 / 1 / 4 / 8 / 3 years) are our own judgment, tuned by checking real addresses.
  They are a starting point for a renter's own questions, not a verdict.

## Values used in the code

`POINTS_OPEN_RECENT = 3`, `POINTS_OPEN_OLD = 1`, `POINTS_RESOLVED_RECENT = 1`,
`MAX_RESOLVED_POINTS = 4`, `MEDIUM_AT = 3`, `HIGH_AT = 8`, `RECENT_YEARS = 3`.
