// Rentals near you: browser location -> /api/neighborhoods/near -> nearby neighborhoods + rental search links.

const statusEl = document.getElementById("status");
const locateButton = document.getElementById("locate");

function setStatus(msg, kind = "") {
  statusEl.textContent = msg;
  statusEl.className = kind;
}

// We only ask for location when the user clicks: asking on page load feels pushy and browsers discourage it.
locateButton.addEventListener("click", () => {
  if (!("geolocation" in navigator)) {
    setStatus("Your browser can't share its location. Try the Neighborhoods tab to browse every neighborhood.", "error");
    return;
  }
  locateButton.disabled = true;
  setStatus("Asking your browser for your location...", "loading");
  navigator.geolocation.getCurrentPosition(onLocation, onLocationError,
    { enableHighAccuracy: false, timeout: 15000, maximumAge: 5 * 60 * 1000 });
});

function onLocationError(err) {
  locateButton.disabled = false;
  const messages = {
    1: "Location access was denied. To use this tab, allow location for this site in your browser settings "
       + "and try again, or browse every neighborhood on the Neighborhoods tab.",
    2: "Your browser couldn't work out where you are. Try again, or use the Neighborhoods tab.",
    3: "Finding your location took too long. Try again, or use the Neighborhoods tab.",
  };
  setStatus(messages[err.code] || "Couldn't get your location. Try the Neighborhoods tab.", "error");
  statusEl.focus();
}

async function onLocation(pos) {
  // Rounded to 3 decimals (about 100 m): plenty to find a neighborhood, and less precise than your exact spot.
  const lat = pos.coords.latitude.toFixed(3);
  const lon = pos.coords.longitude.toFixed(3);
  setStatus("Finding neighborhoods near you...", "loading");
  try {
    const data = await fetchJsonWithRetry(`/api/neighborhoods/near?lat=${lat}&lon=${lon}`, (message) => {
      setStatus(`${message} Trying again in a few seconds...`, "loading");
    });
    render(data);
    setStatus("");
    document.getElementById("results-heading").focus();
  } catch (err) {
    setStatus(`Something went wrong: ${err.message}`, "error");
    statusEl.focus();
  } finally {
    locateButton.disabled = false;
  }
}

function render(data) {
  const nearby = Array.isArray(data.nearby) ? data.nearby : [];
  const perThousand = data.rankedBy === "per1000";
  document.getElementById("outside").hidden = data.insideCity !== false;

  document.getElementById("nearby").replaceChildren(...nearby.map((n, i) => {
    const card = el("article", "card nearby-card");
    const title = el("h3", null, n.name || "Unnamed neighborhood");
    if (i === 0 && data.insideCity !== false) title.appendChild(el("span", "here-tag", "You're here"));
    card.appendChild(title);

    const miles = typeof n.distanceKm === "number" ? (n.distanceKm * 0.621371).toFixed(1) : null;
    if (miles !== null && !(i === 0 && data.insideCity !== false)) {
      card.appendChild(el("p", "note", `About ${miles} mi away`));
    }

    // Rank line: the number and what it measures. No "safe"/"dangerous" words.
    const value = perThousand ? n.per1000 : n.openRecent;
    const rankText = (n.rank !== null && n.rank !== undefined)
      ? `Rank ${n.rank} on the Neighborhoods list: ${value ?? "-"} unresolved building & fire safety cases`
        + (perThousand ? " per 1,000 residents." : ".")
      : "Not ranked (no reliable population figure). See the Neighborhoods tab for why.";
    card.appendChild(el("p", null, rankText));
    if (typeof n.openRecent === "number") {
      card.appendChild(el("p", "note", `${n.openRecent} unresolved case${n.openRecent === 1 ? "" : "s"} issued in the last 3 years.`));
    }

    const links = Array.isArray(n.rentalLinks) ? n.rentalLinks : [];
    const safeLinks = links.filter((l) => /^https?:\/\//i.test(l.url || ""));   // only real web links
    if (safeLinks.length) {
      card.appendChild(el("h4", null, "Search for places to rent"));
      const ul = el("ul", "rental-links");
      for (const l of safeLinks) {
        const li = el("li");
        li.appendChild(externalLink(l.url, l.label || l.url));
        if (l.note) li.appendChild(el("small", "link-note", l.note));
        ul.appendChild(li);
      }
      card.appendChild(ul);
    }
    return card;
  }));

  if (nearby.length === 0) {
    document.getElementById("nearby").appendChild(el("p", "empty", "No neighborhoods found near this location."));
  }
  document.getElementById("api-note").textContent = data.note || "";
  document.getElementById("results").hidden = false;
}
