// Frontend logic. Talks to the Java backend at /api/report.
// The shape of the data it gets back is defined in docs/API_CONTRACT.md.
// The contract says almost any field may be null or empty, so every read below has a fallback.

const form = document.getElementById("search-form");
const input = document.getElementById("address");
const button = form.querySelector("button");
const statusEl = document.getElementById("status");
const reportEl = document.getElementById("report");

// The address the user actually typed. The "Also check" links are built from it.
// Stays null for the sample report, because that address is fake.
let searchedAddress = null;

// Each search gets a number. If you click a second search before the first answers,
// only the newest one is allowed to update the page (otherwise a slow old answer could
// show the report for the wrong address).
let latestRequest = 0;
const TIMEOUT_MS = 25000;   // give up after 25 s instead of spinning forever (demo safety)

// What renters see instead of the raw riskLevel codes.
// "UNKNOWN" means no records were found, which is NOT the same as safe.
const RISK_LABELS = {
  LOW: "Low concern",
  MEDIUM: "Some concern",
  HIGH: "High concern",
  UNKNOWN: "No records found",
};

form.addEventListener("submit", (e) => {
  e.preventDefault();
  search(input.value.trim());
});

// "Try: ..." example links: fill in the box and search, without reloading the page.
// (Each link also has a normal href, so it still works if JavaScript is slow to load.)
document.querySelectorAll("[data-address]").forEach((link) => {
  link.addEventListener("click", (e) => {
    e.preventDefault();
    input.value = link.dataset.address;
    search(link.dataset.address);
  });
});

function search(address) {
  if (!address) return;
  // Put the address in the page URL so a report can be refreshed or shared (handy for the demo).
  history.replaceState(null, "", `?address=${encodeURIComponent(address)}`);
  searchedAddress = address;
  load(`/api/report?address=${encodeURIComponent(address)}`);
}

document.getElementById("sample-link").addEventListener("click", (e) => {
  e.preventDefault();
  history.replaceState(null, "", "?");
  searchedAddress = null;
  load("/api/report/sample");
});

// If the page was opened with ?address=..., run that search right away.
const startAddress = new URLSearchParams(location.search).get("address");
if (startAddress) {
  input.value = startAddress;
  searchedAddress = startAddress;
  load(`/api/report?address=${encodeURIComponent(startAddress)}`);
}

async function load(url) {
  const requestId = ++latestRequest;
  setStatus("Searching city records... (this can take a few seconds)", "loading");
  reportEl.hidden = true;
  button.disabled = true;
  button.textContent = "Checking...";
  try {
    let res;
    try {
      res = await fetch(url, { signal: AbortSignal.timeout(TIMEOUT_MS) });
    } catch (e) {
      if (e.name === "TimeoutError") {
        throw new Error("The search took too long. The city's data service may be slow right now.");
      }
      throw new Error("Can't reach the RentCheck server. Is it running?");
    }
    // A crash can return an HTML error page instead of JSON, so don't assume JSON.
    const data = await res.json().catch(() => null);
    if (requestId !== latestRequest) return;   // a newer search started; ignore this old answer
    if (!res.ok || !data) {
      const detail = data && data.error;
      if (res.status === 502) {
        throw new Error("The city's data service didn't respond. Try again in a minute.");
      }
      throw new Error(detail || `Request failed (${res.status}).`);
    }
    render(data);
    // Say the result out loud for screen readers (the status area is a live region),
    // and move keyboard focus to the results, because the disabled button just lost it.
    setStatus(`Report loaded: ${RISK_LABELS[data.riskLevel] || "No records found"}, `
      + `${data.totalViolations ?? 0} violations.`, "done");
    document.getElementById("results-heading").focus();
  } catch (err) {
    if (requestId !== latestRequest) return;
    setStatus(`Something went wrong: ${err.message} You can search again, or try the sample report.`, "error");
    statusEl.focus();   // so keyboard and screen-reader users land on the error message
  } finally {
    if (requestId !== latestRequest) return;
    button.disabled = false;
    button.textContent = "Check";
  }
}

function setStatus(msg, kind = "") {
  statusEl.textContent = msg;
  statusEl.className = kind;
}

