// Neighborhoods tab: shows /api/neighborhoods as a ranked table (see docs/API_CONTRACT.md).
// We never label neighborhoods "worst" or "safest": we show the number and what it measures.

const statusEl = document.getElementById("status");

load("/api/neighborhoods");
setupCampusFilter();

async function load(url) {
  statusEl.textContent = "Loading neighborhoods...";
  statusEl.className = "loading";
  try {
    const data = await fetchJsonWithRetry(url, (message) => {
      statusEl.textContent = `${message} Trying again in a few seconds...`;
    });
    render(data);
    statusEl.textContent = "";
    statusEl.className = "";
  } catch (err) {
    statusEl.textContent = `Something went wrong: ${err.message}`;
    statusEl.className = "error";
  }
}

function render(data) {
  const perThousand = data.rankedBy === "per1000";
  const all = Array.isArray(data.neighborhoods) ? data.neighborhoods : [];
  const filtered = Boolean(data.filter);   // a school was chosen: sorted closest first, with distances
  // Citywide: the server sorted by rank, and unranked ones (rank: null) go to their own list.
  // Near a campus: one list in distance order; unranked ones stay in it, marked "not ranked".
  const hasRank = (n) => n.rank !== null && n.rank !== undefined;
  const ranked = filtered ? all : all.filter(hasRank);
  const unranked = filtered ? [] : all.filter((n) => !hasRank(n));
  renderFilterInfo(data);

  // Title and column show the unit the server actually used.
  document.getElementById("ranking-title").textContent = perThousand
    ? "Unresolved building & fire safety cases per 1,000 residents"
    : "Unresolved building & fire safety cases (raw count)";
  document.getElementById("unit-header").textContent = perThousand ? "Per 1,000 residents" : "Cases";

  const basis = [data.basis, data.asOf && `Data as of ${data.asOf}.`].filter(Boolean).join(". ");
  document.getElementById("basis").textContent = basis.replace(/\.\./g, ".");

  // Say which end of the list rank 1 is, worked out from the numbers rather than assumed.
  const value = (n) => (perThousand ? n.per1000 : n.openRecent);
  const byRank = all.filter(hasRank).sort((a, b) => a.rank - b.rank);
  document.getElementById("direction").textContent = "";
  if (byRank.length >= 2) {
    const fewestFirst = value(byRank[0]) <= value(byRank[byRank.length - 1]);
    document.getElementById("direction").textContent =
      `Rank 1 has the ${fewestFirst ? "fewest" : "most"} cases${perThousand ? " per 1,000 residents" : ""}. `
      + "Lower numbers mean fewer open safety cases on record, not that a place is safe.";
  }

  document.getElementById("ranked").replaceChildren(...ranked.map((n) => {
    const tr = el("tr");
    tr.setAttribute("role", "row");
    tr.dataset.name = String(n.name || "").toLowerCase();
    const cells = [
      ...(filtered ? [["Distance", typeof n.distanceMiles === "number" ? `${n.distanceMiles.toFixed(1)} mi` : "-"]] : []),
      ["Rank", hasRank(n) ? n.rank : "Not ranked"],
      ["Neighborhood", n.name || "Unnamed"],
      [perThousand ? "Per 1,000 residents" : "Cases", fmt(value(n))],
      ["Unresolved, last 3 years", fmt(n.openRecent)],
      ["Residents (2020)", fmt(n.population)],
    ];
    for (const [label, text] of cells) {
      const td = el("td", null, text);
      td.setAttribute("role", "cell");
      td.dataset.label = label;
      tr.appendChild(td);
    }
    return tr;
  }));

  document.getElementById("unranked-box").hidden = unranked.length === 0;
  document.getElementById("unranked").replaceChildren(...unranked.map((n) => {
    const li = el("li");
    li.dataset.name = String(n.name || "").toLowerCase();
    li.appendChild(el("b", null, n.name || "Unnamed"));
    li.appendChild(el("span", null, ` · ${fmt(n.openRecent)} unresolved (last 3 years)`));
    li.appendChild(el("small", "rate-note", n.rateNote || "No reliable population figure."));
    return li;
  }));

  document.getElementById("api-note").textContent = data.note || "";
  if (data.truncated) {
    document.getElementById("api-note").textContent +=
      " Not every city record could be loaded, so some counts may be too low.";
  }
  document.getElementById("ranking").hidden = false;

  // Opened from a report link like neighborhoods.html?q=Central%20Oakland: pre-fill the filter.
  // Also re-applies whatever is in the name box after the campus filter reloads the list.
  const filter = document.getElementById("filter");
  const q = new URLSearchParams(location.search).get("q");
  if (q && !filter.dataset.prefilled) { filter.value = q; filter.dataset.prefilled = "1"; }
  if (filter.value) filter.dispatchEvent(new Event("input"));
}

