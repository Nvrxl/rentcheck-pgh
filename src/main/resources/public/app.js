// Frontend logic. Talks to the Java backend at /api/report.
// The shape of the data it gets back is defined in docs/API_CONTRACT.md.
// The contract says almost any field may be null or empty, so every read below has a fallback.

const form = document.getElementById("search-form");
const input = document.getElementById("address");
const button = form.querySelector("button");
const statusEl = document.getElementById("status");
const reportEl = document.getElementById("report");

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
  load(`/api/report?address=${encodeURIComponent(address)}`);
});

document.getElementById("sample-link").addEventListener("click", (e) => {
  e.preventDefault();
  history.replaceState(null, "", "?");
  load("/api/report/sample");
});

// If the page was opened with ?address=..., run that search right away.
const startAddress = new URLSearchParams(location.search).get("address");
if (startAddress) {
  input.value = startAddress;
  load(`/api/report?address=${encodeURIComponent(startAddress)}`);
}

async function load(url) {
  setStatus("Searching city records... (this can take a few seconds)", "loading");
  reportEl.hidden = true;
  button.disabled = true;
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
    setStatus(`Something went wrong: ${err.message}`, "error");
  } finally {
    button.disabled = false;
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

  document.getElementById("summary").textContent = r.summary || "";
  document.getElementById("stat-total").textContent = r.totalViolations ?? 0;
  document.getElementById("stat-open").textContent = r.openViolations ?? 0;
  document.getElementById("stat-recent").textContent = r.mostRecent || "-";
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
    // data-label lets the CSS show each cell as "Label: value" on phones.
    const cells = [["Date", v.date], ["Type", v.code], ["Description", v.description], ["Status", v.status]];
    for (const [label, value] of cells) {
      const td = document.createElement("td");
      td.dataset.label = label;
      td.textContent = value || "-";
      tr.appendChild(td);
    }
    return tr;
  }));
  document.getElementById("violations-table").hidden = violations.length === 0;
  document.getElementById("violations-empty").hidden = violations.length > 0;

  reportEl.hidden = false;
}