function render(r) {
  // textContent (not innerHTML) on purpose: never trust text that came from outside.
  const risk = RISK_LABELS[r.riskLevel] ? r.riskLevel : "UNKNOWN";
  const categories = Array.isArray(r.categories) ? r.categories : [];
  const violations = Array.isArray(r.violations) ? r.violations : [];

  const isSample = String(r.query || "").includes("SAMPLE") || String(r.note || "").includes("SAMPLE");
  document.getElementById("sample-banner").hidden = !isSample;
  document.getElementById("searched-for").textContent = r.query || "-";

  const badge = document.getElementById("risk-badge");
  badge.textContent = RISK_LABELS[risk];
  badge.className = `badge ${risk}`;
  document.getElementById("headline").className = `card headline risk-${risk}`;

  document.getElementById("summary").textContent = r.summary || "";
  document.getElementById("stat-total").textContent = r.totalViolations ?? 0;
  document.getElementById("stat-open").textContent = r.openViolations ?? 0;
  document.getElementById("stat-recent").textContent = niceDate(r.mostRecent) || r.mostRecent || "-";
  document.getElementById("note").textContent = r.note || "";

  const cats = document.getElementById("categories");
  cats.replaceChildren(...categories.map((c) => {
    const li = document.createElement("li");
    li.textContent = `${c.name || "Other"} (${c.count ?? 0})`;
    return li;
  }));
  document.getElementById("categories-empty").hidden = categories.length > 0;

  const body = document.getElementById("violations");
  body.replaceChildren(...violations.map((v) => {
    const tr = document.createElement("tr");
    tr.setAttribute("role", "row");
    tr.appendChild(timelineCell(v));
    // data-label lets the CSS show each cell as "Label: value" on phones.
    const cells = [["Type", v.code], ["Description", v.description], ["Status", v.status]];
    for (const [label, value] of cells) {
      const td = document.createElement("td");
      td.dataset.label = label;
      td.setAttribute("role", "cell");
      td.textContent = value || "-";
      tr.appendChild(td);
    }
    return tr;
  }));
  // Nothing matched at all: swap the empty lists for the "no records is not the same as safe" box.
  const nothingFound = violations.length === 0 && !(r.totalViolations > 0);
  document.getElementById("no-records").hidden = !nothingFound;
  document.getElementById("details").hidden = nothingFound;

  document.getElementById("violations-table").hidden = violations.length === 0;
  document.getElementById("violations-empty").hidden = violations.length > 0;

  renderAlsoCheck(searchedAddress);
  renderPlace(searchedAddress);
  renderProperty(r.property);
  renderCondemned(r.condemned);
  renderSuggestions(r.suggestions);
  renderPermits(r.permits, r.permitsNote);
  renderAround(r.requests311);
  renderSources(r.dataAsOf);
  renderWalk(searchedAddress);
  renderQuestions(r.questions);
  renderNeighborhood(r.neighborhood);
  reportEl.hidden = false;
}

// "Also check" links to other sites. encodeURIComponent makes the address safe to put in a URL
// (spaces, "#", "&" and so on can't break the link or add extra parameters).
function renderAlsoCheck(address) {
  document.getElementById("also-check").hidden = !address;
  if (!address) return;
  const a = encodeURIComponent(address);
  document.getElementById("link-maps").href =
    `https://www.google.com/maps/search/?api=1&query=${a}+Pittsburgh`;
  document.getElementById("link-google").href =
    `https://www.google.com/search?q=${encodeURIComponent(`"${address}"`)}+Pittsburgh+reviews`;
  document.getElementById("link-reddit").href =
    `https://www.reddit.com/search/?q=${a}`;
}

// ---------------------------------------------------------------------------
// Timeline for one violation: "Issued Feb 18, 2026, resolved Mar 19, 2026",
// plus a small line with the case ID and legal code section when we have them.
// ---------------------------------------------------------------------------

// Same list as OPEN_STATUSES in Michael's ReportService.java. Keep the two in sync.
const OPEN_STATUSES = ["in violation", "in court", "clean & lien", "appealed"];

const MONTHS = ["Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"];

// "2026-02-18" -> "Feb 18, 2026". We read the numbers ourselves instead of using new Date(),
// because new Date("2026-02-18") means midnight UTC, which shows as Feb 17 in Pittsburgh.
function niceDate(iso) {
  const m = /^(\d{4})-(\d{2})-(\d{2})/.exec(iso || "");
  if (!m) return null;
  const month = MONTHS[Number(m[2]) - 1];
  return month ? `${month} ${Number(m[3])}, ${m[1]}` : null;
}

function timelineText(v) {
  const issued = niceDate(v.date);
  const resolved = niceDate(v.resolvedDate);
  const isOpen = OPEN_STATUSES.includes(String(v.status || "").trim().toLowerCase());

  const start = issued ? `Issued ${issued}` : "Issue date not on record";
  if (resolved) return { text: `${start}, resolved ${resolved}`, open: false };
  if (isOpen) return { text: `${start}. Still unresolved`, open: true };
  // Closed (or unknown status) but the city gave no resolved date: say so rather than guess.
  return { text: `${start}. No resolved date on record`, open: false };
}

