// Neighborhoods tab: shows /api/neighborhoods as a ranked table (see docs/API_CONTRACT.md).
// We never label neighborhoods "worst" or "safest": we show the number and what it measures.

const statusEl = document.getElementById("status");

load();

async function load() {
  try {
    const data = await fetchJsonWithRetry("/api/neighborhoods", (message) => {
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
  // The server already sorted them by rank. Unranked ones (rank: null) go to their own list.
  const ranked = all.filter((n) => n.rank !== null && n.rank !== undefined);
  const unranked = all.filter((n) => n.rank === null || n.rank === undefined);

  // Title and column show the unit the server actually used.
  document.getElementById("ranking-title").textContent = perThousand
    ? "Unresolved building & fire safety cases per 1,000 residents"
    : "Unresolved building & fire safety cases (raw count)";
  document.getElementById("unit-header").textContent = perThousand ? "Per 1,000 residents" : "Cases";

  const basis = [data.basis, data.asOf && `Data as of ${data.asOf}.`].filter(Boolean).join(". ");
  document.getElementById("basis").textContent = basis.replace(/\.\./g, ".");

  // Say which end of the list rank 1 is, worked out from the numbers rather than assumed.
  const value = (n) => (perThousand ? n.per1000 : n.openRecent);
  if (ranked.length >= 2) {
    const fewestFirst = value(ranked[0]) <= value(ranked[ranked.length - 1]);
    document.getElementById("direction").textContent =
      `Rank 1 has the ${fewestFirst ? "fewest" : "most"} cases${perThousand ? " per 1,000 residents" : ""}. `
      + "Lower numbers mean fewer open safety cases on record, not that a place is safe.";
  }

  document.getElementById("ranked").replaceChildren(...ranked.map((n) => {
    const tr = el("tr");
    tr.setAttribute("role", "row");
    tr.dataset.name = String(n.name || "").toLowerCase();
    const cells = [
      ["Rank", n.rank],
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