// Numbers with commas; "-" when missing.
function fmt(x) {
  return typeof x === "number" ? x.toLocaleString("en-US") : "-";
}

// Filter box: hide rows whose name doesn't contain what was typed.
document.getElementById("filter").addEventListener("input", (e) => {
  const q = e.target.value.trim().toLowerCase();
  let shown = 0;
  document.querySelectorAll("#ranked tr, #unranked li").forEach((row) => {
    const match = row.dataset.name.includes(q);
    row.hidden = !match;
    if (match) shown++;
  });
  document.getElementById("filter-empty").hidden = shown > 0;
});

// ---------------------------------------------------------------------------
// "Near my campus" filter (GET /api/schools, then /api/neighborhoods?school=&mode= or &maxMiles=).
// Distances are straight-line from the middle of campus; the server's notes say so and we show them.
// ---------------------------------------------------------------------------
let campusData = null;

async function setupCampusFilter() {
  try {
    campusData = await fetchJsonWithRetry("/api/schools", () => {});
  } catch {
    return;   // no schools endpoint (older server): just leave the filter hidden
  }
  const schools = Array.isArray(campusData.schools) ? campusData.schools : [];
  const modes = Array.isArray(campusData.travelModes) ? campusData.travelModes : [];
  if (schools.length === 0) return;

  const schoolSel = document.getElementById("campus-school");
  const modeSel = document.getElementById("campus-mode");
  const miles = document.getElementById("campus-miles");
  schoolSel.append(...schools.map((sc) => Object.assign(document.createElement("option"), { value: sc.id, textContent: sc.name })));
  modeSel.append(...modes.map((m) => Object.assign(document.createElement("option"), { value: m.id, textContent: m.label })));
  document.getElementById("campus-note").textContent = campusData.note || "";

  const modeById = (id) => modes.find((m) => m.id === id);
  function syncMode() {
    const m = modeById(modeSel.value);
    document.getElementById("campus-mode-note").textContent = (m && m.note) || "";
    if (m && typeof m.defaultMiles === "number") miles.value = m.defaultMiles;   // mode sets a starting radius
  }
  function apply() {
    const school = schoolSel.value;
    modeSel.disabled = miles.disabled = !school;
    showSchoolLinks(schools.find((sc) => sc.id === school));
    if (!school) { load("/api/neighborhoods"); return; }
    const m = modeById(modeSel.value);
    const custom = miles.value !== "" && (!m || Number(miles.value) !== m.defaultMiles);
    load(`/api/neighborhoods?school=${encodeURIComponent(school)}`
      + (custom ? `&maxMiles=${encodeURIComponent(miles.value)}` : `&mode=${encodeURIComponent(modeSel.value)}`));
  }
  schoolSel.addEventListener("change", apply);
  modeSel.addEventListener("change", () => { syncMode(); apply(); });
  miles.addEventListener("change", apply);
  syncMode();
  modeSel.disabled = miles.disabled = true;
  document.getElementById("campus").hidden = false;
}

// The chosen school's OWN housing office links (from /api/schools), opened in a new tab.
function showSchoolLinks(school) {
  const box = document.getElementById("campus-links");
  const links = school && Array.isArray(school.housingLinks) ? school.housingLinks.filter((l) => /^https?:\/\//i.test(l.url || "")) : [];
  box.hidden = links.length === 0;
  box.replaceChildren();
  if (box.hidden) return;
  box.append(`${school.shortName || school.name} housing office: `);
  links.forEach((l, i) => {
    if (i) box.append(" · ");
    box.appendChild(externalLink(l.url, l.label || l.url));
  });
}

// "Showing 4 of 87 neighborhoods within 1 mile of Pitt (Walking)" plus the server's distance note.
function renderFilterInfo(data) {
  const f = data.filter;
  const summary = document.getElementById("filter-summary");
  const note = document.getElementById("distance-note");
  document.getElementById("distance-header").hidden = !f;
  summary.hidden = note.hidden = !f;
  if (!f) return;
  let text = `Showing ${f.matched ?? "?"} of ${f.ofTotal ?? "?"} neighborhoods`
    + (typeof f.maxMiles === "number" ? ` within ${f.maxMiles} mile${f.maxMiles === 1 ? "" : "s"}` : "")
    + ` of ${f.school || "campus"}` + (f.modeLabel ? ` (${f.modeLabel})` : "") + ", closest first.";
  text += " Ranks are still citywide, so gaps in the numbers are expected.";
  summary.textContent = text;
  note.textContent = [data.distanceNote, "Neighborhoods we can't place on a map are left out, because we can't say how far away they are."]
    .filter(Boolean).join(" ");
}