function timelineCell(v) {
  const td = document.createElement("td");
  td.dataset.label = "Timeline";
  td.setAttribute("role", "cell");

  const { text, open } = timelineText(v);
  const main = document.createElement("div");
  main.textContent = text;
  if (open) main.className = "unresolved";
  td.appendChild(main);

  // "Case CF-ES-2026-008155 · CITY CODE 619.06(A)": only the parts that exist.
  const extra = [v.caseId && `Case ${v.caseId}`, v.codeSection].filter(Boolean).join(" · ");
  if (extra) {
    const small = document.createElement("small");
    small.className = "case-meta";
    small.textContent = extra;
    td.appendChild(small);
  }
  return td;
}

// "The building" box: a Google Maps satellite view of the typed address, shown in an iframe
// (a window onto another website). Hidden for the sample report, whose address is fake.
function renderPlace(address) {
  document.getElementById("place").hidden = !address;
  if (!address) return;
  document.getElementById("place-address").textContent = `${address}, Pittsburgh, PA`;
  const src = "https://www.google.com/maps?q=" + encodeURIComponent(`${address}, Pittsburgh, PA`)
    + "&t=k&z=19&output=embed";   // t=k: satellite, z=19: zoomed in on the building
  const frame = document.getElementById("place-map");
  if (frame.src !== src) frame.src = src;   // don't reload the map if it's the same address
}

// "Questions to ask before you sign": a checklist the renter can tick off while talking to the landlord.
// Older servers don't send the field at all, so anything that isn't a non-empty list hides the card.
function renderQuestions(questions) {
  const list = Array.isArray(questions) ? questions.filter((q) => typeof q === "string" && q.trim()) : [];
  document.getElementById("questions").hidden = list.length === 0;
  document.getElementById("questions-list").replaceChildren(...list.map((q, i) => {
    const li = document.createElement("li");
    const box = document.createElement("input");
    box.type = "checkbox";
    box.id = `question-${i}`;
    const label = document.createElement("label");
    label.htmlFor = box.id;          // clicking the text ticks the box, and screen readers read it
    label.textContent = q;
    li.append(box, label);
    return li;
  }));
}

// Print button: the browser's own print window, which also offers "Save as PDF".
// style.css has an @media print section that hides the search box, tabs and map on paper.
document.getElementById("print-report").addEventListener("click", () => {
  const now = new Date().toLocaleDateString("en-US", { month: "short", day: "numeric", year: "numeric" });
  document.getElementById("print-stamp").textContent =
    `Printed from RentCheck PGH on ${now}. Data: City of Pittsburgh via WPRDC. `
    + "A violation is not proof of a bad landlord, and no records does not mean safe.";
  window.print();
});

// "In Central Oakland: rank 12 on the Neighborhoods list". Uses the proposed `neighborhood` field
// (see "Planned additions" in docs/API_CONTRACT.md). Hidden if the server doesn't send it yet.
function renderNeighborhood(hood) {
  const line = document.getElementById("hood-line");
  if (!hood || !hood.name) { line.hidden = true; return; }

  line.replaceChildren();
  line.append("This address is in ");
  line.appendChild(Object.assign(document.createElement("b"), { textContent: hood.name }));
  if (hood.rank !== null && hood.rank !== undefined) {
    const perThousand = hood.rankedBy === "per1000";
    const value = perThousand ? hood.per1000 : hood.openRecent;
    line.append(`: rank ${hood.rank} on the Neighborhoods list`
      + (value !== null && value !== undefined
        ? ` (${value} unresolved building & fire safety cases${perThousand ? " per 1,000 residents" : ""})` : "")
      + ". ");
  } else {
    line.append(". It isn't ranked (no reliable population figure). ");
  }
  const link = document.createElement("a");
  link.href = `neighborhoods.html?q=${encodeURIComponent(hood.name)}`;
  link.textContent = "See the neighborhood ranking";
  line.appendChild(link);
  line.hidden = false;
}

