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
  const address = input.value.trim();
  if (!address) return;
  // Put the address in the page URL so a report can be refreshed or shared (handy for the demo).
  history.replaceState(null, "", `?address=${encodeURIComponent(address)}`);
  searchedAddress = address;
  load(`/api/report?address=${encodeURIComponent(address)}`);
});

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
  setStatus("Searching city records... (this can take a few seconds)", "loading");
  reportEl.hidden = true;
  button.disabled = true;
  button.textContent = "Checking...";
  try {
    let res;
    try {
      res = await fetch(url);
    } catch {
      throw new Error("Can't reach the RentCheck server. Is it running?");
    }
    // A crash can return an HTML error page instead of JSON, so don't assume JSON.
    const data = await res.json().catch(() => null);
    if (!res.ok || !data) {
      const detail = data && data.error;
      if (res.status === 502) {
        throw new Error("The city's data service didn't respond. Try again in a minute.");
      }
      throw new Error(detail || `Request failed (${res.status}).`);
    }
    render(data);
    setStatus("");
  } catch (err) {
    setStatus(`Something went wrong: ${err.message} You can search again, or try the sample report.`, "error");
  } finally {
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
    tr.appendChild(timelineCell(v));
    // data-label lets the CSS show each cell as "Label: value" on phones.
    const cells = [["Type", v.code], ["Description", v.description], ["Status", v.status]];
    for (const [label, value] of cells) {
      const td = document.createElement("td");
      td.dataset.label = label;
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
