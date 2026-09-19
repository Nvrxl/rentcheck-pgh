// Frontend logic. Talks to the Java backend at /api/report.
// The shape of the data it gets back is defined in docs/API_CONTRACT.md.

const form = document.getElementById("search-form");
const input = document.getElementById("address");
const statusEl = document.getElementById("status");
const reportEl = document.getElementById("report");

form.addEventListener("submit", (e) => {
  e.preventDefault();
  load(`/api/report?address=${encodeURIComponent(input.value.trim())}`);
});

document.getElementById("sample-link").addEventListener("click", (e) => {
  e.preventDefault();
  load("/api/report/sample");
});

async function load(url) {
  setStatus("Searching city records...");
  reportEl.hidden = true;
  form.querySelector("button").disabled = true;
  try {
    const res = await fetch(url);
    const data = await res.json();
    if (!res.ok) throw new Error(data.error || `Request failed (${res.status})`);
    render(data);
    setStatus("");
  } catch (err) {
    setStatus(`Something went wrong: ${err.message}`, true);
  } finally {
    form.querySelector("button").disabled = false;
  }
}

function setStatus(msg, isError = false) {
  statusEl.textContent = msg;
  statusEl.className = isError ? "error" : "";
}

function render(r) {
  // textContent (not innerHTML) on purpose: never trust text that came from outside.
  const badge = document.getElementById("risk-badge");
  badge.textContent = r.riskLevel;
  badge.className = `badge ${r.riskLevel}`;

  document.getElementById("summary").textContent = r.summary;
  document.getElementById("stat-total").textContent = r.totalViolations;
  document.getElementById("stat-open").textContent = r.openViolations;
  document.getElementById("stat-recent").textContent = r.mostRecent || "-";
  document.getElementById("note").textContent = r.note || "";

  const cats = document.getElementById("categories");
  cats.replaceChildren(...r.categories.map((c) => {
    const li = document.createElement("li");
    li.textContent = `${c.name} (${c.count})`;
    return li;
  }));

  const body = document.getElementById("violations");
  body.replaceChildren(...r.violations.map((v) => {
    const tr = document.createElement("tr");
    for (const value of [v.date, v.code, v.description, v.status]) {
      const td = document.createElement("td");
      td.textContent = value || "-";
      tr.appendChild(td);
    }
    return tr;
  }));

  reportEl.hidden = false;
}