// "About this building": Allegheny County assessment facts. Rules from docs/API_CONTRACT.md:
// show `type` (never `countyClass`), always show `context` when present, handle `property: null`,
// and never suggest we know who the landlord is (the county excludes owner names by law).
function renderProperty(p) {
  const card = document.getElementById("property");
  if (!p || typeof p !== "object") { card.hidden = true; return; }

  // The context warning: e.g. "this is university property, not housing".
  const context = document.getElementById("property-context");
  context.textContent = p.context || "";
  context.hidden = !p.context;

  const has = (v) => v !== null && v !== undefined && v !== "";
  const facts = [
    ["Type", p.type],
    ["Year built", p.yearBuilt],
    ["Stories", p.stories],
    ["Bedrooms", p.bedrooms],
    ["Full bathrooms", p.fullBaths],
    ["Living area", typeof p.livingAreaSqFt === "number" ? `${p.livingAreaSqFt.toLocaleString("en-US")} sq ft` : p.livingAreaSqFt],
    ["Condition", has(p.conditionRating) ? `${p.conditionRating} (county assessor's rating, may be years old)` : null],
    // Owner TYPE only. We say plainly that we don't know who it is.
    ["Owner", has(p.ownerType) ? `Owned by ${p.ownerType}. The county doesn't publish owner names, so we don't know who the landlord is.` : null],
    // true = someone claimed the homestead tax break (owner lives there). null = unknown, NOT "it's a rental",
    // so we only show this line when it's true.
    ["Owner lives here?", p.ownerOccupied === true ? "Probably: someone claims the homestead tax reduction, which only applies to a home the owner lives in." : null],
    ["County parcel ID", p.parcelId],
  ].filter(([, value]) => has(value));

  document.getElementById("property-facts").replaceChildren(...facts.flatMap(([label, value]) => [
    Object.assign(document.createElement("dt"), { textContent: label }),
    Object.assign(document.createElement("dd"), { textContent: String(value) }),
  ]));

  // Several parcels at one address = assessed unit by unit (condos/apartments), so no room counts.
  const units = document.getElementById("property-units");
  units.hidden = !(p.parcelsAtAddress > 1);
  units.textContent = `The county assesses this address as ${p.parcelsAtAddress} separate units, `
    + "so room counts aren't shown (they would describe just one unit).";

  document.getElementById("property-note").textContent = p.note || "From Allegheny County assessment records.";
  card.hidden = false;
}

// ---------------------------------------------------------------------------
// "How far is the walk to campus?": Google Maps walking directions from the searched address
// to the chosen school. Google computes the real route and time; we don't estimate it ourselves.
// Addresses checked Sep 19 2026 (swpenna.com college guide; CCAC's own "for visiting" address).
// ---------------------------------------------------------------------------
const SCHOOLS = [
  ["University of Pittsburgh", "4200 Fifth Ave, Pittsburgh, PA 15260"],
  ["Carnegie Mellon University", "5000 Forbes Ave, Pittsburgh, PA 15213"],
  ["Duquesne University", "600 Forbes Ave, Pittsburgh, PA 15282"],
  ["Carlow University", "3333 Fifth Ave, Pittsburgh, PA 15213"],
  ["Chatham University", "1 Woodland Rd, Pittsburgh, PA 15232"],
  ["Point Park University", "201 Wood St, Pittsburgh, PA 15222"],
  ["CCAC Allegheny Campus", "808 Ridge Ave, Pittsburgh, PA 15212"],
];

const schoolSelect = document.getElementById("school");
schoolSelect.append(...SCHOOLS.map(([name], i) => Object.assign(document.createElement("option"), { value: i, textContent: name })));
try { schoolSelect.value = localStorage.getItem("school") || ""; } catch { /* storage blocked: fine */ }
schoolSelect.addEventListener("change", () => {
  try { localStorage.setItem("school", schoolSelect.value); } catch { /* not remembered: fine */ }
  renderWalk(searchedAddress);
});

function renderWalk(address) {
  document.getElementById("walk").hidden = !address;   // hidden for the sample report (fake address)
  const result = document.getElementById("walk-result");
  const school = SCHOOLS[schoolSelect.value];
  if (!address || !school) { result.hidden = true; return; }

  const [name, schoolAddress] = school;
  // Google's documented "Maps URLs" format: no API key needed, travelmode=walking.
  const url = "https://www.google.com/maps/dir/?api=1"
    + `&origin=${encodeURIComponent(`${address}, Pittsburgh, PA`)}`
    + `&destination=${encodeURIComponent(schoolAddress)}`
    + "&travelmode=walking";
  const link = document.createElement("a");
  link.href = url;
  link.target = "_blank";
  link.rel = "noopener noreferrer";
  link.textContent = `See the walking route and time to ${name}`;
  link.appendChild(Object.assign(document.createElement("span"), { className: "visually-hidden", textContent: " (opens in a new tab)" }));
  result.replaceChildren(link);
  result.hidden = false;
}

// ---------------------------------------------------------------------------
// Newer report fields (see docs/API_CONTRACT.md). Every one of them can be null, so each
// function hides its section when there's nothing to show. Text always goes in with textContent.
// ---------------------------------------------------------------------------

// Small helper: make an element with text.
function make(tag, text, className) {
  const e = document.createElement(tag);
  if (className) e.className = className;
  if (text !== undefined && text !== null) e.textContent = text;
  return e;
}

// "Condemned": the city says the property is unfit for occupancy. Shown above everything else.
// We show the city's warning and note only; the source data has owner names and we never use them.
function renderCondemned(c) {
  const box = document.getElementById("condemned");
  box.hidden = !(c && c.warning);
  if (box.hidden) return;
  document.getElementById("condemned-warning").textContent = c.warning;
  document.getElementById("condemned-note").textContent = c.note || "";
}

// "Did you mean?": real addresses from the city's data that look close to what was typed.
// The backend only sends these when nothing matched. Clicking one runs a new search.
function renderSuggestions(list) {
  const items = Array.isArray(list) ? list.filter((x) => x && x.address) : [];
  document.getElementById("suggestions").hidden = items.length === 0;
  document.getElementById("suggestions-list").replaceChildren(...items.map((x) => {
    const li = make("li");
    const a = make("a", x.address);
    a.href = `?address=${encodeURIComponent(x.address)}`;
    a.addEventListener("click", (e) => {
      e.preventDefault();
      input.value = x.address;
      search(x.address);
    });
    li.appendChild(a);
    if (typeof x.similarityPercent === "number") li.appendChild(make("small", ` ${x.similarityPercent}% similar`, "muted"));
    return li;
  }));
}

// Building permits since June 2019. When there are none, the backend sends permitsNote instead,
// already worded carefully: we show it exactly as written (no permits is NOT proof of neglect).
function renderPermits(p, permitsNote) {
  const box = document.getElementById("permits");
  const items = p && Array.isArray(p.items) ? p.items : [];
  box.hidden = !p && !permitsNote;
  if (box.hidden) return;

  document.getElementById("permits-summary").textContent = p ? (p.summary || "") : permitsNote;
  document.getElementById("permits-table").hidden = items.length === 0;
  document.getElementById("permits-items").replaceChildren(...items.map((it) => {
    const tr = make("tr");
    tr.setAttribute("role", "row");
    const work = [it.type, it.workType].filter(Boolean).join(": ");
    const value = typeof it.projectValue === "number" ? `$${it.projectValue.toLocaleString("en-US")}` : null;
    for (const [label, text] of [["Date", niceDate(it.date) || it.date], ["Work", work], ["Description", it.description],
                                 ["Value", value], ["Status", it.status]]) {
      const td = make("td", text || "-");
      td.setAttribute("role", "cell");
      td.dataset.label = label;
      tr.appendChild(td);
    }
    return tr;
  }));
  document.getElementById("permits-note").textContent = p ? (p.note || "") : "";
}

// 311 requests in the NEIGHBORHOOD. About the street, not the building, so the heading says
// "Around this address" and the backend's note is always shown.
function renderAround(r) {
  const box = document.getElementById("around");
  box.hidden = !r;
  if (!r) return;
  document.getElementById("around-summary").textContent = r.summary || "";
  const types = Array.isArray(r.topTypes) ? r.topTypes : [];
  document.getElementById("around-types").replaceChildren(...types.map((t) => make("li", `${t.type || "Other"} (${t.count ?? 0})`)));
  const items = Array.isArray(r.items) ? r.items : [];
  document.getElementById("around-details").hidden = items.length === 0;
  document.getElementById("around-items").replaceChildren(...items.map((it) =>
    make("li", [niceDate(it.date) || it.date, it.type, it.status].filter(Boolean).join(" · "))));
  document.getElementById("around-note").textContent =
    [r.note, r.asOf && `Newest request we saw: ${niceDate(r.asOf) || r.asOf}.`].filter(Boolean).join(" ");
}

// "Where this data comes from": each dataset, what it covers, and how fresh it is.
function renderSources(list) {
  const items = Array.isArray(list) ? list.filter((x) => x && x.source) : [];
  document.getElementById("sources").hidden = items.length === 0;
  document.getElementById("sources-list").replaceChildren(...items.map((x) => {
    const li = make("li");
    li.appendChild(make("b", x.source));
    const parts = [x.covers && `covers ${x.covers}`, `newest record: ${x.asOf ? (niceDate(x.asOf) || x.asOf) : "unknown"}`];
    li.append(` · ${parts.filter(Boolean).join(", ")}`);
    if (x.note) li.appendChild(make("small", x.note, "source-note"));
    return li;
  }));
}
